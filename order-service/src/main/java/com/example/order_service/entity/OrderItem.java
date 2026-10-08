package com.example.order_service.entity;

import java.math.BigDecimal;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@AllArgsConstructor
@NoArgsConstructor
@Data
@Entity
@Table(name = "order_items")
public class OrderItem {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "order_id")
    private Order order;

    private Long productId; // Product ID for simplicity
    private int quantity;
    @Column(nullable = false, precision = 12, scale = 2) // numeric(12,2): exact money, never a float
    private BigDecimal price;

    // Product title and image as they were when ordered (null for items from before V7 until first read).
    private String title;
    private String image;
}
