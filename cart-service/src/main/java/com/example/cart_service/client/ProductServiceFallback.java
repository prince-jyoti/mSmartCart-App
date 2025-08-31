package com.example.cart_service.client;

import com.example.cart_service.dto.ProductDTO;
import com.example.cart_service.utils.BaseResponse;

public class ProductServiceFallback implements ProductServiceClient {
    @Override
    public BaseResponse<ProductDTO> getById(Long id) {
        ProductDTO productDTO = new ProductDTO();
        productDTO.setId(id);
        productDTO.setTitle("default-title");
        return new BaseResponse<>(503, "Product service unavailable", productDTO);
    }
}

