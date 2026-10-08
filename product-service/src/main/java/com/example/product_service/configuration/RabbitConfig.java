package com.example.product_service.configuration;

import org.springframework.amqp.core.*;
import org.springframework.amqp.support.converter.DefaultJackson2JavaTypeMapper;
import org.springframework.amqp.support.converter.Jackson2JavaTypeMapper.TypePrecedence;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

// Same names and arguments as order-service's RabbitConfig, which declares this queue too.
// RabbitMQ rejects a redeclaration with different arguments, so keep the two in sync.
@Configuration
public class RabbitConfig {
    public static final String ORDER_EXCHANGE = "order.events";
    public static final String ROUTING_ORDER_PAID = "order.paid";
    public static final String ROUTING_ORDER_EXPIRED = "order.expired";
    public static final String ORDER_DLX = "order.events.dlx";
    public static final String QUEUE = "product.order-paid";
    public static final String DLQ = QUEUE + ".dlq";

    @Bean
    TopicExchange orderExchange() {
        return new TopicExchange(ORDER_EXCHANGE, true, false);
    }

    @Bean
    DirectExchange orderDeadLetterExchange() {
        return new DirectExchange(ORDER_DLX, true, false);
    }

    @Bean
    Queue orderPaidQueue() {
        return QueueBuilder.durable(QUEUE).deadLetterExchange(ORDER_DLX).deadLetterRoutingKey(DLQ).build();
    }

    @Bean
    Queue orderPaidDeadLetterQueue() {
        return QueueBuilder.durable(DLQ).build();
    }

    @Bean
    Binding orderPaidBinding() {
        return BindingBuilder.bind(orderPaidQueue()).to(orderExchange()).with(ROUTING_ORDER_PAID);
    }

    // Expired orders give their reserved stock back.
    @Bean
    Binding orderExpiredBinding() {
        return BindingBuilder.bind(orderPaidQueue()).to(orderExchange()).with(ROUTING_ORDER_EXPIRED);
    }

    @Bean
    Binding orderPaidDeadLetterBinding() {
        return BindingBuilder.bind(orderPaidDeadLetterQueue()).to(orderDeadLetterExchange()).with(DLQ);
    }

    // Deserialize into this service's own OrderEvent, ignoring the sender's class name header.
    @Bean
    Jackson2JsonMessageConverter jsonMessageConverter() {
        Jackson2JsonMessageConverter converter = new Jackson2JsonMessageConverter();
        DefaultJackson2JavaTypeMapper typeMapper = new DefaultJackson2JavaTypeMapper();
        typeMapper.setTypePrecedence(TypePrecedence.INFERRED);
        converter.setJavaTypeMapper(typeMapper);
        return converter;
    }
}
