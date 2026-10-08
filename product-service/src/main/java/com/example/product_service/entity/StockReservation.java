package com.example.product_service.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

// Stock held for one line of an order (see V2__stock_reservations.sql).
@Data
@NoArgsConstructor
@Entity
@Table(name = "stock_reservations")
public class StockReservation {
    public enum Status { RESERVED, CONFIRMED, RELEASED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String orderId;

    @Column(nullable = false)
    private Long productId;

    @Column(nullable = false)
    private int quantity;

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
