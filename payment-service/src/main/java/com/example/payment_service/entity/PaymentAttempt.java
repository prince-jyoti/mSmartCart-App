package com.example.payment_service.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

// A Razorpay order created for one of our orders (see V2__payment_attempts.sql). Holds what
// PaymentReconciler needs to record a payment without calling order-service (it has no user token).
@Data
@NoArgsConstructor
@Entity
@Table(name = "payment_attempts")
public class PaymentAttempt {
    public enum Status { OPEN, PAID, ABANDONED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String razorpayOrderId;

    @Column(nullable = false)
    private String orderIdRef;   // our ORD-... id

    private Long userId;         // who started the payment

    @Column(nullable = false)
    private long amountPaise;    // what the order cost when the payment was started

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    public void moveTo(Status next) {
        this.status = next;
        this.updatedAt = LocalDateTime.now();
    }
}
