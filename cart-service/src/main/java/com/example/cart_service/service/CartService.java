package com.example.cart_service.service;

import com.example.cart_service.client.ProductServiceClient;
import com.example.cart_service.client.UserServiceClient;
import com.example.cart_service.dto.CartItemDTO;
import com.example.cart_service.dto.CartReq;
import com.example.cart_service.dto.ProductDTO;
import com.example.cart_service.dto.UserDTO;
import com.example.cart_service.entity.Cart;
import com.example.cart_service.entity.CartItem;
import com.example.cart_service.repository.CartItemRepo;
import com.example.cart_service.repository.CartRepo;
import com.example.cart_service.utils.BaseResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class CartService {
    @Autowired
    private CartRepo cartRepo;
    @Autowired
    private CartItemRepo cartItemRepo;
    @Autowired
    private UserServiceClient userServiceClient;
    @Autowired
    private ProductServiceClient productServiceClient;

    private UserDTO getUserDTO() {
        try {
            BaseResponse<UserDTO> userResponse = userServiceClient.getCurrentUser();
            if (userResponse == null || userResponse.getData() == null) {
                throw new RuntimeException("User not found");
            }
            return userResponse.getData();
        } catch (Exception e) {
            return null;
        }
    }

    private ProductDTO getProductDTO(Long productId) {
        try {
            BaseResponse<ProductDTO> productResponse = productServiceClient.getById(productId);
            if (productResponse == null || productResponse.getData() == null) {
                throw new RuntimeException("Product not found");
            }
            return productResponse.getData();
        } catch (Exception e) {
            return null;
        }
    }

    public CartReq getCartByUser() {
        UserDTO userDTO = getUserDTO();
        Cart cart = cartRepo.findByUserId(userDTO.getId()).orElseGet(() -> createCartForUser(userDTO));
        return toCartReq(cart);
    }

    @Transactional
    public CartReq addItemToCart( CartItemDTO itemDTO) {

        UserDTO userDTO = getUserDTO();
        Cart cart = cartRepo.findByUserId(userDTO.getId()).orElseGet(() -> createCartForUser(userDTO));
        ProductDTO product = getProductDTO(itemDTO.getProductId());
        Optional<CartItem> existingItemOpt = cartItemRepo.findByCartAndProductId(cart, product.getId());
        CartItem item;
        if (existingItemOpt.isPresent()) {
            item = existingItemOpt.get();
            item.setQuantity(item.getQuantity() + itemDTO.getQuantity());
            item.setPrice(product.getPrice());
        } else {
            item = new CartItem();
            item.setCart(cart);
            item.setProductId(product.getId());
            item.setQuantity(itemDTO.getQuantity());
            item.setPrice(product.getPrice());
        }
        cartItemRepo.save(item);
        updateCartTotal(cart);
        return toCartReq(cart);
    }

    @Transactional
    public CartReq updateCartItem( CartItemDTO itemDTO) {
        UserDTO userDTO = getUserDTO();
        Cart cart = cartRepo.findByUserId(userDTO.getId()).orElseThrow(() -> new RuntimeException("Cart not found"));
        ProductDTO product = getProductDTO(itemDTO.getProductId());
        CartItem item = cartItemRepo.findByCartAndProductId(cart, product.getId()).orElseThrow(() -> new RuntimeException("Cart item not found"));
        item.setQuantity(itemDTO.getQuantity());
        item.setPrice(product.getPrice());
        cartItemRepo.save(item);
        updateCartTotal(cart);
        return toCartReq(cart);
    }

    @Transactional
    public CartReq removeItemFromCart( Long productId) {
        UserDTO userDTO = getUserDTO();
        Cart cart = cartRepo.findByUserId(userDTO.getId()).orElseThrow(() -> new RuntimeException("Cart not found"));
        CartItem item = cartItemRepo.findByCartAndProductId(cart, productId).orElseThrow(() -> new RuntimeException("Cart item not found"));
        cartItemRepo.delete(item);
        updateCartTotal(cart);
        return toCartReq(cart);
    }

    @Transactional
    public void clearCart() {
        UserDTO userDTO = getUserDTO();
        Cart cart = cartRepo.findByUserId(userDTO.getId()).orElseThrow(() -> new RuntimeException("Cart not found"));
        List<CartItem> items = cartItemRepo.findByCart(cart);
        cartItemRepo.deleteAll(items);
        cart.setTotalPrice(0);
        cartRepo.save(cart);
    }

    private Cart createCartForUser(UserDTO userDTO) {
        Cart cart = new Cart();
        cart.setUserId(userDTO.getId());
        cart.setTotalPrice(0);
        return cartRepo.save(cart);
    }

    private void updateCartTotal(Cart cart) {
        List<CartItem> items = cartItemRepo.findByCart(cart);
        double total = items.stream().mapToDouble(i -> i.getPrice() * i.getQuantity()).sum();
        cart.setTotalPrice(total);
        cartRepo.save(cart);
    }

    private CartReq toCartReq(Cart cart) {
        List<CartItemDTO> itemDTOs = cartItemRepo.findByCart(cart).stream().map(this::toCartItemDTO).collect(Collectors.toList());
        CartReq dto = new CartReq();
        dto.setId(cart.getId());
        dto.setUserId(cart.getUserId() != null ? cart.getUserId() : null);
        dto.setItems(itemDTOs);
        dto.setTotalPrice(cart.getTotalPrice());
        return dto;
    }

    private CartItemDTO toCartItemDTO(CartItem item) {
        ProductDTO product = getProductDTO(item.getProductId());
        CartItemDTO dto = new CartItemDTO();
        dto.setId(item.getId());
        dto.setCartId(item.getCart() != null ? item.getCart().getId() : null);
        dto.setProductId(item.getProductId() != null ? item.getProductId() : null);
        dto.setTitle(product.getTitle());
        dto.setDescription(product.getDescription());
        dto.setImage(product.getImage());
        dto.setBrand(product.getBrand());
        dto.setModel(product.getModel());
        dto.setColor(product.getColor());
        dto.setCategory(product.getCategory());
        dto.setDiscount(product.getDiscount());
        dto.setQuantity(item.getQuantity());
        dto.setPrice(item.getPrice());
        dto.setTotal(item.getPrice() * item.getQuantity());
        return dto;
    }
}