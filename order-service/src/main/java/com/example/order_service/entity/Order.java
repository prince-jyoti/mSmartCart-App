package com.example.order_service.entity;

import java.time.Instant;
import java.math.BigDecimal;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@AllArgsConstructor
@NoArgsConstructor
@Data
@Entity
// Declared as named indexes: Hibernate's ddl-auto=update adds these to an existing table,
// but silently skips a column-level @Column(unique = true).
@Table(name = "orders", indexes = {
        @Index(name = "uk_orders_order_id", columnList = "orderId", unique = true),
        @Index(name = "idx_orders_user_id", columnList = "userId")
})
public class Order {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false) // unique (see @Table); looked up by payment-service and the payment consumer
    private String orderId; // public id, ORD-<uuid>

    private Long userId; // User ID for simplicity

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL)
    private List<OrderItem> items;

    @Column(nullable = false, precision = 12, scale = 2) // numeric(12,2): exact money, never a float
    private BigDecimal totalAmount;
    private String status; // "PENDING", "PAID", "FAILED"
    // A moment in time, stored as timestamptz and sent as ISO-8601 UTC ("...Z") so clients can show local time.
    private Instant createdAt;

    private Long paymentId; // Payment ID for simplicity
}
