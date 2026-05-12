package com.connectsphere.payment;

import com.connectsphere.payment.dto.CreateOrderRequest;
import com.connectsphere.payment.dto.CreateOrderResponse;
import com.connectsphere.payment.dto.VerifyPaymentRequest;
import com.connectsphere.payment.entity.Payment;
import com.connectsphere.payment.exception.BadRequestException;
import com.connectsphere.payment.exception.ResourceNotFoundException;
import com.connectsphere.payment.repository.PaymentRepository;
import com.connectsphere.payment.service.PaymentService;
import com.razorpay.Order;
import com.razorpay.OrderClient;
import com.razorpay.RazorpayClient;
import com.razorpay.RazorpayException;
import org.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    @Mock PaymentRepository paymentRepository;
    @Mock RestTemplate restTemplate;
    @Mock RazorpayClient razorpayClient;
    @Mock OrderClient orderClient;
    PaymentService paymentService;

    @BeforeEach
    void setUp() {
        paymentService = new PaymentService(paymentRepository, restTemplate, razorpayClient);
        razorpayClient.Orders = orderClient;
        ReflectionTestUtils.setField(paymentService, "razorpayKeyId", "rzp_test_key");
        ReflectionTestUtils.setField(paymentService, "razorpayKeySecret", "secret");
        ReflectionTestUtils.setField(paymentService, "authServiceUrl", "http://auth-service");
        ReflectionTestUtils.setField(paymentService, "postServiceUrl", "http://post-service");
    }

    @Test
    void createOrder_requiresPaymentType() {
        CreateOrderRequest request = new CreateOrderRequest();
        request.setUserEmail("user@test.com");

        assertThrows(BadRequestException.class, () -> paymentService.createOrder(null, request));
    }

    @Test
    void createOrder_requiresRequestBody() {
        assertThrows(BadRequestException.class, () -> paymentService.createOrder("user@test.com", null));
    }

    @Test
    void createOrder_requiresAuthenticatedEmail() {
        CreateOrderRequest request = new CreateOrderRequest();
        request.setType(Payment.PaymentType.VERIFIED_BADGE);

        assertThrows(BadRequestException.class, () -> paymentService.createOrder(null, request));
    }

    @Test
    void createOrder_blocksDuplicateVerifiedBadge() {
        CreateOrderRequest request = new CreateOrderRequest();
        request.setType(Payment.PaymentType.VERIFIED_BADGE);
        when(paymentRepository.existsByUserEmailAndTypeAndStatus(
            "user@test.com", Payment.PaymentType.VERIFIED_BADGE, Payment.PaymentStatus.SUCCESS))
            .thenReturn(true);

        BadRequestException ex = assertThrows(BadRequestException.class,
            () -> paymentService.createOrder("user@test.com", request));

        assertTrue(ex.getMessage().contains("Verified Badge"));
    }

    @Test
    void createOrder_requiresPostIdForBoostPost() {
        CreateOrderRequest request = new CreateOrderRequest();
        request.setType(Payment.PaymentType.BOOST_POST);

        assertThrows(BadRequestException.class, () -> paymentService.createOrder("user@test.com", request));
    }

    @Test
    void verifyPayment_successMarksPaymentAndGrantsVerifiedBadge() throws Exception {
        Payment payment = payment("order_1", Payment.PaymentType.VERIFIED_BADGE, "user@test.com", null);
        VerifyPaymentRequest request = verifyRequest("order_1", "pay_1", signature("order_1", "pay_1", "secret"));
        when(paymentRepository.findByRazorpayOrderId("order_1")).thenReturn(Optional.of(payment));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Map<String, String> result = paymentService.verifyPayment(request);

        assertEquals("SUCCESS", result.get("status"));
        assertEquals(Payment.PaymentStatus.SUCCESS, payment.getStatus());
        assertEquals("pay_1", payment.getRazorpayPaymentId());
        verify(restTemplate).put("http://auth-service/auth/user/verify-by-email?email=user@test.com", null);
    }

    @Test
    void verifyPayment_successBoostsPostWhenTypeIsBoostPost() throws Exception {
        Payment payment = payment("order_2", Payment.PaymentType.BOOST_POST, "user@test.com", 42L);
        VerifyPaymentRequest request = verifyRequest("order_2", "pay_2", signature("order_2", "pay_2", "secret"));
        when(paymentRepository.findByRazorpayOrderId("order_2")).thenReturn(Optional.of(payment));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Map<String, String> result = paymentService.verifyPayment(request);

        assertEquals("SUCCESS", result.get("status"));
        verify(restTemplate).put("http://post-service/posts/42/boost", null);
    }

    @Test
    void verifyPayment_invalidSignatureMarksFailed() {
        Payment payment = payment("order_3", Payment.PaymentType.VERIFIED_BADGE, "user@test.com", null);
        VerifyPaymentRequest request = verifyRequest("order_3", "pay_3", "wrong");
        when(paymentRepository.findByRazorpayOrderId("order_3")).thenReturn(Optional.of(payment));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        assertThrows(BadRequestException.class, () -> paymentService.verifyPayment(request));

        assertEquals(Payment.PaymentStatus.FAILED, payment.getStatus());
    }

    @Test
    void verifyPayment_unknownOrderThrowsNotFound() {
        VerifyPaymentRequest request = verifyRequest("missing", "pay", "sig");
        when(paymentRepository.findByRazorpayOrderId("missing")).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> paymentService.verifyPayment(request));
    }

    @Test
    void getPaymentHistory_requiresEmail() {
        assertThrows(BadRequestException.class, () -> paymentService.getPaymentHistory(" "));
    }

    @Test
    void getPaymentHistory_returnsUserPayments() {
        when(paymentRepository.findByUserEmailOrderByCreatedAtDesc("user@test.com"))
            .thenReturn(List.of(new Payment()));

        assertEquals(1, paymentService.getPaymentHistory("user@test.com").size());
    }

    @Test
    void getAllPayments_delegatesToRepository() {
        when(paymentRepository.findAll()).thenReturn(List.of(new Payment(), new Payment()));

        assertEquals(2, paymentService.getAllPayments().size());
    }

    @Test
    void getSuccessfulPayments_delegatesToRepository() {
        when(paymentRepository.findByStatusOrderByCreatedAtDesc(Payment.PaymentStatus.SUCCESS))
            .thenReturn(List.of(new Payment()));

        assertEquals(1, paymentService.getSuccessfulPayments().size());
    }


    @Test
    void createOrder_verifiedBadge_successUsesRequestEmailFallback() throws Exception {
        CreateOrderRequest request = new CreateOrderRequest();
        request.setUserEmail("request@test.com");
        request.setType(Payment.PaymentType.VERIFIED_BADGE);
        when(paymentRepository.existsByUserEmailAndTypeAndStatus(
            "request@test.com", Payment.PaymentType.VERIFIED_BADGE, Payment.PaymentStatus.SUCCESS))
            .thenReturn(false);
        when(orderClient.create(any(JSONObject.class))).thenReturn(new Order(new JSONObject().put("id", "order_verified")));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CreateOrderResponse response = paymentService.createOrder(null, request);

        assertEquals("order_verified", response.getRazorpayOrderId());
        assertEquals(9900, response.getAmount());
        assertEquals("INR", response.getCurrency());
        assertEquals("rzp_test_key", response.getKeyId());
        ArgumentCaptor<Payment> payment = ArgumentCaptor.forClass(Payment.class);
        verify(paymentRepository).save(payment.capture());
        assertEquals("request@test.com", payment.getValue().getUserEmail());
        assertEquals(Payment.PaymentType.VERIFIED_BADGE, payment.getValue().getType());
        assertEquals(Payment.PaymentStatus.PENDING, payment.getValue().getStatus());
    }

    @Test
    void createOrder_boostPost_successStoresPostAndBoostAmount() throws Exception {
        CreateOrderRequest request = new CreateOrderRequest();
        request.setType(Payment.PaymentType.BOOST_POST);
        request.setPostId(88L);
        when(orderClient.create(any(JSONObject.class))).thenReturn(new Order(new JSONObject().put("id", "order_boost")));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CreateOrderResponse response = paymentService.createOrder("boost@test.com", request);

        assertEquals("order_boost", response.getRazorpayOrderId());
        assertEquals(4900, response.getAmount());
        ArgumentCaptor<Payment> payment = ArgumentCaptor.forClass(Payment.class);
        verify(paymentRepository).save(payment.capture());
        assertEquals(88L, payment.getValue().getPostId());
        assertEquals(4900, payment.getValue().getAmount());
    }

    @Test
    void createOrder_gatewayNotConfiguredThrows() {
        CreateOrderRequest request = new CreateOrderRequest();
        request.setType(Payment.PaymentType.VERIFIED_BADGE);

        ReflectionTestUtils.setField(paymentService, "razorpayKeyId", " ");
        assertThrows(BadRequestException.class, () -> paymentService.createOrder("user@test.com", request));

        PaymentService noClientService = new PaymentService(paymentRepository, restTemplate, null);
        ReflectionTestUtils.setField(noClientService, "razorpayKeyId", "rzp_test_key");
        ReflectionTestUtils.setField(noClientService, "razorpayKeySecret", "secret");
        assertThrows(BadRequestException.class, () -> noClientService.createOrder("user@test.com", request));
    }

    @Test
    void createOrder_razorpayFailureThrowsFriendlyBadRequest() throws Exception {
        CreateOrderRequest request = new CreateOrderRequest();
        request.setType(Payment.PaymentType.VERIFIED_BADGE);
        when(paymentRepository.existsByUserEmailAndTypeAndStatus(
            "user@test.com", Payment.PaymentType.VERIFIED_BADGE, Payment.PaymentStatus.SUCCESS))
            .thenReturn(false);
        when(orderClient.create(any(JSONObject.class))).thenThrow(new RazorpayException("bad keys"));

        BadRequestException ex = assertThrows(BadRequestException.class,
            () -> paymentService.createOrder("user@test.com", request));

        assertTrue(ex.getMessage().contains("Unable to create Razorpay order"));
        verify(paymentRepository, never()).save(any());
    }

    @Test
    void verifyPayment_requiresAllGatewayFields() {
        VerifyPaymentRequest request = new VerifyPaymentRequest();
        assertThrows(BadRequestException.class, () -> paymentService.verifyPayment(request));

        request.setRazorpayOrderId("order");
        assertThrows(BadRequestException.class, () -> paymentService.verifyPayment(request));

        request.setRazorpayPaymentId("pay");
        assertThrows(BadRequestException.class, () -> paymentService.verifyPayment(request));
    }

    @Test
    void verifyPayment_signatureExceptionMarksFailed() {
        Payment payment = payment("order_secret", Payment.PaymentType.VERIFIED_BADGE, "user@test.com", null);
        VerifyPaymentRequest request = verifyRequest("order_secret", "pay_secret", "sig");
        when(paymentRepository.findByRazorpayOrderId("order_secret")).thenReturn(Optional.of(payment));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> invocation.getArgument(0));
        ReflectionTestUtils.setField(paymentService, "razorpayKeySecret", null);

        assertThrows(BadRequestException.class, () -> paymentService.verifyPayment(request));

        assertEquals(Payment.PaymentStatus.FAILED, payment.getStatus());
    }

    @Test
    void verifyPayment_sideEffectFailuresStillReturnSuccess() throws Exception {
        Payment verified = payment("order_badge_side_effect", Payment.PaymentType.VERIFIED_BADGE, "user@test.com", null);
        VerifyPaymentRequest verifiedRequest = verifyRequest("order_badge_side_effect", "pay_1", signature("order_badge_side_effect", "pay_1", "secret"));
        when(paymentRepository.findByRazorpayOrderId("order_badge_side_effect")).thenReturn(Optional.of(verified));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> invocation.getArgument(0));
        doThrow(new RuntimeException("auth down")).when(restTemplate).put("http://auth-service/auth/user/verify-by-email?email=user@test.com", null);

        Map<String, String> badgeResult = paymentService.verifyPayment(verifiedRequest);
        assertEquals("SUCCESS", badgeResult.get("status"));

        reset(restTemplate, paymentRepository);
        Payment boosted = payment("order_boost_side_effect", Payment.PaymentType.BOOST_POST, "user@test.com", 77L);
        VerifyPaymentRequest boostRequest = verifyRequest("order_boost_side_effect", "pay_2", signature("order_boost_side_effect", "pay_2", "secret"));
        when(paymentRepository.findByRazorpayOrderId("order_boost_side_effect")).thenReturn(Optional.of(boosted));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> invocation.getArgument(0));
        doThrow(new RuntimeException("post down")).when(restTemplate).put("http://post-service/posts/77/boost", null);

        Map<String, String> boostResult = paymentService.verifyPayment(boostRequest);
        assertEquals("SUCCESS", boostResult.get("status"));
    }

    private Payment payment(String orderId, Payment.PaymentType type, String email, Long postId) {
        Payment payment = new Payment();
        payment.setRazorpayOrderId(orderId);
        payment.setType(type);
        payment.setUserEmail(email);
        payment.setPostId(postId);
        payment.setStatus(Payment.PaymentStatus.PENDING);
        payment.setAmount(9900);
        return payment;
    }

    private VerifyPaymentRequest verifyRequest(String orderId, String paymentId, String signature) {
        VerifyPaymentRequest request = new VerifyPaymentRequest();
        request.setRazorpayOrderId(orderId);
        request.setRazorpayPaymentId(paymentId);
        request.setRazorpaySignature(signature);
        return request;
    }

    private String signature(String orderId, String paymentId, String secret) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return HexFormat.of().formatHex(mac.doFinal((orderId + "|" + paymentId).getBytes(StandardCharsets.UTF_8)));
    }
}
