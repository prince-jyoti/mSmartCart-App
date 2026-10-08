package com.example.payment_service.client;

import com.example.payment_service.dto.OrderRes;
import com.example.payment_service.utils.BaseResponse;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

@Component
public class OrderServiceFallbackFactory implements FallbackFactory<OrderServiceClient> {
    @Override
    public OrderServiceClient create(Throwable cause) {
        return new OrderServiceClient() {
            @Override
            public BaseResponse<OrderRes> getOrderByOrderId(String orderId) {
                throw FallbackErrors.translate(cause, "Order not found", "Order service unavailable");
            }
        };
    }
}
