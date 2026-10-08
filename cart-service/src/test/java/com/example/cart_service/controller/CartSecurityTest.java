package com.example.cart_service.controller;

import com.example.cart_service.configuration.SecurityConfig;
import com.example.cart_service.dto.CartReq;
import com.example.cart_service.service.CartService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// The real SecurityConfig in front of the controllers: the service must reject requests without a
// token itself, because its port can be reached without going through the gateway.
@WebMvcTest(CartController.class)
@Import(SecurityConfig.class)
@TestPropertySource(properties = "jwt.secret=test-secret-test-secret-test-secret-test-secret")
class CartSecurityTest {
    @Autowired
    private MockMvc mvc;
    @MockitoBean
    private CartService cartService;

    @Test
    void everyCartEndpointNeedsAToken() throws Exception {
        mvc.perform(get("/carts")).andExpect(status().isUnauthorized());
        mvc.perform(post("/carts/add").contentType(MediaType.APPLICATION_JSON).content("{\"productId\":1}")).andExpect(status().isUnauthorized());
        mvc.perform(delete("/carts/clear")).andExpect(status().isUnauthorized());
    }

    @Test
    void aValidTokenGetsThrough() throws Exception {
        when(cartService.getCartByUser()).thenReturn(new CartReq());

        mvc.perform(get("/carts").with(jwt().jwt(j -> j.subject("5").claim("role", "USER"))
                        .authorities(new SimpleGrantedAuthority("ROLE_USER"))))
                .andExpect(status().isOk());
    }
}
