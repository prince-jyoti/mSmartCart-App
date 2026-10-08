package com.example.payment_service.controller;

import com.example.payment_service.configuration.SecurityConfig;
import com.example.payment_service.service.PaymentService;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// The real SecurityConfig in front of the controllers: the service must reject requests without a
// token itself, because its port can be reached without going through the gateway.
@WebMvcTest(PaymentController.class)
@Import(SecurityConfig.class)
@TestPropertySource(properties = {
        "jwt.secret=test-secret-test-secret-test-secret-test-secret",
        "razorpay.key=k", "razorpay.secret=s"})
class PaymentSecurityTest {
    @Autowired
    private MockMvc mvc;
    @MockitoBean
    private PaymentService paymentService;

    @Test
    void everyPaymentEndpointNeedsAToken() throws Exception {
        mvc.perform(get("/payments/1")).andExpect(status().isUnauthorized());
        mvc.perform(post("/payments").contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isUnauthorized());
        mvc.perform(post("/payments/create-razorpay-order").param("orderIdRef", "ORD-1")).andExpect(status().isUnauthorized());
    }

    @Test
    void listingAllPaymentsIsAdminOnly() throws Exception {
        when(paymentService.getAllPayments()).thenReturn(List.of());

        mvc.perform(get("/payments").with(as("USER"))).andExpect(status().isForbidden());
        mvc.perform(get("/payments").with(as("ADMIN"))).andExpect(status().isOk());
    }

    @Test
    void paymentsCannotBeEditedOrDeleted() throws Exception {
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/payments/1").with(as("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"captured\"}"))
                .andExpect(status().isMethodNotAllowed());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete("/payments/1").with(as("ADMIN")))
                .andExpect(status().isMethodNotAllowed());
    }

    private static RequestPostProcessor as(String role) {
        return jwt().jwt(j -> j.subject("5").claim("role", role)).authorities(new SimpleGrantedAuthority("ROLE_" + role));
    }
}
