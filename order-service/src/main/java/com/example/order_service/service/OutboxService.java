package com.example.order_service.service;

import com.example.order_service.configuration.RabbitConfig;
import com.example.order_service.dto.OrderEvent;
import com.example.order_service.entity.Order;
import com.example.order_service.entity.OutboxEvent;
import com.example.order_service.repository.OutboxRepo;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class OutboxService {
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    @Autowired
    private OutboxRepo outboxRepo;

    // Each must run inside the transaction that changes the order: both commit or neither does.
    @Transactional(propagation = Propagation.MANDATORY)
    public void recordOrderPaid(Order order) {
        record(order, RabbitConfig.ROUTING_ORDER_PAID);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void recordOrderExpired(Order order) {
        record(order, RabbitConfig.ROUTING_ORDER_EXPIRED);
    }

    private void record(Order order, String routingKey) {
        OrderEvent event = new OrderEvent(
                UUID.randomUUID().toString(),
                order.getOrderId(),
                order.getUserId(),
                order.getItems().stream()
                        .map(i -> new OrderEvent.Item(i.getProductId(), i.getQuantity()))
                        .toList(),
                LocalDateTime.now());

        OutboxEvent row = new OutboxEvent();
        row.setEventId(event.getEventId());
        row.setRoutingKey(routingKey);
        row.setCreatedAt(LocalDateTime.now());
        try {
            row.setPayload(MAPPER.writeValueAsString(event));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not serialize order event", e);
        }
        outboxRepo.save(row);
    }
}
