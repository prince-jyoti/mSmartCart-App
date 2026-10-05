package com.example.order_service.messaging;

import com.example.order_service.configuration.RabbitConfig;
import com.example.order_service.dto.PaymentEvent;
import com.example.order_service.service.OrderService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

// Delivery is at-least-once, so this must be safe to run twice for the same event:
// OrderService.applyPaymentOutcome only moves an order forward and ignores repeats.
// If it throws, the listener retries (see application.yml) and then the message is
// dead-lettered to order.payment-events.dlq for inspection instead of looping forever.
@Slf4j
@Component
public class PaymentEventListener {
    @Autowired
    private OrderService orderService;

    @RabbitListener(queues = RabbitConfig.QUEUE)
    public void onPaymentEvent(PaymentEvent event) {
        log.info("Received payment event {} for order {}: {}", event.getEventId(), event.getOrderId(), event.getStatus());
        orderService.applyPaymentOutcome(event.getOrderId(), event.getStatus(), event.getPaymentId());
    }
}
