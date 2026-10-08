package com.example.order_service.client;

import com.example.order_service.configuration.FeignClientConfig;
import com.example.order_service.dto.ProductDTO;
import com.example.order_service.utils.BaseResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import com.example.order_service.dto.StockReservationReq;

@FeignClient(name = "product-service", fallbackFactory = ProductServiceFallbackFactory.class, configuration = FeignClientConfig.class)
public interface ProductServiceClient {
    @GetMapping("/products/{id}")
    BaseResponse<ProductDTO> getById(@PathVariable Long id);

    // All lines or none; 409 if any product lacks stock. Safe to repeat for the same order.
    @PostMapping("/internal/stock/reservations")
    BaseResponse<Void> reserve(@RequestBody StockReservationReq req);
}
