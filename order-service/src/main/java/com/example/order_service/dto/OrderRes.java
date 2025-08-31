package com.example.order_service.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;



@AllArgsConstructor
@NoArgsConstructor
@Data
public class OrderRes {
    private Long id;
    private String orderId;
    private UserDTO  user;
    private List<OrderItemDTO> items;
    private double totalAmount;
    private String status;
    private LocalDateTime createdAt;
    private PaymentRes payment;
}
