package com.connectsphere.payment.service;

import com.connectsphere.payment.dto.CreateOrderRequest;
import com.connectsphere.payment.dto.CreateOrderResponse;
import com.connectsphere.payment.dto.VerifyPaymentRequest;
import com.connectsphere.payment.entity.Payment;
import com.connectsphere.payment.exception.BadRequestException;
import com.connectsphere.payment.exception.ResourceNotFoundException;
import com.connectsphere.payment.repository.PaymentRepository;
import com.razorpay.Order;
import com.razorpay.RazorpayClient;
import com.razorpay.RazorpayException;
import lombok.RequiredArgsConstructor;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);

    private final PaymentRepository paymentRepository;
    private final RestTemplate restTemplate;
    private final RazorpayClient razorpayClient;

    @Value("${razorpay.key.id}")
    private String razorpayKeyId;

    @Value("${razorpay.key.secret}")
    private String razorpayKeySecret;

    @Value("${auth.service.url:http://auth-service}")
    private String authServiceUrl;

    @Value("${post.service.url:http://post-service}")
    private String postServiceUrl;

    private static final int VERIFIED_BADGE_PRICE = 9900;  // ₹99
    private static final int BOOST_POST_PRICE     = 4900;  // ₹49

    public CreateOrderResponse createOrder(String userEmail, CreateOrderRequest request) {
        if ((userEmail == null || userEmail.isBlank()) && request != null) {
            userEmail = request.getUserEmail();
        }
        if (request.getType() == null) throw new BadRequestException("Payment type is required.");
        if (userEmail == null || userEmail.isBlank()) throw new BadRequestException("Authenticated user email is required.");

        if (razorpayClient == null
                || razorpayKeyId == null || razorpayKeyId.isBlank()
                || razorpayKeySecret == null || razorpayKeySecret.isBlank()) {
            throw new BadRequestException("Payment gateway is not configured. Set RAZORPAY_KEY_ID and RAZORPAY_KEY_SECRET.");
        }

        if (request.getType() == Payment.PaymentType.VERIFIED_BADGE &&
            paymentRepository.existsByUserEmailAndTypeAndStatus(
                userEmail, Payment.PaymentType.VERIFIED_BADGE, Payment.PaymentStatus.SUCCESS)) {
            throw new BadRequestException("You already have a Verified Badge.");
        }

        if (request.getType() == Payment.PaymentType.BOOST_POST && request.getPostId() == null) {
            throw new BadRequestException("postId is required for BOOST_POST.");
        }

        int amount = request.getType() == Payment.PaymentType.VERIFIED_BADGE
                ? VERIFIED_BADGE_PRICE : BOOST_POST_PRICE;

        log.info("Creating Razorpay order for email={} type={} amount={}", userEmail, request.getType(), amount);

        JSONObject orderRequest = new JSONObject();
        orderRequest.put("amount", amount);
        orderRequest.put("currency", "INR");
        orderRequest.put("receipt", "rcpt_" + System.currentTimeMillis());
        orderRequest.put("payment_capture", 1);

        final String razorpayOrderId;
        try {
            Order razorpayOrder = razorpayClient.Orders.create(orderRequest);
            razorpayOrderId = razorpayOrder.get("id");
        } catch (RazorpayException e) {
            log.error("Razorpay create order failed for email={} type={}: {}", userEmail, request.getType(), e.getMessage());
            throw new BadRequestException("Unable to create Razorpay order. Check Razorpay keys and account mode (test/live).");
        }

        log.info("Razorpay order created: orderId={}", razorpayOrderId);

        Payment payment = new Payment();
        payment.setUserEmail(userEmail);
        payment.setPostId(request.getPostId());
        payment.setType(request.getType());
        payment.setAmount(amount);
        payment.setRazorpayOrderId(razorpayOrderId);
        payment.setStatus(Payment.PaymentStatus.PENDING);
        paymentRepository.save(payment);

        return new CreateOrderResponse(razorpayOrderId, amount, "INR", razorpayKeyId);
    }

    @Transactional
    public Map<String, String> verifyPayment(VerifyPaymentRequest request) {
        if (request.getRazorpayOrderId() == null || request.getRazorpayPaymentId() == null
                || request.getRazorpaySignature() == null) {
            throw new BadRequestException("orderId, paymentId and signature are all required.");
        }

        log.info("Verifying payment: orderId={}", request.getRazorpayOrderId());

        Payment payment = paymentRepository.findByRazorpayOrderId(request.getRazorpayOrderId())
                .orElseThrow(() -> new ResourceNotFoundException("Payment order not found."));

        boolean valid = verifySignature(
                request.getRazorpayOrderId(),
                request.getRazorpayPaymentId(),
                request.getRazorpaySignature());

        if (!valid) {
            payment.setStatus(Payment.PaymentStatus.FAILED);
            paymentRepository.save(payment);
            log.warn("Signature FAILED for orderId={}", request.getRazorpayOrderId());
            throw new BadRequestException("Payment verification failed. Invalid signature.");
        }

        payment.setRazorpayPaymentId(request.getRazorpayPaymentId());
        payment.setRazorpaySignature(request.getRazorpaySignature());
        payment.setStatus(Payment.PaymentStatus.SUCCESS);
        paymentRepository.save(payment);

        log.info("Payment SUCCESS: orderId={} type={}", request.getRazorpayOrderId(), payment.getType());

        if (payment.getType() == Payment.PaymentType.VERIFIED_BADGE) {
            grantVerifiedBadge(payment.getUserEmail());
        } else if (payment.getType() == Payment.PaymentType.BOOST_POST) {
            grantBoostPost(payment.getPostId());
        }

        String message = payment.getType() == Payment.PaymentType.VERIFIED_BADGE
                ? "Congratulations! Your profile is now Verified ✓"
                : "Your post has been boosted to Trending!";

        return Map.of("status", "SUCCESS", "message", message);
    }

    private boolean verifySignature(String orderId, String paymentId, String signature) {
        try {
            String data = orderId + "|" + paymentId;
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(razorpayKeySecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] hash = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash).equals(signature);
        } catch (Exception e) {
            log.error("Signature verification error: {}", e.getMessage());
            return false;
        }
    }

    private void grantVerifiedBadge(String userEmail) {
        try {
            restTemplate.put(authServiceUrl + "/auth/user/verify-by-email?email=" + userEmail, null);
            log.info("Verified badge granted to email={}", userEmail);
        } catch (Exception e) {
            log.warn("Failed to grant verified badge to email={}: {}", userEmail, e.getMessage());
        }
    }

    private void grantBoostPost(Long postId) {
        try {
            restTemplate.put(postServiceUrl + "/posts/" + postId + "/boost", null);
            log.info("Post boosted: postId={}", postId);
        } catch (Exception e) {
            log.warn("Failed to boost postId={}: {}", postId, e.getMessage());
        }
    }

    public List<Payment> getPaymentHistory(String userEmail) {
        if (userEmail == null || userEmail.isBlank()) {
            throw new BadRequestException("Email is required to fetch payment history.");
        }
        return paymentRepository.findByUserEmailOrderByCreatedAtDesc(userEmail);
    }

    public List<Payment> getAllPayments() {
        return paymentRepository.findAll();
    }

    public List<Payment> getSuccessfulPayments() {
        return paymentRepository.findByStatusOrderByCreatedAtDesc(Payment.PaymentStatus.SUCCESS);
    }
}
