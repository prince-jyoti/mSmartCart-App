package com.example.payment_service.controller;

import jakarta.validation.Valid;
import com.example.payment_service.dto.PaymentReq;
import com.example.payment_service.dto.PaymentRes;
import com.example.payment_service.service.PaymentService;
import com.example.payment_service.utils.BaseResponse;
import kong.unirest.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/payments")
// Payments are only ever created by verification or reconciliation, never edited or deleted:
// they are the record of money that actually moved (and order details look them up).
public class PaymentController {
    @Autowired
    private PaymentService paymentService;

    // user-service puts the user's id in the token subject.
    private Long callerId(Jwt jwt) {
        return Long.valueOf(jwt.getSubject());
    }

    private boolean isAdmin(Jwt jwt) {
        return "ADMIN".equalsIgnoreCase(jwt.getClaimAsString("role"));
    }

    @PostMapping
    public ResponseEntity<BaseResponse<PaymentRes>> createPayment(@Valid @RequestBody PaymentReq paymentReq, @AuthenticationPrincipal Jwt jwt) {
        PaymentRes payment = paymentService.createPayment(paymentReq, callerId(jwt));
        return ResponseEntity.ok(new BaseResponse<>(200, "Payment created", payment));
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<BaseResponse<List<PaymentRes>>> getAllPayments() {
        List<PaymentRes> payments = paymentService.getAllPayments();
        return ResponseEntity.ok(new BaseResponse<>(200, "Payments fetched", payments));
    }

    @GetMapping("/{id}")
    public ResponseEntity<BaseResponse<PaymentRes>> getPaymentById(@PathVariable Long id, @AuthenticationPrincipal Jwt jwt) {
        PaymentRes payment = paymentService.getPaymentById(id, callerId(jwt), isAdmin(jwt));
        return ResponseEntity.ok(new BaseResponse<>(200, "Payment found", payment));
    }

    @PostMapping("/create-razorpay-order")
    public ResponseEntity<?> createRazorpayOrder(@RequestParam String orderIdRef, @AuthenticationPrincipal Jwt jwt) {
        JSONObject order = paymentService.createRazorpayOrder(orderIdRef, callerId(jwt));
        return ResponseEntity.ok(new BaseResponse<>(200, "Razorpay order created", order.toMap()));
    }
}
