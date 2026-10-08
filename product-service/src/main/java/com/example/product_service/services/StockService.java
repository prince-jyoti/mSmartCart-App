package com.example.product_service.services;

import com.example.product_service.dto.OrderEvent;
import com.example.product_service.dto.ReservationReq;
import com.example.product_service.entity.ProcessedEvent;
import com.example.product_service.entity.StockReservation;
import com.example.product_service.entity.StockReservation.Status;
import com.example.product_service.exception.NotFoundException;
import com.example.product_service.repository.ProcessedEventRepo;
import com.example.product_service.repository.ProductRepo;
import com.example.product_service.repository.StockReservationRepo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

// Stock lifecycle of an order: reserved when it is placed, confirmed when it is paid,
// released (returned to stock) when it expires unpaid.
@Slf4j
@Service
public class StockService {
    @Autowired
    private ProductRepo productRepo;
    @Autowired
    private ProcessedEventRepo processedEventRepo;
    @Autowired
    private StockReservationRepo reservationRepo;

    // All lines or none: a line without enough stock throws, which rolls back the lines
    // already taken. Calling again for the same order is a no-op, so order-service may retry.
    @Transactional
    public void reserve(ReservationReq req) {
        if (reservationRepo.existsByOrderId(req.getOrderId())) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        for (ReservationReq.Item item : req.getItems()) {
            if (productRepo.decrementStock(item.getProductId(), item.getQuantity()) == 0) {
                var product = productRepo.findById(item.getProductId())
                        .orElseThrow(() -> new NotFoundException("Product not found: " + item.getProductId()));
                throw new IllegalStateException("Not enough stock for " + product.getTitle() + ": " + product.getStock() + " left");
            }
            StockReservation r = new StockReservation();
            r.setOrderId(req.getOrderId());
            r.setProductId(item.getProductId());
            r.setQuantity(item.getQuantity());
            r.setStatus(Status.RESERVED);
            r.setCreatedAt(now);
            r.setUpdatedAt(now);
            reservationRepo.save(r);
        }
    }

    // order.paid: the stock was taken at reservation time, so this normally just confirms it.
    // If the order had already expired (payment arrived late), its stock was given back and
    // is taken again here. Orders placed before reservations existed have none: take it now.
    @Transactional
    public void applyOrderPaid(OrderEvent event) {
        if (alreadyApplied(event)) {
            return;
        }
        List<StockReservation> reservations = reservationRepo.findByOrderId(event.getOrderId());
        if (reservations.isEmpty()) {
            event.getItems().forEach(i -> take(i.getProductId(), i.getQuantity(), event.getOrderId()));
            return;
        }
        for (StockReservation r : reservations) {
            if (r.getStatus() == Status.RELEASED) {
                take(r.getProductId(), r.getQuantity(), event.getOrderId());
            }
            if (r.getStatus() != Status.CONFIRMED) {
                r.moveTo(Status.CONFIRMED);
            }
        }
    }

    // order.expired: give back whatever this order still holds.
    @Transactional
    public void applyOrderExpired(OrderEvent event) {
        if (alreadyApplied(event)) {
            return;
        }
        for (StockReservation r : reservationRepo.findByOrderId(event.getOrderId())) {
            if (r.getStatus() == Status.RESERVED) {
                productRepo.incrementStock(r.getProductId(), r.getQuantity());
                r.moveTo(Status.RELEASED);
            }
        }
    }

    // Marks the event processed in the caller's transaction: a redelivery after a crash
    // either finds it done or redoes the whole change.
    private boolean alreadyApplied(OrderEvent event) {
        if (processedEventRepo.existsById(event.getEventId())) {
            log.info("Order event {} already applied, skipping", event.getEventId());
            return true;
        }
        processedEventRepo.save(new ProcessedEvent(event.getEventId(), LocalDateTime.now()));
        return false;
    }

    // The order is already paid, so a shortage can't be refused here, only reported.
    private void take(Long productId, int quantity, String orderId) {
        if (productRepo.decrementStock(productId, quantity) == 0) {
            log.error("OVERSOLD: product {} lacks stock for {} unit(s) of paid order {}; needs manual follow-up",
                    productId, quantity, orderId);
        }
    }
}
