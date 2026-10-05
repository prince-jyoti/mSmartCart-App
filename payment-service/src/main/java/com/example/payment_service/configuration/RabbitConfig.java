package com.example.payment_service.configuration;

import org.springframework.amqp.core.*;
import org.springframework.amqp.support.converter.DefaultJackson2JavaTypeMapper;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.Jackson2JavaTypeMapper.TypePrecedence;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

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
