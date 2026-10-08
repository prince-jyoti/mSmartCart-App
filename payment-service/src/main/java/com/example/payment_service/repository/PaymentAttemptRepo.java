package com.example.payment_service.repository;

import com.example.payment_service.entity.PaymentAttempt;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface PaymentAttemptRepo extends JpaRepository<PaymentAttempt, Long> {
    Optional<PaymentAttempt> findByRazorpayOrderId(String razorpayOrderId);

    // Oldest first, in batches.
    List<PaymentAttempt> findTop100ByStatusAndCreatedAtBeforeOrderByCreatedAtAsc(PaymentAttempt.Status status, LocalDateTime before);
}
