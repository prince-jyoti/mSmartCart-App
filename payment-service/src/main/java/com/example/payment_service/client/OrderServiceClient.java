package com.example.payment_service.client;

import com.example.payment_service.configuration.FeignClientConfig;
import com.example.payment_service.dto.OrderRes;
import com.example.payment_service.utils.BaseResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;


@FeignClient(name = "order-service", url = "${order.service.url}", fallback = OrderServiceFallback.class, configuration = FeignClientConfig.class)
public interface OrderServiceClient {
    @GetMapping("/orders/{id}")
    BaseResponse<OrderRes> getOrderById(Long id);
}