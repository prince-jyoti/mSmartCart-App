package com.example.product_service.controller;

import com.example.product_service.dto.ProductRes;
import com.example.product_service.exception.GlobalExceptionHandler;
import com.example.product_service.exception.NotFoundException;
import com.example.product_service.services.ProductService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// The HTTP contract of GlobalExceptionHandler and request validation, through a real
// controller. The same handler is copied into every service.
class ProductControllerErrorTest {
    private final ProductService productService = mock(ProductService.class);
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        ProductController controller = new ProductController();
        ReflectionTestUtils.setField(controller, "productService", productService);
        mvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void foundProductIs200InTheUsualBody() throws Exception {
        ProductRes p = new ProductRes();
        p.setId(1L);
        p.setTitle("Mug");
        when(productService.getProductById(1L)).thenReturn(p);

        mvc.perform(get("/products/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.data.title").value("Mug"));
    }

    @Test
    void missingProductIs404() throws Exception {
        when(productService.getProductById(9L)).thenThrow(new NotFoundException("Product not found"));

        mvc.perform(get("/products/9"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value("Product not found"));
    }

    @Test
    void invalidProductIs400WithOneMessagePerField() throws Exception {
        mvc.perform(post("/products").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"price\":0,\"stock\":-1,\"discount\":150}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Validation failed"))
                .andExpect(jsonPath("$.data.title").value("Title is required"))
                .andExpect(jsonPath("$.data.price").value("Price must be greater than 0"))
                .andExpect(jsonPath("$.data.stock").value("Stock cannot be negative"))
                .andExpect(jsonPath("$.data.discount").value("Discount must be 0-100"));
        verify(productService, never()).createProduct(any());
    }

    @Test
    void priceMustBePresentAndHaveAtMostTwoDecimals() throws Exception {
        mvc.perform(post("/products").contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"Mug\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.data.price").value("Price is required"));
        mvc.perform(post("/products").contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"Mug\",\"price\":10.999}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.data.price").value("Price can have at most 2 decimal places"));
        verify(productService, never()).createProduct(any());
    }

    @Test
    void malformedJsonIs400() throws Exception {
        mvc.perform(post("/products").contentType(MediaType.APPLICATION_JSON).content("{\"title\":"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Malformed or unreadable request body"));
    }

    @Test
    void nonNumericIdIs400() throws Exception {
        mvc.perform(get("/products/abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Invalid value for 'id'"));
    }

    @Test
    void accessDeniedIs403NotA500() throws Exception {
        doThrow(new AccessDeniedException("nope")).when(productService).deleteProduct(1L);

        mvc.perform(delete("/products/1"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Access denied"));
    }

    @Test
    void unexpectedErrorsAre500WithoutLeakingDetails() throws Exception {
        when(productService.getAllProducts()).thenThrow(new RuntimeException("SQL error near 'products' at line 1"));

        mvc.perform(get("/products"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.message").value("Internal server error"));
    }
}
