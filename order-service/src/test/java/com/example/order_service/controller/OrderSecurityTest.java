package com.example.order_service.controller;

import com.example.order_service.configuration.SecurityConfig;
import com.example.order_service.dto.OrderRes;
import com.example.order_service.service.OrderService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// The real SecurityConfig in front of the controller: the service must reject requests without a
// token itself, because its port can be reached without going through the gateway.
@WebMvcTest(OrderController.class)
@Import(SecurityConfig.class)
@TestPropertySource(properties = "jwt.secret=test-secret-test-secret-test-secret-test-secret")
class OrderSecurityTest {
    @Autowired
    private MockMvc mvc;
    @MockitoBean
    private OrderService orderService;

    @Test
    void everyOrderEndpointNeedsAToken() throws Exception {
        mvc.perform(get("/orders")).andExpect(status().isUnauthorized());
        mvc.perform(get("/orders/1")).andExpect(status().isUnauthorized());
        mvc.perform(get("/orders/by-order-id/ORD-1")).andExpect(status().isUnauthorized());
        mvc.perform(post("/orders").contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isUnauthorized());
    }

    @Test
    void aValidTokenGetsThrough() throws Exception {
        when(orderService.getAllOrders("USER")).thenReturn(List.of());

        mvc.perform(get("/orders").with(user("USER"))).andExpect(status().isOk());
    }

    @Test
    void timestampsAreSentAsUtcInstants() throws Exception {
        // Through the app's real JSON setup: an ISO-8601 instant with "Z", so browsers can show local time.
        OrderRes order = new OrderRes();
        order.setOrderId("ORD-1");
        order.setCreatedAt(java.time.Instant.parse("2026-10-08T16:49:24.798Z"));
        when(orderService.getOrderByOrderId("ORD-1", "USER")).thenReturn(order);

        mvc.perform(get("/orders/by-order-id/ORD-1").with(user("USER")))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.data.createdAt")
                        .value("2026-10-08T16:49:24.798Z"));
    }

    @Test
    void ordersCannotBeEditedDirectly() throws Exception {
        mvc.perform(put("/orders/1").with(user("ADMIN")).contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"PAID\"}"))
                .andExpect(status().isMethodNotAllowed());
    }

    private static RequestPostProcessor user(String role) {
        return jwt().jwt(j -> j.subject("5").claim("role", role)).authorities(new SimpleGrantedAuthority("ROLE_" + role));
    }
}
