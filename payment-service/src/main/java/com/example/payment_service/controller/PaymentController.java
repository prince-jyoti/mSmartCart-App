package com.example.payment_service.controller;

import com.example.payment_service.dto.PaymentReq;
import com.example.payment_service.dto.PaymentRes;
import com.example.payment_service.service.PaymentService;
import com.example.payment_service.utils.BaseResponse;
import kong.unirest.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;


@RestController
@RequestMapping("/payments")
public class PaymentController {
    @Autowired
    private PaymentService paymentService;
    @PostMapping
    public ResponseEntity<BaseResponse<PaymentRes>> createPayment(@RequestBody PaymentReq paymentReq) {
        try {
            PaymentRes payment = paymentService.createPayment(paymentReq);
            return ResponseEntity.ok(new BaseResponse<>(200, "Payment created", payment));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new BaseResponse<>(500, "Error creating payment", null));
        }
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<BaseResponse<List<PaymentRes>>> getAllPayments(@RequestHeader("Authorization") String authHeader) {
        try {
            List<PaymentRes> payments = paymentService.getAllPayments();
            return ResponseEntity.ok(new BaseResponse<>(200, "Payments fetched", payments));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new BaseResponse<>(500, "Error fetching payments", null));
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<BaseResponse<PaymentRes>> getPaymentById(@PathVariable Long id) {
        try {
            PaymentRes payment = paymentService.getPaymentById(id);
            return ResponseEntity.ok(new BaseResponse<>(200, "Payment found", payment));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new BaseResponse<>(404, e.getMessage(), null));
        }
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<BaseResponse<PaymentRes>> updatePayment(@PathVariable Long id, @RequestBody PaymentReq paymentReq) {
        try {
            PaymentRes payment = paymentService.updatePayment(id, paymentReq);
            return ResponseEntity.ok(new BaseResponse<>(200, "Payment updated", payment));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new BaseResponse<>(404, e.getMessage(), null));
        }
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<BaseResponse<Void>> deletePayment(@PathVariable Long id) {
        try {
            paymentService.deletePayment(id);
            return ResponseEntity.ok(new BaseResponse<>(200, "Payment deleted", null));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new BaseResponse<>(404, e.getMessage(), null));
        }
    }


    @PostMapping("/create-razorpay-order")
    public ResponseEntity<?> createRazorpayOrder(@RequestParam int amount, @RequestParam String currency, @RequestParam String receipt) {
        try {
            JSONObject order = paymentService.createRazorpayOrder(amount, currency, receipt);
            return ResponseEntity.ok(new BaseResponse<>(200, "Razorpay order created", order.toMap()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new BaseResponse<>(500, "Error creating Razorpay order: " + e.getMessage(), null));
        }
    }
}
