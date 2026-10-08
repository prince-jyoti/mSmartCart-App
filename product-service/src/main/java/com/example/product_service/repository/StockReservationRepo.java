package com.example.product_service.repository;

import com.example.product_service.entity.StockReservation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface StockReservationRepo extends JpaRepository<StockReservation, Long> {
    List<StockReservation> findByOrderId(String orderId);

    boolean existsByOrderId(String orderId);
}
