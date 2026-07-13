package com.example.payment_service.client;

import com.example.payment_service.dto.OrderRes;
import com.example.payment_service.dto.OrderStatusUpdateReq;
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

    @Override
    public BaseResponse<Void> updateOrderStatus(String orderId, OrderStatusUpdateReq statusUpdateReq) {
        // order-service is unreachable: the saga's compensation/completion step didn't land.
        // The order is left in its previous status (e.g. still PENDING) instead of PAID/PAYMENT_FAILED —
        // an honest gap in this synchronous implementation (see interview notes: a retry queue or
        // outbox pattern is the real fix, not swallowing this silently forever).
        return new BaseResponse<>(503, "Order service unavailable, status update not applied for order " + orderId, null);
    }
}