package com.example.order_service.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;


@AllArgsConstructor
@NoArgsConstructor
@Data
@Entity
@Table(name = "orders")
public class Order {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String orderId; // generated UID or Razorpay order_id

//    @ManyToOne
//    @JoinColumn(name = "user_id")
//    private User user;

    private Long userId; // User ID for simplicity

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL)
    private List<OrderItem> items;

    private double totalAmount;
    private String status; // "PENDING", "PAID", "FAILED"
    private LocalDateTime createdAt;

//    @OneToOne(mappedBy = "order", cascade = CascadeType.ALL)
//    private Payment payment;

    private Long paymentId; // Payment ID for simplicity
}
