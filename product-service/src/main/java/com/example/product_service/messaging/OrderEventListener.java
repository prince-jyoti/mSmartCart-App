package com.example.product_service.messaging;

import com.example.product_service.configuration.RabbitConfig;
import com.example.product_service.dto.OrderEvent;
import com.example.product_service.services.StockService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

// order.paid and order.expired arrive on the same queue; the routing key says which.
// Failures are retried (see application.yml), then dead-lettered to product.order-paid.dlq.
@Slf4j
@Component
public class OrderEventListener {
    @Autowired
    private StockService stockService;

    @RabbitListener(queues = RabbitConfig.QUEUE)
    public void onOrderEvent(OrderEvent event, @Header(AmqpHeaders.RECEIVED_ROUTING_KEY) String routingKey) {
        log.info("Received {} event {} for order {}", routingKey, event.getEventId(), event.getOrderId());
        switch (routingKey) {
            case RabbitConfig.ROUTING_ORDER_PAID -> stockService.applyOrderPaid(event);
            case RabbitConfig.ROUTING_ORDER_EXPIRED -> stockService.applyOrderExpired(event);
            default -> throw new IllegalArgumentException("Unexpected routing key " + routingKey);
        }
    }
}
