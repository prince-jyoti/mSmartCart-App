package com.example.product_service.controller;

import com.example.product_service.dto.ProductReq;
import com.example.product_service.dto.ProductRes;
import com.example.product_service.services.ProductService;
import com.example.product_service.utils.BaseResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/products")
public class ProductController {
    @Autowired
    private ProductService productService;

    @GetMapping
    public ResponseEntity<BaseResponse<List<ProductRes>>> getAll() {
        try {

            List<ProductRes> products = productService.getAllProducts();
            return ResponseEntity.ok(new BaseResponse<>(200, "All Products found", products));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new BaseResponse<>(500, "Error fetching products", null));
        }
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<BaseResponse<ProductRes>> getById(@PathVariable Long id) {
        try {
            log.info("Fetching product with id: {}", id);
            ProductRes product = productService.getProductById(id);
            log.info("Fetched product: {}", product);
            return ResponseEntity.ok(new BaseResponse<>(200, "Product found", product));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new BaseResponse<>(404, e.getMessage(), null));
        }
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping
    public ResponseEntity<BaseResponse<ProductRes>> create(@RequestBody ProductReq dto) {
        try {
            ProductRes product = productService.createProduct(dto);
            return ResponseEntity.ok(new BaseResponse<>(200, "Product created", product));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new BaseResponse<>(500, "Error creating product", null));
        }
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/{id}")
    public ResponseEntity<BaseResponse<ProductRes>> update(@PathVariable Long id, @RequestBody ProductReq dto) {
        try {
            ProductRes product = productService.updateProduct(id, dto);
            return ResponseEntity.ok(new BaseResponse<>(200, "Product updated", product));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new BaseResponse<>(404, e.getMessage(), null));
        }
    }

    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/{id}")
    public ResponseEntity<BaseResponse<Void>> delete(@PathVariable Long id) {
        try {
            boolean deleted = productService.deleteProduct(id);
            if (deleted) {
                return ResponseEntity.ok(new BaseResponse<>(200, "Product deleted", null));
            } else {
                throw new RuntimeException("Product not found");
            }
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new BaseResponse<>(404, e.getMessage(), null));
        }
    }
}
