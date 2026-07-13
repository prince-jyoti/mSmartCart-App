package com.example.order_service.service;

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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.stream.Collectors;
import java.util.stream.Stream;


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

    private UserDTO getUserDTO() {
        BaseResponse<UserDTO> userResponse = userServiceClient.getCurrentUser();
        if (userResponse == null || userResponse.getData() == null) {
            throw new RuntimeException("User not found");
        }
        return userResponse.getData();
    }

    private UserDTO getUserDTOById(Long userId) {
        BaseResponse<UserDTO> userResponse = userServiceClient.getUserById(userId);
        if (userResponse == null || userResponse.getData() == null) {
            throw new RuntimeException("User not found");
        }
        return userResponse.getData();
    }

    private ProductDTO getProductDTO(String productId) {
        BaseResponse<ProductDTO> productResponse = productServiceClient.getById(Long.valueOf(productId));
        if (productResponse == null || productResponse.getData() == null) {
            throw new RuntimeException("Product not found");
        }
        return productResponse.getData();
    }

    private PaymentRes getPaymentDTO(String paymentId){
        BaseResponse<PaymentRes> paymentResponse = paymentServiceClient.getPaymentById(paymentId);
        if (paymentResponse == null || paymentResponse.getData() == null) {
            throw new RuntimeException("Payment not found");
        }
        return paymentResponse.getData();
    }

    @Transactional
    public OrderRes createOrder(OrderReq orderReq) {
        UserDTO userDTO= getUserDTO();
        Order order = new Order();
        order.setOrderId("ORD-" + UUID.randomUUID());
        order.setUserId(userDTO.getId());
        order.setStatus(orderReq.getStatus());
        order.setTotalAmount(orderReq.getTotalAmount());
        order.setCreatedAt(orderReq.getCreatedAt());
        // Map items
        Order finalOrder = order;
        List<OrderItem> items = orderReq.getItems().stream().map(itemDTO -> {
            OrderItem item = new OrderItem();
            item.setOrder(finalOrder);
            item.setProductId(itemDTO.getProductId());
            item.setQuantity(itemDTO.getQuantity());
            item.setPrice(itemDTO.getPrice());
            return item;
        }).collect(Collectors.toList());
        order.setItems(items);
        order = orderRepo.save(order);
        return toOrderRes(order);
    }

    public List<OrderRes> getAllOrders(String role) {
        List<Order> orders;
        if ("ADMIN".equalsIgnoreCase(role)) {
            orders = orderRepo.findAll();
        } else {
            UserDTO userDTO = getUserDTO();
            orders = orderRepo.findByUserId(userDTO.getId());
        }
        return orders.stream().map(this::toOrderRes).collect(Collectors.toList());
    }

    public OrderRes getOrderById(Long id) {
        Order order = orderRepo.findById(id).orElseThrow(() -> new RuntimeException("Order not found"));
        return toOrderRes(order);
    }

    @Transactional
    public OrderRes updateOrder(Long id, OrderReq orderReq) {
        Order order = orderRepo.findById(id).orElseThrow(() -> new RuntimeException("Order not found"));
        order.setStatus(orderReq.getStatus());
        order.setTotalAmount(orderReq.getTotalAmount());
        // Optionally update items and payment if needed
        order = orderRepo.save(order);
        return toOrderRes(order);
    }

    @Transactional
    public void deleteOrder(Long id) {
        Order order = orderRepo.findById(id).orElseThrow(() -> new RuntimeException("Order not found"));
        orderRepo.delete(order);
    }

    // Saga compensation/completion step: payment-service calls this after it
    // decides the payment succeeded or failed, so the order's own status
    // reflects the outcome of a transaction that happened in another service's DB.
    @Transactional
    public void updateOrderStatusByOrderId(String orderId, OrderStatusUpdateReq statusUpdateReq) {
        Order order = orderRepo.findByOrderId(orderId)
                .orElseThrow(() -> new RuntimeException("Order not found: " + orderId));
        order.setStatus(statusUpdateReq.getStatus());
        if (statusUpdateReq.getPaymentId() != null) {
            order.setPaymentId(statusUpdateReq.getPaymentId());
        }
        orderRepo.save(order);
    }

    private OrderRes toOrderRes(Order order) {
        // None of these three calls depend on each other's result, so fire them
        // all off concurrently instead of blocking on them one at a time.
        CompletableFuture<UserDTO> userFuture =
                CompletableFuture.supplyAsync(() -> getUserDTOById(order.getUserId()), orderEnrichmentExecutor);

        CompletableFuture<PaymentRes> paymentFuture = order.getPaymentId() != null
                ? CompletableFuture.supplyAsync(() -> getPaymentDTO(String.valueOf(order.getPaymentId())), orderEnrichmentExecutor)
                : CompletableFuture.completedFuture(null);

        List<CompletableFuture<OrderItemDTO>> itemFutures = order.getItems().stream()
                .map(item -> CompletableFuture.supplyAsync(() -> toOrderItemDTO(item), orderEnrichmentExecutor))
                .collect(Collectors.toList());

        CompletableFuture.allOf(
                Stream.concat(Stream.of(userFuture, paymentFuture), itemFutures.stream())
                        .toArray(CompletableFuture[]::new)
        ).join();

        OrderRes res = new OrderRes();
        UserDTO userDTO = userFuture.join();
        PaymentRes paymentRes = paymentFuture.join();
        if (paymentRes != null) {
            res.setPayment(modelMapper.map(paymentRes, PaymentRes.class));
        }
        res.setId(order.getId());
        res.setOrderId(order.getOrderId());
        res.setUser(modelMapper.map(userDTO, UserDTO.class));
        res.setItems(itemFutures.stream().map(CompletableFuture::join).collect(Collectors.toList()));
        res.setTotalAmount(order.getTotalAmount());
        res.setStatus(order.getStatus());
        res.setCreatedAt(order.getCreatedAt());
        return res;
    }

    private OrderItemDTO toOrderItemDTO(OrderItem item) {
        OrderItemDTO dto = new OrderItemDTO();
        ProductDTO productDTO = getProductDTO(String.valueOf(item.getProductId()));
        dto.setId(item.getId());
        dto.setProductId(productDTO.getId() != null ? productDTO.getId() : null);
        dto.setTitle(productDTO.getTitle()!=null? productDTO.getTitle() : null);
        dto.setImage(productDTO.getImage()!=null? productDTO.getImage() : null);
        dto.setQuantity(item.getQuantity());
        dto.setPrice(item.getPrice());
        return dto;
    }
}
