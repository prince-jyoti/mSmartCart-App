package com.example.order_service.service;

import java.math.BigDecimal;
import com.example.order_service.client.PaymentServiceClient;
import com.example.order_service.client.ProductServiceClient;
import com.example.order_service.client.UserServiceClient;
import com.example.order_service.dto.OrderItemDTO;
import com.example.order_service.dto.OrderReq;
import com.example.order_service.dto.OrderRes;
import com.example.order_service.dto.PaymentRes;
import com.example.order_service.dto.ProductDTO;
import com.example.order_service.dto.StockReservationReq;
import com.example.order_service.dto.UserDTO;
import com.example.order_service.entity.Order;
import com.example.order_service.entity.OrderItem;
import com.example.order_service.exception.NotFoundException;
import com.example.order_service.exception.ServiceUnavailableException;
import com.example.order_service.repository.OrderRepo;
import com.example.order_service.utils.BaseResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.modelmapper.ModelMapper;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class OrderServiceTest {
    private static final long BUYER = 5L;
    private static final long STRANGER = 7L;

    private final OrderRepo orderRepo = mock(OrderRepo.class);
    private final UserServiceClient userClient = mock(UserServiceClient.class);
    private final ProductServiceClient productClient = mock(ProductServiceClient.class);
    private final PaymentServiceClient paymentClient = mock(PaymentServiceClient.class);
    private final ExecutorService executor = Executors.newFixedThreadPool(2);
    private final OutboxService outboxService = mock(OutboxService.class);
    private final OrderService orderService = new OrderService();

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(orderService, "orderRepo", orderRepo);
        ReflectionTestUtils.setField(orderService, "modelMapper", new ModelMapper());
        ReflectionTestUtils.setField(orderService, "userServiceClient", userClient);
        ReflectionTestUtils.setField(orderService, "productServiceClient", productClient);
        ReflectionTestUtils.setField(orderService, "paymentServiceClient", paymentClient);
        ReflectionTestUtils.setField(orderService, "orderEnrichmentExecutor", executor);
        ReflectionTestUtils.setField(orderService, "outboxService", outboxService);
        lenient().when(orderRepo.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(userClient.getUserById(anyLong())).thenAnswer(inv -> ok(user(inv.getArgument(0))));
    }

    @AfterEach
    void tearDown() {
        executor.shutdownNow();
    }

    // ---- createOrder: the server decides prices, total and status ------------------------

    @Test
    void pricesTotalAndStatusComeFromTheServerNotTheClient() {
        callerIs(BUYER);
        product(1L, 249.5);
        product(2L, 1200.0);
        OrderReq req = new OrderReq();
        req.setStatus("PAID");     // ignored
        req.setTotalAmount(BigDecimal.ONE);     // ignored
        req.setItems(List.of(item(1L, 2, 1.0), item(2L, 1, 0.0)));

        OrderRes res = orderService.createOrder(req);

        Order saved = savedOrder();
        assertThat(saved.getStatus()).isEqualTo("PENDING");
        assertThat(saved.getTotalAmount()).isEqualByComparingTo("1699.00");
        assertThat(saved.getItems()).extracting(OrderItem::getPrice).usingElementComparator(BigDecimal::compareTo)
                .containsExactly(new BigDecimal("249.50"), new BigDecimal("1200.00"));
        assertThat(saved.getUserId()).isEqualTo(BUYER);
        assertThat(saved.getOrderId()).startsWith("ORD-");
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(res.getUser().getId()).isEqualTo(BUYER);
    }

    @Test
    void totalsAreExactEvenWhereFloatingPointIsNot() {
        // In double arithmetic 3 x 0.10 is 0.30000000000000004.
        callerIs(BUYER);
        product(1L, 0.10);
        OrderReq req = new OrderReq();
        req.setItems(List.of(item(1L, 3, 0)));

        orderService.createOrder(req);

        assertThat(savedOrder().getTotalAmount()).isEqualByComparingTo("0.30");
    }

    @Test
    void unknownProductIsNotFoundAndNothingIsSaved() {
        callerIs(BUYER);
        when(productClient.getById(99L)).thenThrow(new NotFoundException("Product not found"));
        OrderReq req = new OrderReq();
        req.setItems(List.of(item(99L, 1, 0)));

        assertThatThrownBy(() -> orderService.createOrder(req)).isInstanceOf(NotFoundException.class);
        verify(orderRepo, never()).save(any());
    }

    @Test
    void orderIsRefusedWhenTheCallerCannotBeIdentified() {
        // The old fallback returned a user without an id, and orders were saved with no owner.
        when(userClient.getCurrentUser()).thenReturn(ok(new UserDTO()));
        OrderReq req = new OrderReq();
        req.setItems(List.of(item(1L, 1, 0)));

        assertThatThrownBy(() -> orderService.createOrder(req)).isInstanceOf(ServiceUnavailableException.class);
        verify(orderRepo, never()).save(any());
    }

    @Test
    void stockIsReservedForEveryLineBeforeTheOrderIsSaved() {
        callerIs(BUYER);
        product(1L, 249.5);
        product(2L, 1200.0);
        OrderReq req = new OrderReq();
        req.setItems(List.of(item(1L, 2, 0), item(2L, 1, 0)));

        orderService.createOrder(req);

        ArgumentCaptor<StockReservationReq> reserved = ArgumentCaptor.forClass(StockReservationReq.class);
        org.mockito.InOrder inOrder = org.mockito.Mockito.inOrder(productClient, orderRepo);
        inOrder.verify(productClient).reserve(reserved.capture());
        inOrder.verify(orderRepo).save(any(Order.class));
        assertThat(reserved.getValue().getOrderId()).isEqualTo(savedOrder().getOrderId());
        assertThat(reserved.getValue().getItems())
                .extracting(StockReservationReq.Item::getProductId, StockReservationReq.Item::getQuantity)
                .containsExactly(org.assertj.core.groups.Tuple.tuple(1L, 2), org.assertj.core.groups.Tuple.tuple(2L, 1));
    }

    @Test
    void notEnoughStockKeepsProductServicesMessageAndSavesNothing() {
        callerIs(BUYER);
        product(1L, 249.5);
        when(productClient.reserve(any())).thenThrow(new IllegalStateException("Not enough stock for Product 1: 2 left"));
        OrderReq req = new OrderReq();
        req.setItems(List.of(item(1L, 3, 0)));

        assertThatThrownBy(() -> orderService.createOrder(req))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Not enough stock for Product 1: 2 left");
        verify(orderRepo, never()).save(any());
    }

    // ---- remote calls per request (no N+1) --------------------------------------------------

    @Test
    void listingMyOrdersMakesOneRemoteCallHoweverManyOrdersThereAre() {
        callerIs(BUYER);
        Order paid = snapshotted(order(1L, BUYER));
        paid.setStatus("PAID");
        paid.setPaymentId(10L);
        when(orderRepo.findByUserId(BUYER)).thenReturn(List.of(snapshotted(order(2L, BUYER)), snapshotted(order(3L, BUYER)), paid));

        List<OrderRes> mine = orderService.getAllOrders("USER");

        assertThat(mine).hasSize(3).allSatisfy(o -> assertThat(o.getUser().getId()).isEqualTo(BUYER));
        assertThat(mine.get(0).getItems().get(0).getTitle()).isEqualTo("Saved title");
        verify(userClient, org.mockito.Mockito.times(1)).getCurrentUser();
        verify(userClient, never()).getUserById(anyLong());
        verifyNoInteractions(productClient, paymentClient); // lists leave payment details out
    }

    @Test
    void adminListLooksEachOwnerUpOnce() {
        when(orderRepo.findAll()).thenReturn(List.of(snapshotted(order(1L, 5L)), snapshotted(order(2L, 5L)), snapshotted(order(3L, 7L))));

        List<OrderRes> all = orderService.getAllOrders("ADMIN");

        assertThat(all).extracting(o -> o.getUser().getId()).containsExactly(5L, 5L, 7L);
        verify(userClient, org.mockito.Mockito.times(1)).getUserById(5L);
        verify(userClient, org.mockito.Mockito.times(1)).getUserById(7L);
        verifyNoInteractions(productClient);
    }

    @Test
    void viewingOneOfMyOrdersUsesASingleUserLookupAndIncludesThePayment() {
        callerIs(BUYER);
        Order order = snapshotted(order(1L, BUYER));
        order.setPaymentId(10L);
        stored(order);
        PaymentRes payment = new PaymentRes();
        payment.setPaymentId("pay_1");
        when(paymentClient.getPaymentById("10")).thenReturn(ok(payment));

        OrderRes res = orderService.getOrderById(1L, "USER");

        assertThat(res.getPayment().getPaymentId()).isEqualTo("pay_1");
        verify(userClient, org.mockito.Mockito.times(1)).getCurrentUser();
        verify(userClient, never()).getUserById(anyLong());
        verifyNoInteractions(productClient);
    }

    @Test
    void newOrdersStoreWhatWasBoughtSoReadingThemNeedsNoProductLookups() {
        callerIs(BUYER);
        product(1L, 249.5);
        OrderReq req = new OrderReq();
        req.setItems(List.of(item(1L, 1, 0)));

        OrderRes res = orderService.createOrder(req);

        assertThat(savedOrder().getItems().get(0).getTitle()).isEqualTo("Product 1");
        assertThat(res.getUser().getId()).isEqualTo(BUYER);
        verify(userClient, never()).getUserById(anyLong()); // the caller is the owner
    }

    @Test
    void anOlderItemIsLookedUpOnceAndThenRemembered() {
        callerIs(BUYER);
        product(1L, 249.5);
        Order old = order(1L, BUYER); // item without a saved title (placed before V7)
        stored(old);

        orderService.getOrderById(1L, "USER");
        orderService.getOrderById(1L, "USER");

        assertThat(old.getItems().get(0).getTitle()).isEqualTo("Product 1");
        verify(productClient, org.mockito.Mockito.times(1)).getById(1L);
        verify(orderRepo, org.mockito.Mockito.times(1)).save(old);
    }

    @Test
    void aDeletedProductDoesNotMakeAnOldOrderUnreadable() {
        callerIs(BUYER);
        when(productClient.getById(1L)).thenThrow(new NotFoundException("Product not found"));
        stored(order(1L, BUYER));

        OrderRes res = orderService.getOrderById(1L, "USER");

        assertThat(res.getItems().get(0).getTitle()).isEqualTo("Product no longer available");
    }

    // ---- expiry -------------------------------------------------------------------------------

    @Test
    void expiringAnUnpaidOrderAnnouncesItSoStockIsReturned() {
        Order order = order(1L, BUYER);
        when(orderRepo.markExpired(1L)).thenReturn(1);
        when(orderRepo.findById(1L)).thenReturn(Optional.of(order));

        assertThat(orderService.expireOrder(1L)).isTrue();

        verify(outboxService).recordOrderExpired(order);
    }

    @Test
    void orderThatWasPaidOrExpiredMeanwhileIsLeftAlone() {
        when(orderRepo.markExpired(1L)).thenReturn(0); // the conditional update matched nothing

        assertThat(orderService.expireOrder(1L)).isFalse();

        verifyNoInteractions(outboxService);
    }

    @Test
    void paymentThatCompletesAfterExpiryStillMarksTheOrderPaid() {
        Order order = order(1L, BUYER);
        order.setStatus("EXPIRED");
        when(orderRepo.findByOrderId("ORD-1")).thenReturn(Optional.of(order));

        orderService.applyPaymentOutcome("ORD-1", "PAID", 10L);

        assertThat(order.getStatus()).isEqualTo("PAID");
        verify(outboxService).recordOrderPaid(order); // product-service takes the released stock again
    }

    @Test
    void failedPaymentDoesNotReviveAnExpiredOrder() {
        Order order = order(1L, BUYER);
        order.setStatus("EXPIRED");
        when(orderRepo.findByOrderId("ORD-1")).thenReturn(Optional.of(order));

        orderService.applyPaymentOutcome("ORD-1", "PAYMENT_FAILED", null);

        assertThat(order.getStatus()).isEqualTo("EXPIRED");
        verify(orderRepo, never()).save(any());
    }

    // ---- reading orders: owner or admin only -------------------------------------------------

    @Test
    void ownerCanReadTheirOrderWithTheirDetails() {
        callerIs(BUYER);
        product(1L, 249.5);
        stored(order(1L, BUYER));

        OrderRes res = orderService.getOrderById(1L, "USER");

        assertThat(res.getUser().getId()).isEqualTo(BUYER);
        assertThat(res.getItems()).singleElement().satisfies(i -> assertThat(i.getTitle()).isEqualTo("Product 1"));
    }

    @Test
    void unpaidOrderTellsTheClientHowLongItHasLeftAndAPaidOneDoesNot() {
        callerIs(BUYER);
        product(1L, 249.5);
        Order unpaid = order(1L, BUYER);
        unpaid.setCreatedAt(java.time.Instant.now().minus(java.time.Duration.ofMinutes(10)));
        Order paid = order(2L, BUYER);
        paid.setStatus("PAID");
        paid.setCreatedAt(java.time.Instant.now().minus(java.time.Duration.ofMinutes(10)));
        stored(unpaid);
        stored(paid);

        assertThat(orderService.getOrderById(1L, "USER").getExpiresInSeconds()).isBetween(19 * 60L, 20 * 60L); // 30 min - 10
        assertThat(orderService.getOrderById(2L, "USER").getExpiresInSeconds()).isNull();
    }

    @Test
    void otherUsersGetNotFoundForSomeoneElsesOrder() {
        callerIs(STRANGER);
        stored(order(1L, BUYER));

        assertThatThrownBy(() -> orderService.getOrderById(1L, "USER"))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void orderWithoutAnOwnerIsNotFoundInsteadOfCrashing() {
        callerIs(BUYER);
        stored(order(1L, null)); // saved before the user lookup was fixed

        assertThatThrownBy(() -> orderService.getOrderById(1L, "USER"))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void adminCanReadAnyOrder() {
        product(1L, 249.5);
        stored(order(1L, BUYER));

        assertThat(orderService.getOrderById(1L, "ADMIN").getUser().getId()).isEqualTo(BUYER);
        verify(userClient, never()).getCurrentUser();
    }

    @Test
    void adminListStillWorksWhenAnOrderHasNoOwner() {
        product(1L, 249.5);
        when(orderRepo.findAll()).thenReturn(List.of(order(1L, BUYER), order(2L, null)));

        List<OrderRes> all = orderService.getAllOrders("ADMIN");

        assertThat(all).hasSize(2);
        assertThat(all.get(0).getUser().getId()).isEqualTo(BUYER);
        assertThat(all.get(1).getUser()).isNull();
        verify(userClient, never()).getUserById(null);
    }

    @Test
    void failedLookupDuringEnrichmentKeepsItsMeaningInsteadOfBecomingA500() {
        callerIs(BUYER);
        stored(order(1L, BUYER));
        when(productClient.getById(1L)).thenThrow(new ServiceUnavailableException("Product service unavailable"));

        // Used to surface as a CompletionException, which the handler turned into a 500.
        assertThatThrownBy(() -> orderService.getOrderById(1L, "USER"))
                .isInstanceOf(ServiceUnavailableException.class)
                .hasMessage("Product service unavailable");
    }

    // ---- applyPaymentOutcome: idempotent, PAID is final ------------------------------------

    @Test
    void paidEventMarksTheOrderPaidAndLinksThePayment() {
        Order order = order(1L, BUYER);
        when(orderRepo.findByOrderId("ORD-1")).thenReturn(Optional.of(order));

        orderService.applyPaymentOutcome("ORD-1", "PAID", 10L);

        assertThat(order.getStatus()).isEqualTo("PAID");
        assertThat(order.getPaymentId()).isEqualTo(10L);
        verify(outboxService).recordOrderPaid(order); // next saga step: stock and cart
    }

    @Test
    void failedPaymentDoesNotAnnounceAPaidOrder() {
        when(orderRepo.findByOrderId("ORD-1")).thenReturn(Optional.of(order(1L, BUYER)));

        orderService.applyPaymentOutcome("ORD-1", "PAYMENT_FAILED", null);

        verifyNoInteractions(outboxService);
    }

    @Test
    void lateFailureCannotUndoAPaidOrder() {
        Order order = order(1L, BUYER);
        order.setStatus("PAID");
        order.setPaymentId(10L);
        when(orderRepo.findByOrderId("ORD-1")).thenReturn(Optional.of(order));

        orderService.applyPaymentOutcome("ORD-1", "PAYMENT_FAILED", null);

        assertThat(order.getStatus()).isEqualTo("PAID");
        assertThat(order.getPaymentId()).isEqualTo(10L);
        verify(orderRepo, never()).save(any());
    }

    @Test
    void duplicatePaidEventChangesNothing() {
        Order order = order(1L, BUYER);
        when(orderRepo.findByOrderId("ORD-1")).thenReturn(Optional.of(order));

        orderService.applyPaymentOutcome("ORD-1", "PAID", 10L);
        orderService.applyPaymentOutcome("ORD-1", "PAID", 10L);

        assertThat(order.getStatus()).isEqualTo("PAID");
        verify(orderRepo, org.mockito.Mockito.times(1)).save(order);
        verify(outboxService, org.mockito.Mockito.times(1)).recordOrderPaid(order); // stock is reduced once
    }

    @Test
    void failureThenSuccessEndsPaid() {
        Order order = order(1L, BUYER);
        when(orderRepo.findByOrderId("ORD-1")).thenReturn(Optional.of(order));

        orderService.applyPaymentOutcome("ORD-1", "PAYMENT_FAILED", null);
        assertThat(order.getStatus()).isEqualTo("PAYMENT_FAILED");
        orderService.applyPaymentOutcome("ORD-1", "PAID", 11L);

        assertThat(order.getStatus()).isEqualTo("PAID");
        assertThat(order.getPaymentId()).isEqualTo(11L);
    }

    @Test
    void unknownStatusIsRejected() {
        assertThatThrownBy(() -> orderService.applyPaymentOutcome("ORD-1", "REFUNDED", null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ---- helpers ---------------------------------------------------------------------------

    private void callerIs(long id) {
        when(userClient.getCurrentUser()).thenReturn(ok(user(id)));
    }

    private void product(long id, double price) {
        product(id, price, 100);
    }

    private void product(long id, double price, int stock) {
        ProductDTO p = new ProductDTO();
        p.setStock(stock);
        p.setId(id);
        p.setTitle("Product " + id);
        p.setPrice(BigDecimal.valueOf(price));
        lenient().when(productClient.getById(id)).thenReturn(ok(p));
    }

    private void stored(Order order) {
        when(orderRepo.findById(order.getId())).thenReturn(Optional.of(order));
    }

    private static Order order(long id, Long userId) {
        Order o = new Order();
        o.setId(id);
        o.setOrderId("ORD-" + id);
        o.setUserId(userId);
        o.setStatus("PENDING");
        o.setTotalAmount(new BigDecimal("249.50"));
        OrderItem item = new OrderItem();
        item.setOrder(o);
        item.setProductId(1L);
        item.setQuantity(1);
        item.setPrice(new BigDecimal("249.50"));
        o.setItems(new ArrayList<>(List.of(item)));
        return o;
    }

    private static Order snapshotted(Order o) {
        o.getItems().forEach(i -> i.setTitle("Saved title"));
        return o;
    }

    private static OrderItemDTO item(long productId, int quantity, double clientPrice) {
        OrderItemDTO i = new OrderItemDTO();
        i.setProductId(productId);
        i.setQuantity(quantity);
        i.setPrice(BigDecimal.valueOf(clientPrice));
        return i;
    }

    private static UserDTO user(Long id) {
        UserDTO u = new UserDTO();
        u.setId(id);
        u.setEmail("user" + id + "@test.local");
        return u;
    }

    private static <T> BaseResponse<T> ok(T data) {
        return new BaseResponse<>(200, "ok", data);
    }

    private Order savedOrder() {
        ArgumentCaptor<Order> saved = ArgumentCaptor.forClass(Order.class);
        verify(orderRepo).save(saved.capture());
        return saved.getValue();
    }
}
