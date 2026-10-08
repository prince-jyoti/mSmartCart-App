package com.example.cart_service.client;

import com.example.cart_service.dto.ProductDTO;
import com.example.cart_service.utils.BaseResponse;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

@Component
public class ProductServiceFallbackFactory implements FallbackFactory<ProductServiceClient> {
    @Override
    public ProductServiceClient create(Throwable cause) {
        return new ProductServiceClient() {
            @Override
            public BaseResponse<ProductDTO> getById(Long id) {
                throw FallbackErrors.translate(cause, "Product not found", "Product service unavailable");
            }
        };
    }
}
