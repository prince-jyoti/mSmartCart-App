package com.example.product_service.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

// Events this service has already applied. Written in the same transaction as the change the
// event causes, so a redelivered message (delivery is at-least-once) is skipped, not reapplied.
@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "processed_events")
public class ProcessedEvent {
    @Id
    private String eventId;

    private LocalDateTime processedAt;
}
