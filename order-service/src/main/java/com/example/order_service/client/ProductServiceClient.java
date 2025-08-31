package com.example.order_service.client;

import com.example.order_service.configuration.FeignClientConfig;
import com.example.order_service.dto.ProductDTO;
import com.example.order_service.utils.BaseResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "product-service", url = "${product.service.url}", fallback = ProductServiceFallback.class, configuration = FeignClientConfig.class)
public interface ProductServiceClient {
    @GetMapping("/products/{id}")
    BaseResponse<ProductDTO> getById(@PathVariable Long id);
}
