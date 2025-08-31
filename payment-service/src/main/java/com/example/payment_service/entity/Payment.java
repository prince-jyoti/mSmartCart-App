package com.example.payment_service.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;


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

    private String paymentId; // Razorpay payment_id

    private String signature;

    private String status;    // "SUCCESS", "FAILED", "PENDING"

    private LocalDateTime paymentDate;

//    @OneToOne
//    @JoinColumn(name = "order_id")
//    private Order order;
    private Long orderIdRef; // Reference to Order entity as Long
}