package com.connectsphere.payment.controller;

import com.connectsphere.payment.dto.CreateOrderRequest;
import com.connectsphere.payment.dto.CreateOrderResponse;
import com.connectsphere.payment.dto.VerifyPaymentRequest;
import com.connectsphere.payment.entity.Payment;
import com.connectsphere.payment.service.PaymentService;
import com.razorpay.RazorpayException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    /** POST /payments/create-order — X-User-Email injected by API Gateway */
    @PostMapping("/create-order")
    public ResponseEntity<CreateOrderResponse> createOrder(
            @RequestHeader("X-User-Email") String userEmail,
            @RequestBody CreateOrderRequest request) throws RazorpayException {
        return ResponseEntity.ok(paymentService.createOrder(userEmail, request));
    }

    /** POST /payments/verify — verify Razorpay signature and mark payment SUCCESS */
    @PostMapping("/verify")
    public ResponseEntity<Map<String, String>> verifyPayment(
            @RequestBody VerifyPaymentRequest request) {
        return ResponseEntity.ok(paymentService.verifyPayment(request));
    }

    /** GET /payments/history — payment history for the authenticated user */
    @GetMapping("/history")
    public ResponseEntity<List<Payment>> getHistory(
            @RequestHeader("X-User-Email") String userEmail) {
        return ResponseEntity.ok(paymentService.getPaymentHistory(userEmail));
    }

    /** GET /payments/admin/all — admin: all payments */
    @GetMapping("/admin/all")
    public ResponseEntity<List<Payment>> getAllPayments() {
        return ResponseEntity.ok(paymentService.getAllPayments());
    }

    /** GET /payments/admin/successful — admin: successful payments only */
    @GetMapping("/admin/successful")
    public ResponseEntity<List<Payment>> getSuccessful() {
        return ResponseEntity.ok(paymentService.getSuccessfulPayments());
    }
}
