package com.example.payment_service.client;

import com.example.payment_service.configuration.FeignClientConfig;
import com.example.payment_service.dto.OrderRes;
import com.example.payment_service.dto.OrderStatusUpdateReq;
import com.example.payment_service.utils.BaseResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;


@FeignClient(name = "order-service", fallback = OrderServiceFallback.class, configuration = FeignClientConfig.class)
public interface OrderServiceClient {
    @GetMapping("/orders/{id}")
    BaseResponse<OrderRes> getOrderById(Long id);

    @PatchMapping("/orders/by-order-id/{orderId}/status")
    BaseResponse<Void> updateOrderStatus(@PathVariable("orderId") String orderId, @RequestBody OrderStatusUpdateReq statusUpdateReq);
}