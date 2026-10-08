package com.example.payment_service.dto;

import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@AllArgsConstructor
@NoArgsConstructor
@Data
// What the browser sends after Razorpay checkout, for verification. Unknown fields are ignored.
public class PaymentReq {

    @NotBlank(message = "paymentId is required")

    private String paymentId; // Razorpay payment_id

    @NotBlank(message = "orderId is required")

    private String orderId;   // Razorpay order_id

    @NotBlank(message = "signature is required")

    private String signature;

    @NotBlank(message = "orderIdRef is required")

    private String orderIdRef; // Reference to Order entity as String (id)
}