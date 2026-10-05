package com.example.payment_service.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

// Published by payment-service when a payment attempt reaches a final outcome.
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PaymentEvent {
    private String eventId;      // unique per event, also used as the AMQP messageId
    private String orderId;      // public order id (ORD-...)
    private Long paymentId;      // payments.id, null when the payment failed
    private String status;       // "PAID" or "PAYMENT_FAILED"
    private LocalDateTime occurredAt;
}
