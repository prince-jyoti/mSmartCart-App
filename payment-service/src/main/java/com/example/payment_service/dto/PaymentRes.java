package com.example.payment_service.dto;

import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@AllArgsConstructor
@NoArgsConstructor
@Data
public class PaymentRes {
    private Long id;

    private String paymentId; // Razorpay payment_id

    private String orderId;   // Razorpay order_id

    private String status;    // "SUCCESS", "FAILED", "PENDING"

    private Instant paymentDate;

    private String orderIdRef; // Reference to Order entity as String (id)

}