package com.example.cart_service.service;

import java.math.BigDecimal;
import com.example.cart_service.dto.OrderPaidEvent;
import com.example.cart_service.entity.ProcessedEvent;
import com.example.cart_service.repository.ProcessedEventRepo;
import com.example.cart_service.exception.ServiceUnavailableException;
import com.example.cart_service.exception.NotFoundException;
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

import java.time.LocalDateTime;
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
    @Autowired
    private ProcessedEventRepo processedEventRepo;

    // Failures propagate (404 or 503 via the fallback factories). These used to return null
    // or placeholder data, which could put every user in one cart or price items at 0.
    private UserDTO getUserDTO() {
        BaseResponse<UserDTO> userResponse = userServiceClient.getCurrentUser();
        if (userResponse == null || userResponse.getData() == null || userResponse.getData().getId() == null) {
            throw new ServiceUnavailableException("Could not resolve the current user");
        }
        return userResponse.getData();
    }

    private ProductDTO getProductDTO(Long productId) {
        BaseResponse<ProductDTO> productResponse = productServiceClient.getById(productId);
        if (productResponse == null || productResponse.getData() == null) {
            throw new NotFoundException("Product not found");
        }
        return productResponse.getData();
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
        Cart cart = cartRepo.findByUserId(userDTO.getId()).orElseThrow(() -> new NotFoundException("Cart not found"));
        ProductDTO product = getProductDTO(itemDTO.getProductId());
        CartItem item = cartItemRepo.findByCartAndProductId(cart, product.getId()).orElseThrow(() -> new NotFoundException("Cart item not found"));
        item.setQuantity(itemDTO.getQuantity());
        item.setPrice(product.getPrice());
        cartItemRepo.save(item);
        updateCartTotal(cart);
        return toCartReq(cart);
    }

    @Transactional
    public CartReq removeItemFromCart( Long productId) {
        UserDTO userDTO = getUserDTO();
        Cart cart = cartRepo.findByUserId(userDTO.getId()).orElseThrow(() -> new NotFoundException("Cart not found"));
        CartItem item = cartItemRepo.findByCartAndProductId(cart, productId).orElseThrow(() -> new NotFoundException("Cart item not found"));
        cartItemRepo.delete(item);
        updateCartTotal(cart);
        return toCartReq(cart);
    }

    @Transactional
    public void clearCart() {
        UserDTO userDTO = getUserDTO();
        Cart cart = cartRepo.findByUserId(userDTO.getId()).orElseThrow(() -> new NotFoundException("Cart not found"));
        List<CartItem> items = cartItemRepo.findByCart(cart);
        cartItemRepo.deleteAll(items);
        cart.setTotalPrice(BigDecimal.ZERO);
        cartRepo.save(cart);
    }

    // Consumes order.paid: takes the purchased quantities out of the buyer's cart, once per event.
    // Only those products are touched, so anything added after checkout stays. No user lookup:
    // a message listener has no caller token, and the event already says whose cart it is.
    @Transactional
    public void removePurchasedItems(OrderPaidEvent event) {
        if (processedEventRepo.existsById(event.getEventId())) {
            return;
        }
        processedEventRepo.save(new ProcessedEvent(event.getEventId(), LocalDateTime.now()));

        Optional<Cart> cart = cartRepo.findByUserId(event.getUserId());
        if (cart.isEmpty()) {
            return;
        }
        for (OrderPaidEvent.Item bought : event.getItems()) {
            cartItemRepo.findByCartAndProductId(cart.get(), bought.getProductId()).ifPresent(item -> {
                int left = item.getQuantity() - bought.getQuantity();
                if (left > 0) {
                    item.setQuantity(left);
                    cartItemRepo.save(item);
                } else {
                    cartItemRepo.delete(item);
                }
            });
        }
        updateCartTotal(cart.get());
    }

    private static BigDecimal lineTotal(CartItem item) {
        return item.getPrice().multiply(BigDecimal.valueOf(item.getQuantity()));
    }

    private Cart createCartForUser(UserDTO userDTO) {
        Cart cart = new Cart();
        cart.setUserId(userDTO.getId());
        cart.setTotalPrice(BigDecimal.ZERO);
        return cartRepo.save(cart);
    }

    private void updateCartTotal(Cart cart) {
        List<CartItem> items = cartItemRepo.findByCart(cart);
        BigDecimal total = items.stream().map(CartService::lineTotal).reduce(BigDecimal.ZERO, BigDecimal::add);
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
        dto.setTotal(lineTotal(item));
        return dto;
    }
}