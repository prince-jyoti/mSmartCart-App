package com.example.order_service.service;

import java.time.Instant;
import java.math.BigDecimal;
import com.example.order_service.exception.ServiceUnavailableException;
import com.example.order_service.exception.NotFoundException;
import com.example.order_service.client.PaymentServiceClient;
import com.example.order_service.client.ProductServiceClient;
import com.example.order_service.client.UserServiceClient;
import com.example.order_service.dto.*;
import com.example.order_service.entity.Order;
import com.example.order_service.entity.OrderItem;
import com.example.order_service.repository.OrderRepo;
import com.example.order_service.utils.BaseResponse;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.Map;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Collection;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.stream.Collectors;


@Service
public class OrderService {
    @Autowired
    private OrderRepo orderRepo;
    @Autowired
    private ModelMapper modelMapper;
    @Autowired
    private UserServiceClient userServiceClient;
    @Autowired
    private ProductServiceClient productServiceClient;
    @Autowired
    private PaymentServiceClient paymentServiceClient;
    @Autowired
    private ExecutorService orderEnrichmentExecutor;
    @Value("${order.expiry.after:PT30M}")
    private Duration expireAfter = Duration.ofMinutes(30);
    @Autowired
    private OutboxService outboxService;

    private UserDTO getUserDTO() {
        BaseResponse<UserDTO> userResponse = userServiceClient.getCurrentUser();
        if (userResponse == null || userResponse.getData() == null || userResponse.getData().getId() == null) {
            throw new ServiceUnavailableException("Could not resolve the current user");
        }
        return userResponse.getData();
    }

    private UserDTO getUserDTOById(Long userId) {
        BaseResponse<UserDTO> userResponse = userServiceClient.getUserById(userId);
        if (userResponse == null || userResponse.getData() == null) {
            throw new NotFoundException("User not found");
        }
        return userResponse.getData();
    }

    private ProductDTO getProductDTO(String productId) {
        BaseResponse<ProductDTO> productResponse = productServiceClient.getById(Long.valueOf(productId));
        if (productResponse == null || productResponse.getData() == null) {
            throw new NotFoundException("Product not found: " + productId);
        }
        return productResponse.getData();
    }

    private PaymentRes getPaymentDTO(String paymentId){
        BaseResponse<PaymentRes> paymentResponse = paymentServiceClient.getPaymentById(paymentId);
        if (paymentResponse == null || paymentResponse.getData() == null) {
            throw new NotFoundException("Payment not found");
        }
        return paymentResponse.getData();
    }

    // Status, prices and total are decided here, never taken from the client: the
    // request only contributes which products and how many of each.
    @Transactional
    public OrderRes createOrder(OrderReq orderReq) {
        if (orderReq.getItems() == null || orderReq.getItems().isEmpty()) {
            throw new IllegalArgumentException("Order must contain at least one item");
        }
        UserDTO userDTO = getUserDTO();
        Order order = new Order();
        order.setOrderId("ORD-" + UUID.randomUUID());
        order.setUserId(userDTO.getId());
        order.setStatus("PENDING");
        order.setCreatedAt(Instant.now());

        BigDecimal total = BigDecimal.ZERO;
        List<OrderItem> items = new ArrayList<>();
        for (OrderItemDTO itemDTO : orderReq.getItems()) {
            if (itemDTO.getQuantity() <= 0) {
                throw new IllegalArgumentException("Quantity must be positive");
            }
            ProductDTO product = getProductDTO(String.valueOf(itemDTO.getProductId()));
            OrderItem item = new OrderItem();
            item.setOrder(order);
            item.setProductId(product.getId());
            item.setQuantity(itemDTO.getQuantity());
            item.setPrice(product.getPrice());
            item.setTitle(product.getTitle());
            item.setImage(product.getImage());
            items.add(item);
            total = total.add(product.getPrice().multiply(BigDecimal.valueOf(itemDTO.getQuantity())));
        }
        order.setItems(items);
        order.setTotalAmount(total);

        // Take the stock now (all lines or none; 409 "Not enough stock for ..." otherwise). It is
        // held until the order is paid or expires. If saving the order then fails, the
        // reservation is left behind with no order; rare enough to accept for now.
        productServiceClient.reserve(new StockReservationReq(order.getOrderId(), items.stream()
                .map(i -> new StockReservationReq.Item(i.getProductId(), i.getQuantity()))
                .toList()));
        order = orderRepo.save(order);
        return toOrderRes(order, userDTO, false);
    }

    // Lists leave out payment details (the frontend doesn't use them there), and each owner is
    // looked up once: for a user's own orders that's the caller, already known, so listing them
    // makes one remote call however many orders there are.
    public List<OrderRes> getAllOrders(String role) {
        List<Order> orders;
        Map<Long, UserDTO> owners;
        if ("ADMIN".equalsIgnoreCase(role)) {
            orders = orderRepo.findAll();
            owners = usersById(orders.stream().map(Order::getUserId).filter(Objects::nonNull).collect(Collectors.toSet()));
        } else {
            UserDTO me = getUserDTO();
            orders = orderRepo.findByUserId(me.getId());
            owners = Map.of(me.getId(), me);
        }
        return orders.stream().map(o -> toOrderRes(o, owners.get(o.getUserId()), false)).collect(Collectors.toList());
    }

    public OrderRes getOrderById(Long id, String role) {
        Order order = orderRepo.findById(id).orElseThrow(() -> new NotFoundException("Order not found"));
        return toOrderRes(order, ownerVisibleTo(order, role), true);
    }

    // Lets payment-service look an order up by its public ORD-... id, using the
    // caller's own token, so it can confirm the caller owns the order and check the amount.
    public OrderRes getOrderByOrderId(String orderId, String role) {
        Order order = orderRepo.findByOrderId(orderId)
                .orElseThrow(() -> new NotFoundException("Order not found: " + orderId));
        return toOrderRes(order, ownerVisibleTo(order, role), true);
    }

    // The order's owner, if the caller may see the order (else 404). For a user that's the caller
    // themself, so the ownership check and the owner's details come from one lookup.
    private UserDTO ownerVisibleTo(Order order, String role) {
        if ("ADMIN".equalsIgnoreCase(role)) {
            return order.getUserId() != null ? getUserDTOById(order.getUserId()) : null;
        }
        UserDTO me = getUserDTO();
        // Null-safe: orders saved before the user lookup was fixed have no owner.
        if (!Objects.equals(order.getUserId(), me.getId())) {
            throw new NotFoundException("Order not found");
        }
        return me;
    }

    // One lookup per distinct user, in parallel.
    private Map<Long, UserDTO> usersById(Set<Long> ids) {
        Map<Long, CompletableFuture<UserDTO>> lookups = new HashMap<>();
        ids.forEach(id -> lookups.put(id, CompletableFuture.supplyAsync(() -> getUserDTOById(id), orderEnrichmentExecutor)));
        joinAll(lookups.values());
        Map<Long, UserDTO> users = new HashMap<>();
        lookups.forEach((id, f) -> users.put(id, f.join()));
        return users;
    }



    // Saga completion/compensation step, driven by PaymentEvents from payment-service. Safe to
    // apply repeatedly: PAID is final, and re-applying the same status changes nothing.
    @Transactional
    public void applyPaymentOutcome(String orderId, String status, Long paymentId) {
        if (!"PAID".equals(status) && !"PAYMENT_FAILED".equals(status)) {
            throw new IllegalArgumentException("Unsupported status: " + status);
        }
        Order order = orderRepo.findByOrderId(orderId)
                .orElseThrow(() -> new NotFoundException("Order not found: " + orderId));
        if ("PAID".equals(order.getStatus())) {
            return; // already settled; a late or repeated message must not change it
        }
        if ("EXPIRED".equals(order.getStatus()) && "PAYMENT_FAILED".equals(status)) {
            return; // a failed attempt doesn't bring an expired order back
        }
        // PAID is applied even to an EXPIRED order: the customer has been charged. Its stock
        // was released at expiry; product-service takes it again when it sees order.paid.
        order.setStatus(status);
        if (paymentId != null) {
            order.setPaymentId(paymentId);
        }
        orderRepo.save(order);
        if ("PAID".equals(status)) {
            // Next saga step, in this same transaction: stock and cart react to order.paid.
            // Only reached on the transition to PAID, so a repeated payment event can't send it twice.
            outboxService.recordOrderPaid(order);
        }
    }

    // Called by OrderExpiryJob for an unpaid order past its deadline. The conditional update
    // makes this safe against a payment landing at the same moment and against other
    // instances running the job: only the call that actually expires the order announces it.
    @Transactional
    public boolean expireOrder(Long id) {
        if (orderRepo.markExpired(id) == 0) {
            return false;
        }
        Order order = orderRepo.findById(id).orElseThrow(() -> new NotFoundException("Order not found"));
        outboxService.recordOrderExpired(order); // product-service returns the reserved stock
        return true;
    }

    private OrderRes toOrderRes(Order order, UserDTO owner, boolean withPayment) {
        // Items carry their own title/image (saved at order time). Only items from before that
        // (V7) need product-service, once: the result is saved onto the item.
        List<OrderItem> missing = order.getItems().stream().filter(i -> i.getTitle() == null).toList();
        // Identity map: OrderItem/Order are Lombok @Data, so their hashCode() would recurse into each other.
        Map<OrderItem, CompletableFuture<ProductDTO>> lookups = new IdentityHashMap<>();
        missing.forEach(i -> lookups.put(i, CompletableFuture.supplyAsync(() -> productOrNull(i.getProductId()), orderEnrichmentExecutor)));
        CompletableFuture<PaymentRes> paymentFuture = withPayment && order.getPaymentId() != null
                ? CompletableFuture.supplyAsync(() -> getPaymentDTO(String.valueOf(order.getPaymentId())), orderEnrichmentExecutor)
                : CompletableFuture.completedFuture(null);
        List<CompletableFuture<?>> all = new ArrayList<>(lookups.values());
        all.add(paymentFuture);
        joinAll(all);

        if (!lookups.isEmpty()) {
            lookups.forEach((item, f) -> {
                ProductDTO p = f.join();
                item.setTitle(p != null ? p.getTitle() : "Product no longer available");
                item.setImage(p != null ? p.getImage() : null);
            });
            orderRepo.save(order); // backfill, so this order needs no lookups next time
        }

        OrderRes res = new OrderRes();
        res.setId(order.getId());
        res.setOrderId(order.getOrderId());
        if (owner != null) {
            res.setUser(modelMapper.map(owner, UserDTO.class));
        }
        PaymentRes paymentRes = paymentFuture.join();
        if (paymentRes != null) {
            res.setPayment(modelMapper.map(paymentRes, PaymentRes.class));
        }
        res.setItems(order.getItems().stream().map(OrderService::toOrderItemDTO).collect(Collectors.toList()));
        res.setTotalAmount(order.getTotalAmount());
        res.setStatus(order.getStatus());
        res.setCreatedAt(order.getCreatedAt());
        if (("PENDING".equals(order.getStatus()) || "PAYMENT_FAILED".equals(order.getStatus())) && order.getCreatedAt() != null) {
            long left = Duration.between(Instant.now(), order.getCreatedAt().plus(expireAfter)).toSeconds();
            res.setExpiresInSeconds(Math.max(0, left)); // 0: due, the expiry job will pick it up within a minute
        }
        return res;
    }

    private static OrderItemDTO toOrderItemDTO(OrderItem item) {
        OrderItemDTO dto = new OrderItemDTO();
        dto.setId(item.getId());
        dto.setProductId(item.getProductId());
        dto.setTitle(item.getTitle());
        dto.setImage(item.getImage());
        dto.setQuantity(item.getQuantity());
        dto.setPrice(item.getPrice());
        return dto;
    }

    // A deleted product shouldn't make a whole old order unreadable; other failures (503) still propagate.
    private ProductDTO productOrNull(Long productId) {
        try {
            return getProductDTO(String.valueOf(productId));
        } catch (NotFoundException e) {
            return null;
        }
    }

    private static void joinAll(Collection<? extends CompletableFuture<?>> futures) {
        try {
            CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new)).join();
        } catch (CompletionException e) {
            // join() wraps failures; rethrow the original so a 404/503 isn't reported as a 500.
            if (e.getCause() instanceof RuntimeException cause) {
                throw cause;
            }
            throw e;
        }
    }
}
