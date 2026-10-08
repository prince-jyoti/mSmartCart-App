package com.example.product_service.services;

import com.example.product_service.dto.OrderEvent;
import com.example.product_service.dto.ReservationReq;
import com.example.product_service.entity.Product;
import com.example.product_service.entity.StockReservation;
import com.example.product_service.entity.StockReservation.Status;
import com.example.product_service.repository.ProcessedEventRepo;
import com.example.product_service.repository.ProductRepo;
import com.example.product_service.repository.StockReservationRepo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StockServiceTest {
    private final ProductRepo productRepo = mock(ProductRepo.class);
    private final ProcessedEventRepo processedRepo = mock(ProcessedEventRepo.class);
    private final StockReservationRepo reservationRepo = mock(StockReservationRepo.class);
    private final StockService stockService = new StockService();

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(stockService, "productRepo", productRepo);
        ReflectionTestUtils.setField(stockService, "processedEventRepo", processedRepo);
        ReflectionTestUtils.setField(stockService, "reservationRepo", reservationRepo);
        when(productRepo.decrementStock(anyLong(), anyInt())).thenReturn(1);
    }

    // ---- reserve: when the order is placed ----------------------------------------------------

    @Test
    void placingAnOrderTakesTheStockAndRecordsAReservationPerLine() {
        stockService.reserve(new ReservationReq("ORD-1", List.of(line(1L, 2), line(2L, 1))));

        verify(productRepo).decrementStock(1L, 2);
        verify(productRepo).decrementStock(2L, 1);
        ArgumentCaptor<StockReservation> saved = ArgumentCaptor.forClass(StockReservation.class);
        verify(reservationRepo, times(2)).save(saved.capture());
        assertThat(saved.getAllValues()).allSatisfy(r -> {
            assertThat(r.getOrderId()).isEqualTo("ORD-1");
            assertThat(r.getStatus()).isEqualTo(Status.RESERVED);
        });
    }

    @Test
    void notEnoughStockFailsTheWholeReservationWithAClearMessage() {
        when(productRepo.decrementStock(2L, 5)).thenReturn(0);
        Product lamp = new Product();
        lamp.setTitle("Lamp");
        lamp.setStock(3);
        when(productRepo.findById(2L)).thenReturn(Optional.of(lamp));

        // Throwing rolls back the first line's decrement too (one transaction).
        assertThatThrownBy(() -> stockService.reserve(new ReservationReq("ORD-1", List.of(line(1L, 1), line(2L, 5)))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Not enough stock for Lamp: 3 left");
    }

    @Test
    void reservingTheSameOrderTwiceTakesStockOnce() {
        when(reservationRepo.existsByOrderId("ORD-1")).thenReturn(true); // a retried call

        stockService.reserve(new ReservationReq("ORD-1", List.of(line(1L, 2))));

        verify(productRepo, never()).decrementStock(anyLong(), anyInt());
    }

    // ---- order.paid --------------------------------------------------------------------------

    @Test
    void paidOrderConfirmsItsReservationWithoutTakingStockAgain() {
        StockReservation r = reservation(1L, 2, Status.RESERVED);
        when(reservationRepo.findByOrderId("ORD-1")).thenReturn(List.of(r));

        stockService.applyOrderPaid(event("evt-1", 1L, 2));

        assertThat(r.getStatus()).isEqualTo(Status.CONFIRMED);
        verify(productRepo, never()).decrementStock(anyLong(), anyInt());
    }

    @Test
    void paymentAfterExpiryTakesTheReleasedStockAgain() {
        StockReservation r = reservation(1L, 2, Status.RELEASED);
        when(reservationRepo.findByOrderId("ORD-1")).thenReturn(List.of(r));

        stockService.applyOrderPaid(event("evt-1", 1L, 2));

        verify(productRepo).decrementStock(1L, 2);
        assertThat(r.getStatus()).isEqualTo(Status.CONFIRMED);
    }

    @Test
    void orderPlacedBeforeReservationsExistedHasItsStockTakenWhenPaid() {
        when(reservationRepo.findByOrderId("ORD-1")).thenReturn(List.of());

        stockService.applyOrderPaid(event("evt-1", 1L, 2));

        verify(productRepo).decrementStock(1L, 2);
    }

    @Test
    void oversoldItemIsLoggedWithoutBlockingTheEvent() {
        when(reservationRepo.findByOrderId("ORD-1")).thenReturn(List.of(reservation(1L, 5, Status.RELEASED)));
        when(productRepo.decrementStock(1L, 5)).thenReturn(0); // sold to someone else meanwhile

        // Throwing would retry and dead-letter a message for an order that is already paid.
        assertThatCode(() -> stockService.applyOrderPaid(event("evt-1", 1L, 5))).doesNotThrowAnyException();
    }

    // ---- order.expired -----------------------------------------------------------------------

    @Test
    void expiredOrderGivesItsReservedStockBack() {
        StockReservation r = reservation(1L, 2, Status.RESERVED);
        when(reservationRepo.findByOrderId("ORD-1")).thenReturn(List.of(r));

        stockService.applyOrderExpired(event("evt-1", 1L, 2));

        verify(productRepo).incrementStock(1L, 2);
        assertThat(r.getStatus()).isEqualTo(Status.RELEASED);
    }

    @Test
    void stockAlreadyConfirmedOrReleasedIsNotReturnedTwice() {
        when(reservationRepo.findByOrderId("ORD-1")).thenReturn(List.of(
                reservation(1L, 2, Status.CONFIRMED), reservation(2L, 1, Status.RELEASED)));

        stockService.applyOrderExpired(event("evt-1", 1L, 2));

        verify(productRepo, never()).incrementStock(anyLong(), anyInt());
    }

    // ---- redelivery ----------------------------------------------------------------------------

    @Test
    void aRedeliveredEventChangesNothing() {
        when(processedRepo.existsById("evt-1")).thenReturn(true);

        stockService.applyOrderPaid(event("evt-1", 1L, 2));
        stockService.applyOrderExpired(event("evt-1", 1L, 2));

        verify(reservationRepo, never()).findByOrderId(any());
        verify(productRepo, never()).decrementStock(anyLong(), anyInt());
        verify(productRepo, never()).incrementStock(anyLong(), anyInt());
    }

    private static ReservationReq.Item line(long productId, int quantity) {
        return new ReservationReq.Item(productId, quantity);
    }

    private static OrderEvent event(String id, long productId, int quantity) {
        return new OrderEvent(id, "ORD-1", 5L, List.of(new OrderEvent.Item(productId, quantity)), null);
    }

    private static StockReservation reservation(long productId, int quantity, Status status) {
        StockReservation r = new StockReservation();
        r.setOrderId("ORD-1");
        r.setProductId(productId);
        r.setQuantity(quantity);
        r.setStatus(status);
        r.setCreatedAt(LocalDateTime.now());
        r.setUpdatedAt(LocalDateTime.now());
        return r;
    }
}
