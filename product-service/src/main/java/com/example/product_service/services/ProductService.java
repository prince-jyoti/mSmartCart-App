package com.example.product_service.services;

import com.example.product_service.dto.ProductReq;
import com.example.product_service.dto.ProductRes;
import com.example.product_service.entity.Product;
import com.example.product_service.repository.ProductRepo;
import lombok.extern.slf4j.Slf4j;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
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
        try {
            List<Product> products=productRepo.findAll();
            return products.stream()
                    .map(product -> modelMapper.map(product, ProductRes.class))
                    .collect(Collectors.toList());
        } catch (Exception e) {
//            log.error("Error fetching products", e);
            throw new RuntimeException("Error fetching products");
        }
    }

    public ProductRes getProductById(Long id) {
        log.info("Fetching product with id: {}", id);
        return productRepo.findById(id)
                .map(product -> modelMapper.map(product, ProductRes.class))
                .orElseThrow(() -> new RuntimeException("Product not found"));
    }

    @Transactional
    public ProductRes createProduct(ProductReq dto) {
        try {
            Product product = modelMapper.map(dto, Product.class);
            product = productRepo.save(product);
            return modelMapper.map(product, ProductRes.class);
        } catch (DataIntegrityViolationException e) {
//            log.error("Product creation failed due to data integrity issues", e);
            throw new RuntimeException("Product creation failed due to data integrity issues");
        } catch (Exception e) {
//            log.error("An unexpected error occurred while creating the product", e);
            throw new RuntimeException("An unexpected error occurred while creating the product");
        }
    }

    @Transactional
    public ProductRes updateProduct(Long id, ProductReq dto) {
        try {
            Product existingProduct = productRepo.findById(id)
                    .orElseThrow(() -> new RuntimeException("Product not found"));
            modelMapper.map(dto, existingProduct);
            Product updatedProduct = productRepo.save(existingProduct);
            return modelMapper.map(updatedProduct, ProductRes.class);
        } catch (Exception e) {
//            log.error("Error updating product", e);
            throw new RuntimeException(e.getMessage());
        }
    }

    @Transactional
    public boolean deleteProduct(Long id) {
        try {
            if (productRepo.existsById(id)) {
                productRepo.deleteById(id);
                return true;
            } else {
                throw new RuntimeException("Product not found");
            }
        } catch (Exception e) {
//            log.error("Failed to delete product with id: {}", id, e);
            throw new RuntimeException(e.getMessage());
        }
    }
}
