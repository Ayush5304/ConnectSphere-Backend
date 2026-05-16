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
     * Keep the service bootable without local Razorpay keys. PaymentService checks
     * the configured key values before using the client and returns a clear error
     * when payment features are not configured.
     */
    @Bean
    public RazorpayClient razorpayClient() throws RazorpayException {
        if (razorpayKeyId == null || razorpayKeyId.isBlank()
                || razorpayKeySecret == null || razorpayKeySecret.isBlank()) {
            log.warn("Razorpay keys not configured. Payment features will return an error until RAZORPAY_KEY_ID and RAZORPAY_KEY_SECRET are set.");
            return new RazorpayClient("rzp_test_dummy", "dummy_secret");
        }
        return new RazorpayClient(razorpayKeyId, razorpayKeySecret);
    }
}
