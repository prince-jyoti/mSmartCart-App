package com.example.cart_service.service;

import java.math.BigDecimal;
import com.example.cart_service.client.ProductServiceClient;
import com.example.cart_service.client.ProductServiceFallbackFactory;
import com.example.cart_service.client.UserServiceClient;
import com.example.cart_service.client.UserServiceFallbackFactory;
import com.example.cart_service.dto.CartItemDTO;
import com.example.cart_service.dto.CartReq;
import com.example.cart_service.dto.OrderPaidEvent;
import com.example.cart_service.entity.ProcessedEvent;
import com.example.cart_service.repository.ProcessedEventRepo;
import com.example.cart_service.dto.ProductDTO;
import com.example.cart_service.dto.UserDTO;
import com.example.cart_service.entity.Cart;
import com.example.cart_service.entity.CartItem;
import com.example.cart_service.exception.NotFoundException;
import com.example.cart_service.exception.ServiceUnavailableException;
import com.example.cart_service.repository.CartItemRepo;
import com.example.cart_service.repository.CartRepo;
import com.example.cart_service.utils.BaseResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class CartServiceTest {
    private final CartRepo cartRepo = mock(CartRepo.class);
    private final CartItemRepo cartItemRepo = mock(CartItemRepo.class);
    private final UserServiceClient userClient = mock(UserServiceClient.class);
    private final ProductServiceClient productClient = mock(ProductServiceClient.class);
    private final ProcessedEventRepo processedRepo = mock(ProcessedEventRepo.class);
    private final CartService cartService = new CartService();
    private Cart cart;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(cartService, "cartRepo", cartRepo);
        ReflectionTestUtils.setField(cartService, "cartItemRepo", cartItemRepo);
        ReflectionTestUtils.setField(cartService, "userServiceClient", userClient);
        ReflectionTestUtils.setField(cartService, "productServiceClient", productClient);
        ReflectionTestUtils.setField(cartService, "processedEventRepo", processedRepo);
        cart = new Cart();
        cart.setId(1L);
        cart.setUserId(5L);
        lenient().when(cartRepo.findByUserId(5L)).thenReturn(Optional.of(cart));
        lenient().when(cartRepo.save(any(Cart.class))).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(cartItemRepo.findByCartAndProductId(any(), any())).thenReturn(Optional.empty());
    }

    @Test
    void itemIsAddedAtTheProductsCurrentPrice() {
        callerIs(5L);
        product(1L, 249.5);
        CartItemDTO add = new CartItemDTO();
        add.setProductId(1L);
        add.setQuantity(2);
        add.setPrice(BigDecimal.ONE); // the client's price is ignored

        cartService.addItemToCart(add);

        ArgumentCaptor<CartItem> saved = ArgumentCaptor.forClass(CartItem.class);
        verify(cartItemRepo).save(saved.capture());
        assertThat(saved.getValue().getPrice()).isEqualByComparingTo("249.50");
        assertThat(saved.getValue().getQuantity()).isEqualTo(2);
    }

    @Test
    void userServiceOutageDoesNotFallBackToASharedCart() {
        // The old fallback returned a user with no id, so every caller got the cart where userId IS NULL.
        when(userClient.getCurrentUser()).thenAnswer(inv ->
                new UserServiceFallbackFactory().create(new RuntimeException("timeout")).getCurrentUser());

        assertThatThrownBy(cartService::getCartByUser).isInstanceOf(ServiceUnavailableException.class);
        verifyNoInteractions(cartRepo);
    }

    @Test
    void userWithoutAnIdIsRejected() {
        when(userClient.getCurrentUser()).thenReturn(new BaseResponse<>(200, "ok", new UserDTO()));

        assertThatThrownBy(cartService::getCartByUser).isInstanceOf(ServiceUnavailableException.class);
        verifyNoInteractions(cartRepo);
    }

    @Test
    void productServiceOutageDoesNotAddAFreeItem() {
        // The old fallback returned a product priced at 0, which was added to the cart.
        callerIs(5L);
        when(productClient.getById(1L)).thenAnswer(inv ->
                new ProductServiceFallbackFactory().create(new RuntimeException("connection refused")).getById(1L));
        CartItemDTO add = new CartItemDTO();
        add.setProductId(1L);

        assertThatThrownBy(() -> cartService.addItemToCart(add)).isInstanceOf(ServiceUnavailableException.class);
        verify(cartItemRepo, never()).save(any());
    }

    @Test
    void unknownProductIsNotFound() {
        callerIs(5L);
        when(productClient.getById(99L)).thenThrow(new NotFoundException("Product not found"));
        CartItemDTO add = new CartItemDTO();
        add.setProductId(99L);

        assertThatThrownBy(() -> cartService.addItemToCart(add)).isInstanceOf(NotFoundException.class);
        verify(cartItemRepo, never()).save(any());
    }

    @Test
    void removingAnItemThatIsNotInTheCartIsNotFound() {
        callerIs(5L);

        assertThatThrownBy(() -> cartService.removeItemFromCart(42L))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Cart item not found");
    }

    @Test
    void cartTotalIsRecomputedFromItems() {
        callerIs(5L);
        product(1L, 249.5);
        CartItem existing = new CartItem();
        existing.setCart(cart);
        existing.setProductId(1L);
        existing.setPrice(new BigDecimal("249.50"));
        existing.setQuantity(3);
        when(cartItemRepo.findByCart(cart)).thenReturn(List.of(existing));

        CartReq res = cartService.getCartByUser();

        assertThat(res.getItems()).singleElement().satisfies(i -> assertThat(i.getTotal()).isEqualByComparingTo("748.50"));
    }

    // ---- order.paid consumer ----------------------------------------------------------------

    @Test
    void paidOrderRemovesOnlyThePurchasedQuantities() {
        CartItem mug = line(1L, 3);      // bought 2 of 3: 1 stays
        CartItem lamp = line(2L, 1);     // bought 1 of 1: removed
        CartItem addedLater = line(3L, 1);
        when(cartItemRepo.findByCartAndProductId(cart, 1L)).thenReturn(Optional.of(mug));
        when(cartItemRepo.findByCartAndProductId(cart, 2L)).thenReturn(Optional.of(lamp));

        cartService.removePurchasedItems(paid("evt-1", new OrderPaidEvent.Item(1L, 2), new OrderPaidEvent.Item(2L, 1)));

        assertThat(mug.getQuantity()).isEqualTo(1);
        verify(cartItemRepo).save(mug);
        verify(cartItemRepo).delete(lamp);
        verify(cartItemRepo, never()).delete(addedLater);
        verify(cartRepo).save(cart); // total recomputed
        verifyNoInteractions(userClient, productClient); // a listener has no caller token
    }

    @Test
    void aRedeliveredEventDoesNotTouchTheCartAgain() {
        when(processedRepo.existsById("evt-1")).thenReturn(true);

        cartService.removePurchasedItems(paid("evt-1", new OrderPaidEvent.Item(1L, 1)));

        verifyNoInteractions(cartRepo, cartItemRepo);
    }

    @Test
    void buyerWithoutACartIsFine() {
        when(cartRepo.findByUserId(5L)).thenReturn(Optional.empty());

        cartService.removePurchasedItems(paid("evt-1", new OrderPaidEvent.Item(1L, 1)));

        verify(processedRepo).save(any(ProcessedEvent.class));
        verifyNoInteractions(cartItemRepo);
    }

    private OrderPaidEvent paid(String id, OrderPaidEvent.Item... items) {
        return new OrderPaidEvent(id, "ORD-1", 5L, List.of(items), null);
    }

    private CartItem line(long productId, int quantity) {
        CartItem i = new CartItem();
        i.setCart(cart);
        i.setProductId(productId);
        i.setQuantity(quantity);
        i.setPrice(new BigDecimal("100.00"));
        return i;
    }

    private void callerIs(long id) {
        UserDTO u = new UserDTO();
        u.setId(id);
        when(userClient.getCurrentUser()).thenReturn(new BaseResponse<>(200, "ok", u));
    }

    private void product(long id, double price) {
        ProductDTO p = new ProductDTO();
        p.setId(id);
        p.setTitle("Product " + id);
        p.setPrice(BigDecimal.valueOf(price));
        when(productClient.getById(id)).thenReturn(new BaseResponse<>(200, "ok", p));
    }
}
