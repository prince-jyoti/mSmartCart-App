package com.example.product_service.controller;

import com.example.product_service.configuration.SecurityConfig;
import com.example.product_service.services.ProductService;
import com.example.product_service.services.StockService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// The real SecurityConfig in front of the controllers: the service must reject requests without a
// token itself, because its port can be reached without going through the gateway.
@WebMvcTest({ProductController.class, StockReservationController.class})
@Import(SecurityConfig.class)
@TestPropertySource(properties = "jwt.secret=test-secret-test-secret-test-secret-test-secret")
class ProductSecurityTest {
    private static final String PRODUCT = "{\"title\":\"Mug\",\"price\":10,\"stock\":1}";

    @Autowired
    private MockMvc mvc;
    @MockitoBean
    private ProductService productService;
    @MockitoBean
    private StockService stockService;

    @Test
    void theCatalogueCanBeReadWithoutLoggingIn() throws Exception {
        when(productService.getAllProducts()).thenReturn(List.of());

        mvc.perform(get("/products")).andExpect(status().isOk());
    }

    @Test
    void changingProductsNeedsAnAdminToken() throws Exception {
        mvc.perform(post("/products").contentType(MediaType.APPLICATION_JSON).content(PRODUCT)).andExpect(status().isUnauthorized());
        mvc.perform(delete("/products/1")).andExpect(status().isUnauthorized());
        mvc.perform(post("/products").with(as("USER")).contentType(MediaType.APPLICATION_JSON).content(PRODUCT)).andExpect(status().isForbidden());
        mvc.perform(delete("/products/1").with(as("ADMIN"))).andExpect(status().isOk());
    }

    @Test
    void stockReservationsNeedAToken() throws Exception {
        mvc.perform(post("/internal/stock/reservations").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orderId\":\"ORD-1\",\"items\":[{\"productId\":1,\"quantity\":1}]}"))
                .andExpect(status().isUnauthorized());
    }

    private static RequestPostProcessor as(String role) {
        return jwt().jwt(j -> j.subject("5").claim("role", role)).authorities(new SimpleGrantedAuthority("ROLE_" + role));
    }
}
