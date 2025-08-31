package com.example.order_service.repository;

import com.example.order_service.entity.Order;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface OrderRepo  extends JpaRepository<Order,Long> {

    List<Order> findByUserId(Long userId);
    Optional<Order> findByOrderId(String orderId);
}
