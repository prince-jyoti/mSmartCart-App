package com.example.payment_service.entity;

import java.time.Instant;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@AllArgsConstructor
@NoArgsConstructor
@Data
@Entity
@Table(name = "payments")
public class Payment {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Transient
    private String orderId; // Used for DTO mapping only, not persisted

    @Column(unique = true)
    private String paymentId; // Razorpay payment_id

    private String signature;

    private String status;    // "SUCCESS", "FAILED", "PENDING"

    // A moment in time, stored as timestamptz and sent as ISO-8601 UTC ("...Z") so clients can show local time.
    private Instant paymentDate;

    private String orderIdRef; // public order id (ORD-...) of the order this pays for

    private Long userId; // user who made the payment (JWT subject); only they or an admin may read it
}