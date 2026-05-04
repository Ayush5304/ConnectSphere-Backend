package com.connectsphere.payment.config;

import com.razorpay.RazorpayClient;
import com.razorpay.RazorpayException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

@Configuration
public class AppConfig {

    private static final Logger log = LoggerFactory.getLogger(AppConfig.class);

    @Value("${razorpay.key.id:}")
    private String razorpayKeyId;

    @Value("${razorpay.key.secret:}")
    private String razorpayKeySecret;

    @Bean
    public RestTemplate restTemplate() { return new RestTemplate(); }

    /**
     * BUG-FIX: Original bean threw RazorpayException at startup when keys were
     * missing/blank, crashing the entire payment-service on first launch.
     *
     * Now: returns null when keys are not configured. PaymentService already
     * validates keys before use and throws a user-friendly BadRequestException.
     * The null client is never reached because that check comes first.
     */
    @Bean
    public RazorpayClient razorpayClient() {
        if (razorpayKeyId == null || razorpayKeyId.isBlank()
                || razorpayKeySecret == null || razorpayKeySecret.isBlank()) {
            log.warn("Razorpay keys not configured. Payment features will return an error until RAZORPAY_KEY_ID and RAZORPAY_KEY_SECRET are set.");
            return null;
        }
        try {
            return new RazorpayClient(razorpayKeyId, razorpayKeySecret);
        } catch (RazorpayException e) {
            log.error("Failed to initialize RazorpayClient: {}. Check your Razorpay keys.", e.getMessage());
            return null;
        }
    }
}
