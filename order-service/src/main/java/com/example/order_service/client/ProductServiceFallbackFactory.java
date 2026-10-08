package com.example.order_service.client;

import com.example.order_service.dto.ProductDTO;
import com.example.order_service.dto.StockReservationReq;
import com.example.order_service.utils.BaseResponse;
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

            @Override
            public BaseResponse<Void> reserve(StockReservationReq req) {
                throw FallbackErrors.translate(cause, "Product not found", "Product service unavailable");
            }
        };
    }
}
