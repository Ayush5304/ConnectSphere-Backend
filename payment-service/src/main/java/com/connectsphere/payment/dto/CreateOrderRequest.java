package com.connectsphere.payment.dto;

import com.connectsphere.payment.entity.Payment;
import lombok.Data;

@Data
public class CreateOrderRequest {
    private Payment.PaymentType type;
    /** Required only for BOOST_POST */
    private Long postId;
    /** Fallback when gateway user header is unavailable */
    private String userEmail;
}
