package com.connectsphere.payment.entity;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.Locale;

@Entity
@Table(name = "payments")
@Data
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long paymentId;

    @Column(nullable = false)
    private String userEmail;

    private Long postId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentType type;

    @Column(unique = true)
    private String razorpayOrderId;

    private String razorpayPaymentId;

    private String razorpaySignature;

    /** Amount in paise. e.g. ₹99 = 9900 */
    @Column(nullable = false)
    private Integer amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentStatus status;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public enum PaymentType {
        VERIFIED_BADGE, BOOST_POST;

        @JsonCreator
        public static PaymentType from(String raw) {
            if (raw == null) return null;
            String normalized = raw.trim()
                    .toUpperCase(Locale.ROOT)
                    .replace('-', '_')
                    .replace(' ', '_');
            return switch (normalized) {
                case "VERIFIED_BADGE", "VERIFIEDBADGE", "VERIFIED" -> VERIFIED_BADGE;
                case "BOOST_POST", "BOOSTPOST", "BOOST" -> BOOST_POST;
                default -> throw new IllegalArgumentException("Invalid payment type: " + raw);
            };
        }

        @JsonValue
        public String toJson() {
            return name();
        }
    }

    public enum PaymentStatus {
        PENDING, SUCCESS, FAILED
    }
}
