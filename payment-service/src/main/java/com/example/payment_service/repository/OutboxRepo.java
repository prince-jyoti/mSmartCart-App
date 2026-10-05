package com.example.payment_service.repository;

import com.example.payment_service.entity.OutboxEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OutboxRepo extends JpaRepository<OutboxEvent, Long> {
    List<OutboxEvent> findTop50ByPublishedAtIsNullOrderByIdAsc();
}
