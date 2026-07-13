package com.example.order_service.controller;

import com.example.order_service.dto.OrderReq;
import com.example.order_service.dto.OrderRes;
import com.example.order_service.dto.OrderStatusUpdateReq;
import com.example.order_service.service.OrderService;
import com.example.order_service.utils.BaseResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;


@RestController
@RequestMapping("/orders")
public class OrderController {
    @Autowired
    private OrderService orderService;

    private String getUserRoleOrThrow(Jwt jwt) {
        String role = jwt.getClaimAsString("role");
        if (role == null || role.isEmpty()) {
            throw new IllegalArgumentException("Invalid token: missing role");
        }
        return role;
    }

    @PostMapping
    public ResponseEntity<BaseResponse<OrderRes>> createOrder(@RequestBody OrderReq orderReq) {

        try {
            OrderRes order = orderService.createOrder(orderReq);
            return ResponseEntity.ok(new BaseResponse<>(200, "Order created", order));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new BaseResponse<>(500, "Error creating order", null));
        }
    }

    @GetMapping
    public ResponseEntity<BaseResponse<List<OrderRes>>> getAllOrder(@AuthenticationPrincipal Jwt jwt) {
        try {
            String role = getUserRoleOrThrow(jwt);
            List<OrderRes> orders = orderService.getAllOrders( role);
            return ResponseEntity.ok(new BaseResponse<>(200, "Orders fetched", orders));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new BaseResponse<>(500, "Error fetching orders", null));
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<BaseResponse<OrderRes>> getOrderById(@PathVariable Long id) {
        try {
            OrderRes order = orderService.getOrderById(id);
            return ResponseEntity.ok(new BaseResponse<>(200, "Order found", order));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new BaseResponse<>(404, e.getMessage(), null));
        }
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<BaseResponse<OrderRes>> updateOrder(@PathVariable Long id, @RequestBody OrderReq orderReq) {
        try {
            OrderRes order = orderService.updateOrder(id, orderReq);
            return ResponseEntity.ok(new BaseResponse<>(200, "Order updated", order));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new BaseResponse<>(404, e.getMessage(), null));
        }
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<BaseResponse<Void>> deleteOrder(@PathVariable Long id) {
        try {
            orderService.deleteOrder(id);
            return ResponseEntity.ok(new BaseResponse<>(200, "Order deleted", null));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new BaseResponse<>(404, e.getMessage(), null));
        }
    }

    // Called by payment-service (the saga's other participant) to report the outcome
    // of a transaction that happened in its own database, not in order-service's.
    @PatchMapping("/by-order-id/{orderId}/status")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<BaseResponse<Void>> updateOrderStatusByOrderId(
            @PathVariable String orderId, @RequestBody OrderStatusUpdateReq statusUpdateReq) {
        try {
            orderService.updateOrderStatusByOrderId(orderId, statusUpdateReq);
            return ResponseEntity.ok(new BaseResponse<>(200, "Order status updated", null));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new BaseResponse<>(404, e.getMessage(), null));
        }
    }
}

