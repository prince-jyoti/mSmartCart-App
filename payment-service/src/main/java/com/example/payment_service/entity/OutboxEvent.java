package com.example.payment_service.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

// Outbox pattern: the event is written in the same DB transaction as the payment it
// describes, then a background job publishes it. Either both exist or neither does.
@Data
@NoArgsConstructor
@Entity
@Table(name = "outbox_events")
public class OutboxEvent {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String eventId;

    @Column(nullable = false)
    private String routingKey;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String payload;

    private LocalDateTime createdAt;

    private LocalDateTime publishedAt; // null until the broker has confirmed it
}
