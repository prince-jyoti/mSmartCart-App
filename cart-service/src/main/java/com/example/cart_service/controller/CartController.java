package com.example.cart_service.controller;

import com.example.cart_service.dto.CartItemDTO;
import com.example.cart_service.dto.CartReq;
import com.example.cart_service.service.CartService;
import com.example.cart_service.utils.BaseResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;



@RestController
@RequestMapping("/carts")
public class CartController {
    @Autowired
    private CartService cartService;

//    private String getKeycloakUserIdOrThrow(Jwt jwt) {
//        String  = jwt.getSubject();
//        if ( == null || .isEmpty()) {
//            throw new IllegalArgumentException("Invalid token: missing subject");
//        }
//        return ;
//    }

    @GetMapping
    public ResponseEntity<BaseResponse<CartReq>> getCart() {
        try {
            CartReq cart = cartService.getCartByUser();
            return ResponseEntity.ok(new BaseResponse<>(200, "Cart fetched", cart));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new BaseResponse<>(500, "Error fetching cart", null));
        }
    }

    @PostMapping("/add")
    public ResponseEntity<BaseResponse<CartReq>> addItem(@RequestBody CartItemDTO itemDTO) {
        try {
            CartReq cart = cartService.addItemToCart(itemDTO);
            return ResponseEntity.ok(new BaseResponse<>(200, "Item added to cart", cart));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new BaseResponse<>(500, "Error adding item", null));
        }
    }

    @PutMapping("/update")
    public ResponseEntity<BaseResponse<CartReq>> updateItem(@RequestBody CartItemDTO itemDTO) {
        try {
            CartReq cart = cartService.updateCartItem(itemDTO);
            return ResponseEntity.ok(new BaseResponse<>(200, "Cart item updated", cart));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new BaseResponse<>(500, "Error updating item", null));
        }
    }

    @DeleteMapping("/remove/{productId}")
    public ResponseEntity<BaseResponse<CartReq>> removeItem(@PathVariable Long productId) {
        try {
            CartReq cart = cartService.removeItemFromCart( productId);
            return ResponseEntity.ok(new BaseResponse<>(200, "Item removed from cart", cart));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new BaseResponse<>(500, "Error removing item", null));
        }
    }

    @DeleteMapping("/clear")
    public ResponseEntity<BaseResponse<Void>> clearCart() {
        try {
            cartService.clearCart();
            return ResponseEntity.ok(new BaseResponse<>(200, "Cart cleared", null));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new BaseResponse<>(500, "Error clearing cart", null));
        }
    }
}