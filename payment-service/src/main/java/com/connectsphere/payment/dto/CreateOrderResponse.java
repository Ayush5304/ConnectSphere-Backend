package com.connectsphere.payment.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class CreateOrderResponse {
    private String razorpayOrderId;
    private Integer amount;
    private String currency;
    private String keyId;
}
