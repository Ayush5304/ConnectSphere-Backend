package com.connectsphere.payment.repository;

import com.connectsphere.payment.entity.Payment;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, Long> {
    Optional<Payment> findByRazorpayOrderId(String razorpayOrderId);
    List<Payment> findByUserEmailOrderByCreatedAtDesc(String userEmail);
    boolean existsByUserEmailAndTypeAndStatus(String userEmail, Payment.PaymentType type, Payment.PaymentStatus status);
    List<Payment> findByStatusOrderByCreatedAtDesc(Payment.PaymentStatus status);
}
