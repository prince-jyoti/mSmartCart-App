package com.example.order_service.client;

import com.example.order_service.dto.ProductDTO;
import com.example.order_service.utils.BaseResponse;
import org.springframework.stereotype.Component;


@Component
public class ProductServiceFallback implements ProductServiceClient {
    @Override
    public BaseResponse<ProductDTO> getById(Long id) {
        ProductDTO productDTO = new ProductDTO();
        productDTO.setId(id);
        productDTO.setTitle("default-title");
        return new BaseResponse<>(503, "Product service unavailable", productDTO);
    }
}