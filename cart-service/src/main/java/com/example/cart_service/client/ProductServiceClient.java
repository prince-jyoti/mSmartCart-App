package com.example.cart_service.client;

import com.example.cart_service.configuration.FeignClientConfig;
import com.example.cart_service.dto.ProductDTO;
import com.example.cart_service.utils.BaseResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;


@FeignClient(name = "product-service", fallback = ProductServiceFallback.class, configuration = FeignClientConfig.class)
public interface ProductServiceClient {
    @GetMapping("/products/{id}")
    BaseResponse<ProductDTO> getById(@PathVariable Long id);
}
