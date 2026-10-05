package com.example.payment_service.service;

import com.example.payment_service.configuration.RabbitConfig;
import com.example.payment_service.entity.OutboxEvent;
import com.example.payment_service.repository.OutboxRepo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

// Relays outbox rows to RabbitMQ. A row is marked published only after the broker confirms
// it, so a crash or broker outage just means it is sent again on a later run. That makes
// delivery at-least-once, which is why the consumer must tolerate duplicates.
@Slf4j
@Component
public class OutboxPublisher {
    @Autowired
    private OutboxRepo outboxRepo;
    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Scheduled(fixedDelay = 2000)
    public void publishPending() {
        List<OutboxEvent> pending = outboxRepo.findTop50ByPublishedAtIsNullOrderByIdAsc();
        for (OutboxEvent event : pending) {
            try {
                Message message = MessageBuilder.withBody(event.getPayload().getBytes(java.nio.charset.StandardCharsets.UTF_8))
                        .setContentType(MessageProperties.CONTENT_TYPE_JSON)
                        .setMessageId(event.getEventId())
                        .setDeliveryMode(MessageDeliveryMode.PERSISTENT)
                        .build();
                rabbitTemplate.invoke(template -> {
                    template.send(RabbitConfig.EXCHANGE, event.getRoutingKey(), message);
                    template.waitForConfirmsOrDie(5000);
                    return null;
                });
                event.setPublishedAt(LocalDateTime.now());
                outboxRepo.save(event);
            } catch (Exception e) {
                // Broker down or unconfirmed: stop here to keep events in order and retry next tick.
                log.warn("Could not publish outbox event {}: {}", event.getEventId(), e.getMessage());
                return;
            }
        }
    }
}
