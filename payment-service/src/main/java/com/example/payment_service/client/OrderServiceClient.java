package com.example.payment_service.client;

import com.example.payment_service.configuration.FeignClientConfig;
import com.example.payment_service.dto.OrderRes;
import com.example.payment_service.utils.BaseResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;


@FeignClient(name = "order-service", fallback = OrderServiceFallback.class, configuration = FeignClientConfig.class)
public interface OrderServiceClient {
    @GetMapping("/orders/by-order-id/{orderId}")
    BaseResponse<OrderRes> getOrderByOrderId(@PathVariable("orderId") String orderId);

}