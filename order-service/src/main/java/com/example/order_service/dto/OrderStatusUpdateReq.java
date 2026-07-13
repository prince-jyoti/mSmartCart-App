package com.example.order_service.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@AllArgsConstructor
@NoArgsConstructor
@Data
public class OrderStatusUpdateReq {
    private String status; // "PAID", "PAYMENT_FAILED"
    private Long paymentId;
}
