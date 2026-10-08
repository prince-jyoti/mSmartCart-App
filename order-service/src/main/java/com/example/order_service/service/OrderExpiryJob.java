package com.example.order_service.service;

import java.time.Instant;
import com.example.order_service.entity.Order;
import com.example.order_service.repository.OrderRepo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.scheduling.annotation.Scheduled;

import java.time.Duration;
import java.util.List;

// Expires orders that were never paid, so the stock reserved for them goes back on sale.
// PAYMENT_FAILED orders count too: they can be retried, but only until the deadline.
@Slf4j
@Component
public class OrderExpiryJob {
    private static final List<String> UNPAID = List.of("PENDING", "PAYMENT_FAILED");

    @Autowired
    private OrderRepo orderRepo;
    @Autowired
    private OrderService orderService;

    @Value("${order.expiry.after:PT30M}")
    private Duration expireAfter;

    @Scheduled(fixedDelayString = "${order.expiry.scan-interval:PT1M}")
    public void expireUnpaidOrders() {
        Instant cutoff = Instant.now().minus(expireAfter);
        List<Order> due;
        do {
            due = orderRepo.findTop100ByStatusInAndCreatedAtBeforeOrderByCreatedAtAsc(UNPAID, cutoff);
            int expired = 0;
            for (Order order : due) {
                try {
                    if (orderService.expireOrder(order.getId())) {
                        expired++;
                    }
                } catch (Exception e) {
                    // One bad order must not stop the rest; it is picked up again next run.
                    log.error("Could not expire order {}", order.getOrderId(), e);
                }
            }
            if (expired > 0) {
                log.info("Expired {} unpaid order(s) older than {}", expired, expireAfter);
            }
        } while (due.size() == 100);
    }
}
