package com.example.product_service.controller;

import com.example.product_service.dto.ReservationReq;
import com.example.product_service.services.StockService;
import com.example.product_service.utils.BaseResponse;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// Called by order-service when an order is placed. Not under /products, so the gateway
// (which only routes /products/**) doesn't expose it, and it requires a valid token.
@RestController
@RequestMapping("/internal/stock")
public class StockReservationController {
    @Autowired
    private StockService stockService;

    @PostMapping("/reservations")
    public ResponseEntity<BaseResponse<Void>> reserve(@Valid @RequestBody ReservationReq req) {
        stockService.reserve(req);
        return ResponseEntity.ok(new BaseResponse<>(200, "Stock reserved", null));
    }
}
