package com.example.order_service.repository;

import java.time.Instant;
import com.example.order_service.entity.Order;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;

import java.util.List;
import java.util.Optional;

public interface OrderRepo  extends JpaRepository<Order,Long> {

    List<Order> findByUserId(Long userId);
    Optional<Order> findByOrderId(String orderId);

    // Candidates for expiry, oldest first, in batches.
    List<Order> findTop100ByStatusInAndCreatedAtBeforeOrderByCreatedAtAsc(Collection<String> statuses, Instant cutoff);

    // Conditional, so it can't overwrite an order that was paid (or expired by another
    // instance) after it was selected. Returns 1 only for the caller that expired it.
    @Modifying
    @Query("update Order o set o.status = 'EXPIRED' where o.id = :id and o.status in ('PENDING', 'PAYMENT_FAILED')")
    int markExpired(@Param("id") Long id);
}
