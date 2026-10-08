package com.example.order_service.configuration;

import org.springframework.amqp.core.*;
import org.springframework.amqp.support.converter.DefaultJackson2JavaTypeMapper;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.Jackson2JavaTypeMapper.TypePrecedence;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.util.ArrayList;
import java.util.List;

// Declared identically in payment-service and order-service. Declaring is idempotent, and
// doing it on both sides means events published before order-service has ever started
// still land in the queue instead of being dropped by an exchange with no bindings.
@Configuration
@EnableScheduling
public class RabbitConfig {
    public static final String EXCHANGE = "payment.events";
    public static final String QUEUE = "order.payment-events";
    public static final String DLX = "payment.events.dlx";
    public static final String DLQ = "order.payment-events.dlq";
    public static final String ROUTING_COMPLETED = "payment.completed";
    public static final String ROUTING_FAILED = "payment.failed";

    // order-service publishes these (via its outbox). The consumer queues are declared here too,
    // identically to product-service and cart-service, so an event sent before a consumer has
    // ever started is queued instead of dropped.
    public static final String ORDER_EXCHANGE = "order.events";
    // The exchange this service's OutboxPublisher sends to (the publisher is shared code).
    public static final String OUTBOX_EXCHANGE = ORDER_EXCHANGE;
    public static final String ROUTING_ORDER_PAID = "order.paid";
    public static final String ROUTING_ORDER_EXPIRED = "order.expired";
    public static final String ORDER_DLX = "order.events.dlx";
    public static final String PRODUCT_QUEUE = "product.order-paid";
    public static final String CART_QUEUE = "cart.order-paid";

    @Bean
    TopicExchange paymentExchange() {
        return new TopicExchange(EXCHANGE, true, false);
    }

    @Bean
    FanoutExchange deadLetterExchange() {
        return new FanoutExchange(DLX, true, false);
    }

    @Bean
    Queue orderPaymentQueue() {
        return QueueBuilder.durable(QUEUE).deadLetterExchange(DLX).build();
    }

    @Bean
    Queue orderPaymentDeadLetterQueue() {
        return QueueBuilder.durable(DLQ).build();
    }

    @Bean
    Binding paymentBinding() {
        return BindingBuilder.bind(orderPaymentQueue()).to(paymentExchange()).with("payment.*");
    }

    @Bean
    Binding deadLetterBinding() {
        return BindingBuilder.bind(orderPaymentDeadLetterQueue()).to(deadLetterExchange());
    }

    @Bean
    TopicExchange orderExchange() {
        return new TopicExchange(ORDER_EXCHANGE, true, false);
    }

    // Direct, not fanout: each queue dead-letters under its own name, into its own DLQ.
    @Bean
    DirectExchange orderDeadLetterExchange() {
        return new DirectExchange(ORDER_DLX, true, false);
    }

    @Bean
    Declarables orderPaidConsumerQueues() {
        List<Declarable> all = new ArrayList<>(consumerQueue(PRODUCT_QUEUE));
        all.addAll(consumerQueue(CART_QUEUE));
        // product-service also gives reserved stock back when an order expires.
        all.add(new Binding(PRODUCT_QUEUE, Binding.DestinationType.QUEUE, ORDER_EXCHANGE, ROUTING_ORDER_EXPIRED, null));
        return new Declarables(all);
    }

    // A consumer's queue, its DLQ, and their bindings.
    private List<Declarable> consumerQueue(String name) {
        Queue queue = QueueBuilder.durable(name).deadLetterExchange(ORDER_DLX).deadLetterRoutingKey(name + ".dlq").build();
        Queue dlq = QueueBuilder.durable(name + ".dlq").build();
        return List.of(queue, dlq,
                BindingBuilder.bind(queue).to(orderExchange()).with(ROUTING_ORDER_PAID),
                BindingBuilder.bind(dlq).to(orderDeadLetterExchange()).with(name + ".dlq"));
    }

    // Each service has its own PaymentEvent class, so ignore the sender's class name header
    // and deserialize into whatever type the receiving code asks for.
    @Bean
    Jackson2JsonMessageConverter jsonMessageConverter() {
        Jackson2JsonMessageConverter converter = new Jackson2JsonMessageConverter();
        DefaultJackson2JavaTypeMapper typeMapper = new DefaultJackson2JavaTypeMapper();
        typeMapper.setTypePrecedence(TypePrecedence.INFERRED);
        converter.setJavaTypeMapper(typeMapper);
        return converter;
    }
}
