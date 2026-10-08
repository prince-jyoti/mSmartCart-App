package com.example.product_service.controller;

import jakarta.validation.Valid;
import com.example.product_service.dto.ProductReq;
import com.example.product_service.dto.ProductRes;
import com.example.product_service.services.ProductService;
import com.example.product_service.utils.BaseResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
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

        List<ProductRes> products = productService.getAllProducts();
        return ResponseEntity.ok(new BaseResponse<>(200, "All Products found", products));
    }

    @GetMapping("/{id}")
    public ResponseEntity<BaseResponse<ProductRes>> getById(@PathVariable Long id) {
        log.info("Fetching product with id: {}", id);
        ProductRes product = productService.getProductById(id);
        log.info("Fetched product: {}", product);
        return ResponseEntity.ok(new BaseResponse<>(200, "Product found", product));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping
    public ResponseEntity<BaseResponse<ProductRes>> create(@Valid @RequestBody ProductReq dto) {
        ProductRes product = productService.createProduct(dto);
        return ResponseEntity.ok(new BaseResponse<>(200, "Product created", product));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/{id}")
    public ResponseEntity<BaseResponse<ProductRes>> update(@PathVariable Long id, @Valid @RequestBody ProductReq dto) {
        ProductRes product = productService.updateProduct(id, dto);
        return ResponseEntity.ok(new BaseResponse<>(200, "Product updated", product));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/{id}")
    public ResponseEntity<BaseResponse<Void>> delete(@PathVariable Long id) {
        productService.deleteProduct(id);
        return ResponseEntity.ok(new BaseResponse<>(200, "Product deleted", null));
    }
}
