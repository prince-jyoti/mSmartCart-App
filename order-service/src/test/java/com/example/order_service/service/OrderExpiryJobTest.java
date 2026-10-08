package com.example.order_service.service;

import com.example.order_service.entity.Order;
import com.example.order_service.repository.OrderRepo;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OrderExpiryJobTest {
    private final OrderRepo orderRepo = mock(OrderRepo.class);
    private final OrderService orderService = mock(OrderService.class);
    private final OrderExpiryJob job = new OrderExpiryJob();

    @Test
    void onlyUnpaidOrdersOlderThanTheDeadlineAreExpiredAndOneFailureDoesNotStopTheRest() {
        ReflectionTestUtils.setField(job, "orderRepo", orderRepo);
        ReflectionTestUtils.setField(job, "orderService", orderService);
        ReflectionTestUtils.setField(job, "expireAfter", Duration.ofMinutes(30));
        when(orderRepo.findTop100ByStatusInAndCreatedAtBeforeOrderByCreatedAtAsc(any(), any()))
                .thenReturn(List.of(order(1L), order(2L)));
        when(orderService.expireOrder(1L)).thenThrow(new RuntimeException("db hiccup"));
        when(orderService.expireOrder(2L)).thenReturn(true);

        Instant before = Instant.now();
        job.expireUnpaidOrders();

        verify(orderService).expireOrder(2L); // still processed after order 1 failed
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Collection<String>> statuses = ArgumentCaptor.forClass(Collection.class);
        ArgumentCaptor<Instant> cutoff = ArgumentCaptor.forClass(Instant.class);
        verify(orderRepo).findTop100ByStatusInAndCreatedAtBeforeOrderByCreatedAtAsc(statuses.capture(), cutoff.capture());
        assertThat(statuses.getValue()).containsExactlyInAnyOrder("PENDING", "PAYMENT_FAILED");
        assertThat(cutoff.getValue()).isBetween(before.minus(Duration.ofMinutes(31)), before.minus(Duration.ofMinutes(29)));
    }

    private static Order order(long id) {
        Order o = new Order();
        o.setId(id);
        o.setOrderId("ORD-" + id);
        return o;
    }
}
