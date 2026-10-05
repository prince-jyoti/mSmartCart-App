package com.example.payment_service.service;

import com.example.payment_service.configuration.RabbitConfig;
import com.example.payment_service.dto.PaymentEvent;
import com.example.payment_service.entity.OutboxEvent;
import com.example.payment_service.repository.OutboxRepo;
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

    // Joins the caller's transaction, so the event commits or rolls back with the payment.
    @Transactional
    public void recordPaid(String orderId, Long paymentId) {
        save(RabbitConfig.ROUTING_COMPLETED, new PaymentEvent(newId(), orderId, paymentId, "PAID", LocalDateTime.now()));
    }

    // The payment transaction is rolled back when a payment fails, so a failure event has to
    // be committed in its own transaction or it would vanish along with it.
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordFailed(String orderId) {
        save(RabbitConfig.ROUTING_FAILED, new PaymentEvent(newId(), orderId, null, "PAYMENT_FAILED", LocalDateTime.now()));
    }

    private String newId() {
        return UUID.randomUUID().toString();
    }

    private void save(String routingKey, PaymentEvent event) {
        OutboxEvent row = new OutboxEvent();
        row.setEventId(event.getEventId());
        row.setRoutingKey(routingKey);
        row.setCreatedAt(LocalDateTime.now());
        try {
            row.setPayload(MAPPER.writeValueAsString(event));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not serialize payment event", e);
        }
        outboxRepo.save(row);
    }
}
