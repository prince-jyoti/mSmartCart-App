package com.example.payment_service.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;


@AllArgsConstructor
@NoArgsConstructor
@Data
public class PaymentRes {
    private Long id;

    private String paymentId; // Razorpay payment_id

    private String orderId;   // Razorpay order_id

    private String signature;

    private String status;    // "SUCCESS", "FAILED", "PENDING"

    private LocalDateTime paymentDate;

    private String orderIdRef; // Reference to Order entity as String (id)

}