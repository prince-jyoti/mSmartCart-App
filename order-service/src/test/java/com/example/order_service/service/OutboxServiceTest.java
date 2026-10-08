package com.example.order_service.service;

import com.example.order_service.configuration.RabbitConfig;
import com.example.order_service.entity.Order;
import com.example.order_service.entity.OrderItem;
import com.example.order_service.entity.OutboxEvent;
import com.example.order_service.repository.OutboxRepo;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class OutboxServiceTest {
    @Test
    void orderPaidEventCarriesTheBuyerAndEveryItem() throws Exception {
        OutboxRepo repo = mock(OutboxRepo.class);
        OutboxService service = new OutboxService();
        ReflectionTestUtils.setField(service, "outboxRepo", repo);
        Order order = new Order();
        order.setOrderId("ORD-1");
        order.setUserId(5L);
        order.setItems(List.of(item(1L, 2), item(2L, 1)));

        service.recordOrderPaid(order);

        ArgumentCaptor<OutboxEvent> row = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(repo).save(row.capture());
        assertThat(row.getValue().getRoutingKey()).isEqualTo(RabbitConfig.ROUTING_ORDER_PAID);
        assertThat(row.getValue().getPublishedAt()).isNull();
        JsonNode payload = new ObjectMapper().readTree(row.getValue().getPayload());
        assertThat(payload.get("eventId").asText()).isEqualTo(row.getValue().getEventId());
        assertThat(payload.get("orderId").asText()).isEqualTo("ORD-1");
        assertThat(payload.get("userId").asLong()).isEqualTo(5L);
        assertThat(payload.get("items")).hasSize(2);
        assertThat(payload.get("items").get(0).get("productId").asLong()).isEqualTo(1L);
        assertThat(payload.get("items").get(0).get("quantity").asInt()).isEqualTo(2);
    }

    private static OrderItem item(long productId, int quantity) {
        OrderItem i = new OrderItem();
        i.setProductId(productId);
        i.setQuantity(quantity);
        return i;
    }
}
