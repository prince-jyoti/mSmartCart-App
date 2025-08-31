package com.example.payment_service.client;

import com.example.payment_service.dto.OrderRes;
import com.example.payment_service.utils.BaseResponse;
import org.springframework.stereotype.Component;


@Component
public class OrderServiceFallback implements OrderServiceClient {
    @Override
    public BaseResponse<OrderRes> getOrderById(Long id) {
        OrderRes orderRes = new OrderRes();
        orderRes.setId(id);
        return new BaseResponse<>(503, "Product service unavailable", orderRes);
    }
}