package com.example.payment_service.service;

import com.example.payment_service.razorpay.RazorpayClient;
import com.example.payment_service.entity.PaymentAttempt;
import com.example.payment_service.repository.PaymentAttemptRepo;
import kong.unirest.json.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

// Settles payments the normal verification never recorded: the customer paid but closed the
// page first, or Razorpay was unreachable when the browser asked us to verify. For each OPEN
// attempt it asks Razorpay which payments exist for that Razorpay order and records a successful
// one for the right amount, which then flows on to order-service like any verified payment.
@Slf4j
@Component
public class PaymentReconciler {
    @Autowired
    private PaymentAttemptRepo attemptRepo;
    @Autowired
    private RazorpayClient razorpay;
    @Autowired
    private PaymentService paymentService;

    // Left alone this long, so the browser's own verification normally gets there first.
    @Value("${payment.reconcile.min-age:PT1M}")
    private Duration minAge;

    // Without a successful payment by then, stop asking.
    @Value("${payment.reconcile.give-up-after:PT2H}")
    private Duration giveUpAfter;

    @Scheduled(fixedDelayString = "${payment.reconcile.interval:PT1M}")
    public void reconcile() {
        List<PaymentAttempt> open = attemptRepo.findTop100ByStatusAndCreatedAtBeforeOrderByCreatedAtAsc(
                PaymentAttempt.Status.OPEN, LocalDateTime.now().minus(minAge));
        for (PaymentAttempt attempt : open) {
            try {
                reconcile(attempt);
            } catch (IllegalArgumentException e) {
                // Razorpay rejects the request itself (unknown order...): retrying won't help, so
                // stop once the window is over instead of blocking the batch forever.
                log.warn("Razorpay rejected reconciliation of {} (order {}): {}",
                        attempt.getRazorpayOrderId(), attempt.getOrderIdRef(), e.getMessage());
                if (pastDeadline(attempt)) {
                    paymentService.abandonAttempt(attempt.getId());
                }
            } catch (Exception e) {
                // Razorpay unreachable, database hiccup...: the attempt stays OPEN for the next run.
                log.warn("Could not reconcile Razorpay order {} (order {}): {}",
                        attempt.getRazorpayOrderId(), attempt.getOrderIdRef(), e.getMessage());
            }
        }
    }

    private void reconcile(PaymentAttempt attempt) {
        Optional<JSONObject> paid = razorpay.paymentsForOrder(attempt.getRazorpayOrderId()).stream()
                .filter(p -> "captured".equals(p.optString("status")) || "authorized".equals(p.optString("status")))
                .filter(p -> p.optLong("amount") == attempt.getAmountPaise())
                .findFirst();
        if (paid.isPresent()) {
            paymentService.recordReconciledPayment(attempt.getId(), paid.get());
        } else if (pastDeadline(attempt)) {
            paymentService.abandonAttempt(attempt.getId());
        }
    }

    private boolean pastDeadline(PaymentAttempt attempt) {
        return attempt.getCreatedAt().isBefore(LocalDateTime.now().minus(giveUpAfter));
    }
}
