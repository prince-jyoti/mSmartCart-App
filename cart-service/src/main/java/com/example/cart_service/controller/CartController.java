package com.example.cart_service.controller;

import jakarta.validation.Valid;
import com.example.cart_service.dto.CartItemDTO;
import com.example.cart_service.dto.CartReq;
import com.example.cart_service.service.CartService;
import com.example.cart_service.utils.BaseResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/carts")
public class CartController {
    @Autowired
    private CartService cartService;

    @GetMapping
    public ResponseEntity<BaseResponse<CartReq>> getCart() {
        CartReq cart = cartService.getCartByUser();
        return ResponseEntity.ok(new BaseResponse<>(200, "Cart fetched", cart));
    }

    @PostMapping("/add")
    public ResponseEntity<BaseResponse<CartReq>> addItem(@Valid @RequestBody CartItemDTO itemDTO) {
        CartReq cart = cartService.addItemToCart(itemDTO);
        return ResponseEntity.ok(new BaseResponse<>(200, "Item added to cart", cart));
    }

    @PutMapping("/update")
    public ResponseEntity<BaseResponse<CartReq>> updateItem(@Valid @RequestBody CartItemDTO itemDTO) {
        CartReq cart = cartService.updateCartItem(itemDTO);
        return ResponseEntity.ok(new BaseResponse<>(200, "Cart item updated", cart));
    }

    @DeleteMapping("/remove/{productId}")
    public ResponseEntity<BaseResponse<CartReq>> removeItem(@PathVariable Long productId) {
        CartReq cart = cartService.removeItemFromCart( productId);
        return ResponseEntity.ok(new BaseResponse<>(200, "Item removed from cart", cart));
    }

    @DeleteMapping("/clear")
    public ResponseEntity<BaseResponse<Void>> clearCart() {
        cartService.clearCart();
        return ResponseEntity.ok(new BaseResponse<>(200, "Cart cleared", null));
    }
}