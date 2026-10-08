package com.example.product_service.services;

import com.example.product_service.exception.NotFoundException;
import com.example.product_service.dto.ProductReq;
import com.example.product_service.dto.ProductRes;
import com.example.product_service.entity.Product;
import com.example.product_service.repository.ProductRepo;
import lombok.extern.slf4j.Slf4j;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
public class ProductService {
    @Autowired
    private ProductRepo productRepo;
    @Autowired
    private ModelMapper modelMapper;

    public List<ProductRes> getAllProducts() {
        return productRepo.findAll().stream()
                .map(product -> modelMapper.map(product, ProductRes.class))
                .collect(Collectors.toList());
    }

    public ProductRes getProductById(Long id) {
        log.info("Fetching product with id: {}", id);
        return productRepo.findById(id)
                .map(product -> modelMapper.map(product, ProductRes.class))
                .orElseThrow(() -> new NotFoundException("Product not found"));
    }

    @Transactional
    public ProductRes createProduct(ProductReq dto) {
        Product product = modelMapper.map(dto, Product.class);
        product.setId(null); // always a new row, whatever id the client sent
        product = productRepo.save(product);
        return modelMapper.map(product, ProductRes.class);
    }

    @Transactional
    public ProductRes updateProduct(Long id, ProductReq dto) {
        Product existingProduct = productRepo.findById(id)
                .orElseThrow(() -> new NotFoundException("Product not found"));
        modelMapper.map(dto, existingProduct);
        existingProduct.setId(id); // the path decides which product changes, not the body
        Product updatedProduct = productRepo.save(existingProduct);
        return modelMapper.map(updatedProduct, ProductRes.class);
    }

    @Transactional
    public void deleteProduct(Long id) {
        if (!productRepo.existsById(id)) {
            throw new NotFoundException("Product not found");
        }
        productRepo.deleteById(id);
    }
}
