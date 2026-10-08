package com.example.cart_service.messaging;

import com.example.cart_service.configuration.RabbitConfig;
import com.example.cart_service.dto.OrderPaidEvent;
import com.example.cart_service.service.CartService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

// Failures are retried (see application.yml), then dead-lettered to cart.order-paid.dlq.
@Slf4j
@Component
public class OrderPaidListener {
    @Autowired
    private CartService cartService;

    @RabbitListener(queues = RabbitConfig.QUEUE)
    public void onOrderPaid(OrderPaidEvent event) {
        log.info("Received order-paid event {} for order {}", event.getEventId(), event.getOrderId());
        cartService.removePurchasedItems(event);
    }
}
