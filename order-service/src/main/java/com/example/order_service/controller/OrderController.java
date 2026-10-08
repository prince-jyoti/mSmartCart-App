package com.example.order_service.controller;

import jakarta.validation.Valid;
import com.example.order_service.dto.OrderReq;
import com.example.order_service.dto.OrderRes;
import com.example.order_service.service.OrderService;
import com.example.order_service.utils.BaseResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;


@RestController
@RequestMapping("/orders")
// Orders change state only through the checkout flow (payment events, expiry), never by direct
// edits: a hand-set status skips stock and cart updates, and deleting an order loses a paid
// order's record or leaves its stock reserved for good.
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
    public ResponseEntity<BaseResponse<OrderRes>> createOrder(@Valid @RequestBody OrderReq orderReq) {

        OrderRes order = orderService.createOrder(orderReq);
        return ResponseEntity.ok(new BaseResponse<>(200, "Order created", order));
    }

    @GetMapping
    public ResponseEntity<BaseResponse<List<OrderRes>>> getAllOrder(@AuthenticationPrincipal Jwt jwt) {
        String role = getUserRoleOrThrow(jwt);
        List<OrderRes> orders = orderService.getAllOrders( role);
        return ResponseEntity.ok(new BaseResponse<>(200, "Orders fetched", orders));
    }

    @GetMapping("/{id}")
    public ResponseEntity<BaseResponse<OrderRes>> getOrderById(@PathVariable Long id, @AuthenticationPrincipal Jwt jwt) {
        OrderRes order = orderService.getOrderById(id, getUserRoleOrThrow(jwt));
        return ResponseEntity.ok(new BaseResponse<>(200, "Order found", order));
    }

    @GetMapping("/by-order-id/{orderId}")
    public ResponseEntity<BaseResponse<OrderRes>> getOrderByOrderId(@PathVariable String orderId, @AuthenticationPrincipal Jwt jwt) {
        OrderRes order = orderService.getOrderByOrderId(orderId, getUserRoleOrThrow(jwt));
        return ResponseEntity.ok(new BaseResponse<>(200, "Order found", order));
    }

}
