package com.example.payment_service.client;

import com.example.payment_service.dto.OrderRes;
import com.example.payment_service.utils.BaseResponse;
import org.springframework.stereotype.Component;


@Component
public class OrderServiceFallback implements OrderServiceClient {
    @Override
    public BaseResponse<OrderRes> getOrderByOrderId(String orderId) {
        return new BaseResponse<>(503, "Order service unavailable", null);
    }
}
