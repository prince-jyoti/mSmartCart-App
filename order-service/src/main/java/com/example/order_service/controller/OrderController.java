package com.example.order_service.controller;

import com.example.order_service.dto.OrderReq;
import com.example.order_service.dto.OrderRes;
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
import java.util.Map;


@RestController
@RequestMapping("/orders")
public class OrderController {
    @Autowired
    private OrderService orderService;

    private String getKeycloakUserRoleOrThrow(Jwt jwt) {
        Map<String, Object> realmAccess = jwt.getClaim("realm_access");
        if (realmAccess == null || !realmAccess.containsKey("roles")) {
            throw new IllegalArgumentException("Invalid token: missing roles");
        }
        List<String> roles = (List<String>) realmAccess.get("roles");
        if (roles == null || roles.isEmpty()) {
            throw new IllegalArgumentException("No roles found in token");
        }
        return roles.get(0); // returns the first role
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
            String role = getKeycloakUserRoleOrThrow(jwt);
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
}

