package com.example.payment_service.service;

import com.example.payment_service.razorpay.RazorpayClient;
import com.example.payment_service.entity.PaymentAttempt;
import com.example.payment_service.exception.ServiceUnavailableException;
import com.example.payment_service.repository.PaymentAttemptRepo;
import kong.unirest.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PaymentReconcilerTest {
    private final PaymentAttemptRepo attemptRepo = mock(PaymentAttemptRepo.class);
    private final RazorpayClient razorpay = mock(RazorpayClient.class);
    private final PaymentService paymentService = mock(PaymentService.class);
    private final PaymentReconciler reconciler = new PaymentReconciler();

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(reconciler, "attemptRepo", attemptRepo);
        ReflectionTestUtils.setField(reconciler, "razorpay", razorpay);
        ReflectionTestUtils.setField(reconciler, "paymentService", paymentService);
        ReflectionTestUtils.setField(reconciler, "minAge", Duration.ofMinutes(1));
        ReflectionTestUtils.setField(reconciler, "giveUpAfter", Duration.ofHours(2));
    }

    @Test
    void aSuccessfulPaymentForTheRightAmountIsRecorded() {
        open(attempt(1L, "order_a", 24950, 5));
        JSONObject good = payment("pay_ok", "captured", 24950);
        when(razorpay.paymentsForOrder("order_a")).thenReturn(List.of(payment("pay_fail", "failed", 24950), good));

        reconciler.reconcile();

        verify(paymentService).recordReconciledPayment(1L, good);
    }

    @Test
    void failedOrWrongAmountPaymentsAreNotRecorded() {
        open(attempt(1L, "order_a", 24950, 5));
        when(razorpay.paymentsForOrder("order_a")).thenReturn(List.of(
                payment("pay_1", "failed", 24950), payment("pay_2", "captured", 100)));

        reconciler.reconcile();

        verify(paymentService, never()).recordReconciledPayment(anyLong(), any());
        verify(paymentService, never()).abandonAttempt(anyLong()); // still within the window
    }

    @Test
    void anAttemptWithoutPaymentIsGivenUpOnAfterTheWindow() {
        open(attempt(1L, "order_a", 24950, 3 * 60));
        when(razorpay.paymentsForOrder("order_a")).thenReturn(List.of());

        reconciler.reconcile();

        verify(paymentService).abandonAttempt(1L);
    }

    @Test
    void razorpayBeingDownForOneAttemptDoesNotStopTheOthersOrGiveUpOnIt() {
        open(attempt(1L, "order_a", 24950, 3 * 60), attempt(2L, "order_b", 500, 5));
        when(razorpay.paymentsForOrder("order_a")).thenThrow(new ServiceUnavailableException("down"));
        JSONObject good = payment("pay_b", "authorized", 500);
        when(razorpay.paymentsForOrder("order_b")).thenReturn(List.of(good));

        reconciler.reconcile();

        verify(paymentService, never()).abandonAttempt(1L); // unknown is not the same as unpaid
        verify(paymentService).recordReconciledPayment(2L, good);
    }

    @Test
    void anAttemptRazorpayRejectsIsGivenUpOnOnlyAfterTheWindow() {
        open(attempt(1L, "order_old", 100, 3 * 60), attempt(2L, "order_new", 100, 5));
        when(razorpay.paymentsForOrder(any())).thenThrow(new IllegalArgumentException("Razorpay returned HTTP 400"));

        reconciler.reconcile();

        verify(paymentService).abandonAttempt(1L);
        verify(paymentService, never()).abandonAttempt(2L);
    }

    private void open(PaymentAttempt... attempts) {
        when(attemptRepo.findTop100ByStatusAndCreatedAtBeforeOrderByCreatedAtAsc(any(), any())).thenReturn(List.of(attempts));
    }

    private static PaymentAttempt attempt(long id, String razorpayOrderId, long amountPaise, int minutesOld) {
        PaymentAttempt a = new PaymentAttempt();
        a.setId(id);
        a.setRazorpayOrderId(razorpayOrderId);
        a.setOrderIdRef("ORD-" + id);
        a.setAmountPaise(amountPaise);
        a.setStatus(PaymentAttempt.Status.OPEN);
        a.setCreatedAt(LocalDateTime.now().minusMinutes(minutesOld));
        return a;
    }

    private static JSONObject payment(String id, String status, long amount) {
        return new JSONObject().put("id", id).put("status", status).put("amount", amount).put("created_at", 1_700_000_000L);
    }
}
