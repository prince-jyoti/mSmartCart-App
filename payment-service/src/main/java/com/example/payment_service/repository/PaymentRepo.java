package com.example.payment_service.repository;

import com.example.payment_service.entity.Payment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PaymentRepo extends JpaRepository<Payment,Long> {
    Optional<Payment> findByPaymentId(String paymentId);

}