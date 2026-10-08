package com.example.payment_service.service;

import com.example.payment_service.configuration.RabbitConfig;
import com.example.payment_service.entity.OutboxEvent;
import com.example.payment_service.repository.OutboxRepo;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OutboxTest {

    @ExtendWith(MockitoExtension.class)
    @org.junit.jupiter.api.Nested
    class Recording {
        @Mock
        private OutboxRepo outboxRepo;
        @InjectMocks
        private OutboxService outboxService;

        @Test
        void paidEventCarriesOrderPaymentAndRoutingKey() throws Exception {
            outboxService.recordPaid("ORD-1", 10L);

            OutboxEvent row = saved();
            assertThat(row.getRoutingKey()).isEqualTo(RabbitConfig.ROUTING_COMPLETED);
            assertThat(row.getPublishedAt()).isNull();
            JsonNode payload = new ObjectMapper().readTree(row.getPayload());
            assertThat(payload.get("orderId").asText()).isEqualTo("ORD-1");
            assertThat(payload.get("paymentId").asLong()).isEqualTo(10L);
            assertThat(payload.get("status").asText()).isEqualTo("PAID");
            assertThat(payload.get("eventId").asText()).isEqualTo(row.getEventId());
        }

        @Test
        void failedEventHasNoPaymentId() throws Exception {
            outboxService.recordFailed("ORD-1");

            OutboxEvent row = saved();
            assertThat(row.getRoutingKey()).isEqualTo(RabbitConfig.ROUTING_FAILED);
            JsonNode payload = new ObjectMapper().readTree(row.getPayload());
            assertThat(payload.get("status").asText()).isEqualTo("PAYMENT_FAILED");
            assertThat(payload.get("paymentId").isNull()).isTrue();
        }

        @Test
        void everyEventGetsItsOwnId() {
            outboxService.recordPaid("ORD-1", 10L);
            outboxService.recordPaid("ORD-1", 10L);

            ArgumentCaptor<OutboxEvent> rows = ArgumentCaptor.forClass(OutboxEvent.class);
            verify(outboxRepo, times(2)).save(rows.capture());
            assertThat(rows.getAllValues().get(0).getEventId()).isNotEqualTo(rows.getAllValues().get(1).getEventId());
        }

        private OutboxEvent saved() {
            ArgumentCaptor<OutboxEvent> row = ArgumentCaptor.forClass(OutboxEvent.class);
            verify(outboxRepo).save(row.capture());
            return row.getValue();
        }
    }

    @ExtendWith(MockitoExtension.class)
    @org.junit.jupiter.api.Nested
    class Publishing {
        @Mock
        private OutboxRepo outboxRepo;
        @Mock
        private RabbitTemplate rabbitTemplate;
        @InjectMocks
        private OutboxPublisher publisher;

        @Test
        void eventIsMarkedPublishedOnlyAfterTheBrokerConfirms() {
            OutboxEvent event = pending(1L);
            when(outboxRepo.findTop50ByPublishedAtIsNullOrderByIdAsc()).thenReturn(List.of(event));
            when(rabbitTemplate.invoke(any())).thenReturn(null); // send + confirm succeeded

            publisher.publishPending();

            assertThat(event.getPublishedAt()).isNotNull();
            verify(outboxRepo).save(event);
        }

        @Test
        void brokerFailureLeavesTheEventPendingAndStopsToKeepOrder() {
            OutboxEvent first = pending(1L);
            OutboxEvent second = pending(2L);
            when(outboxRepo.findTop50ByPublishedAtIsNullOrderByIdAsc()).thenReturn(List.of(first, second));
            when(rabbitTemplate.invoke(any())).thenThrow(new AmqpException("broker down"));

            publisher.publishPending();

            assertThat(first.getPublishedAt()).isNull();
            assertThat(second.getPublishedAt()).isNull();
            verify(rabbitTemplate, times(1)).invoke(any()); // never tries the second one
            verify(outboxRepo, never()).save(any());
        }

        private OutboxEvent pending(long id) {
            OutboxEvent e = new OutboxEvent();
            e.setId(id);
            e.setEventId("evt-" + id);
            e.setRoutingKey(RabbitConfig.ROUTING_COMPLETED);
            e.setPayload("{\"orderId\":\"ORD-" + id + "\"}");
            e.setCreatedAt(LocalDateTime.now());
            return e;
        }
    }
}
