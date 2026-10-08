package com.example.payment_service.service;

import java.math.BigDecimal;
import com.example.payment_service.client.OrderServiceClient;
import com.example.payment_service.dto.OrderRes;
import com.example.payment_service.dto.PaymentReq;
import com.example.payment_service.dto.PaymentRes;
import com.example.payment_service.entity.Payment;
import com.example.payment_service.entity.PaymentAttempt;
import com.example.payment_service.repository.PaymentAttemptRepo;
import com.example.payment_service.exception.NotFoundException;
import com.example.payment_service.exception.ServiceUnavailableException;
import com.example.payment_service.repository.PaymentRepo;
import com.example.payment_service.utils.BaseResponse;
import kong.unirest.GetRequest;
import kong.unirest.HttpRequestWithBody;
import kong.unirest.HttpResponse;
import kong.unirest.JsonNode;
import kong.unirest.RequestBodyEntity;
import kong.unirest.Unirest;
import kong.unirest.UnirestException;
import kong.unirest.json.JSONObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {
    private static final String SECRET = "test_secret";
    private static final String ORDER_REF = "ORD-1";
    private static final String RZP_ORDER = "order_abc";
    private static final String RZP_PAYMENT = "pay_123";
    private static final long BUYER = 5L;

    @Mock
    private PaymentRepo paymentRepo;
    @Mock
    private OrderServiceClient orderServiceClient;
    @Mock
    private OutboxService outboxService;
    @Mock
    private PaymentAttemptRepo attemptRepo;
    @InjectMocks
    private PaymentService paymentService;

    private MockedStatic<Unirest> unirest;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(paymentService, "key", "rzp_test_key");
        ReflectionTestUtils.setField(paymentService, "secret", SECRET);
        unirest = mockStatic(Unirest.class);
    }

    @AfterEach
    void tearDown() {
        unirest.close();
    }

    // ---- createPayment: the happy path --------------------------------------------------

    @Test
    void validPaymentIsSavedForTheCallerAndAPaidEventIsRecorded() {
        orderReturns("PENDING", 249.5);
        razorpayPaymentReturns("captured", 24950, RZP_ORDER);
        when(paymentRepo.save(any(Payment.class))).thenAnswer(inv -> {
            Payment p = inv.getArgument(0);
            p.setId(10L);
            return p;
        });

        PaymentRes res = paymentService.createPayment(request(validSignature()), BUYER);

        ArgumentCaptor<Payment> saved = ArgumentCaptor.forClass(Payment.class);
        verify(paymentRepo).save(saved.capture());
        assertThat(saved.getValue().getUserId()).isEqualTo(BUYER);
        assertThat(saved.getValue().getStatus()).isEqualTo("captured");
        assertThat(saved.getValue().getOrderIdRef()).isEqualTo(ORDER_REF);
        verify(outboxService).recordPaid(ORDER_REF, 10L);
        verify(outboxService, never()).recordFailed(anyString());
        assertThat(res.getId()).isEqualTo(10L);
    }

    @Test
    void paymentForAnOrderMarkedPaymentFailedCanBeRetried() {
        orderReturns("PAYMENT_FAILED", 100.0);
        razorpayPaymentReturns("authorized", 10000, RZP_ORDER);
        when(paymentRepo.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));

        paymentService.createPayment(request(validSignature()), BUYER);

        verify(outboxService).recordPaid(ORDER_REF, null);
    }

    // ---- createPayment: verification failures mark the order PAYMENT_FAILED --------------

    @Test
    void invalidSignatureIsRejectedAndRecordedAsFailed() {
        orderReturns("PENDING", 249.5);

        assertThatThrownBy(() -> paymentService.createPayment(request("forged"), BUYER))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("signature");

        verify(outboxService).recordFailed(ORDER_REF);
        verify(paymentRepo, never()).save(any());
        unirest.verifyNoInteractions(); // never asks Razorpay about a forged payment
    }

    @Test
    void paymentThatWasNotCapturedIsRejected() {
        orderReturns("PENDING", 249.5);
        razorpayPaymentReturns("failed", 24950, RZP_ORDER);

        assertThatThrownBy(() -> paymentService.createPayment(request(validSignature()), BUYER))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not successful");

        verify(outboxService).recordFailed(ORDER_REF);
        verify(paymentRepo, never()).save(any());
    }

    @Test
    void paidAmountMustMatchTheOrderTotalToThePaisa() {
        orderReturns("PENDING", 249.5);
        razorpayPaymentReturns("captured", 24900, RZP_ORDER); // 50 paise short

        assertThatThrownBy(() -> paymentService.createPayment(request(validSignature()), BUYER))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("amount");

        verify(outboxService).recordFailed(ORDER_REF);
        verify(paymentRepo, never()).save(any());
    }

    @Test
    void paymentMustBelongToTheRazorpayOrderInTheRequest() {
        orderReturns("PENDING", 249.5);
        razorpayPaymentReturns("captured", 24950, "order_someone_else");

        assertThatThrownBy(() -> paymentService.createPayment(request(validSignature()), BUYER))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Razorpay order");

        verify(outboxService).recordFailed(ORDER_REF);
    }

    @Test
    void razorpayBeingUnreachableIsReportedAsUnavailable() {
        orderReturns("PENDING", 249.5);
        GetRequest get = mock(GetRequest.class);
        unirest.when(() -> Unirest.get(anyString())).thenReturn(get);
        when(get.basicAuth(anyString(), anyString())).thenReturn(get);
        when(get.asJson()).thenThrow(new UnirestException("connection refused"));

        assertThatThrownBy(() -> paymentService.createPayment(request(validSignature()), BUYER))
                .isInstanceOf(ServiceUnavailableException.class)
                .hasMessageContaining("confirmed automatically");
        // The customer may well have paid: the order must not be failed. PaymentReconciler settles it.
        verifyNoInteractions(outboxService);
        verify(paymentRepo, never()).save(any());
    }

    @Test
    void razorpayNotKnowingThePaymentIsAFailureNotAnOutage() {
        orderReturns("PENDING", 249.5);
        GetRequest get = mock(GetRequest.class);
        @SuppressWarnings("unchecked")
        HttpResponse<JsonNode> response = mock(HttpResponse.class);
        unirest.when(() -> Unirest.get(anyString())).thenReturn(get);
        when(get.basicAuth(anyString(), anyString())).thenReturn(get);
        when(get.asJson()).thenReturn(response);
        when(response.getStatus()).thenReturn(400);

        assertThatThrownBy(() -> paymentService.createPayment(request(validSignature()), BUYER))
                .isInstanceOf(IllegalArgumentException.class);
        verify(outboxService).recordFailed(ORDER_REF);
    }

    @Test
    void verifiedPaymentClosesItsAttempt() {
        orderReturns("PENDING", 249.5);
        razorpayPaymentReturns("captured", 24950, RZP_ORDER);
        when(paymentRepo.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));
        PaymentAttempt attempt = attempt(PaymentAttempt.Status.OPEN);
        when(attemptRepo.findByRazorpayOrderId(RZP_ORDER)).thenReturn(Optional.of(attempt));

        paymentService.createPayment(request(validSignature()), BUYER);

        assertThat(attempt.getStatus()).isEqualTo(PaymentAttempt.Status.PAID);
    }

    // ---- reconciliation ----------------------------------------------------------------------

    @Test
    void reconciledPaymentIsRecordedLikeAVerifiedOne() {
        PaymentAttempt attempt = attempt(PaymentAttempt.Status.OPEN);
        when(attemptRepo.findById(7L)).thenReturn(Optional.of(attempt));
        when(attemptRepo.findByRazorpayOrderId(RZP_ORDER)).thenReturn(Optional.of(attempt));
        when(paymentRepo.findByPaymentId(RZP_PAYMENT)).thenReturn(Optional.empty());
        when(paymentRepo.save(any(Payment.class))).thenAnswer(inv -> {
            Payment p = inv.getArgument(0);
            p.setId(11L);
            return p;
        });

        paymentService.recordReconciledPayment(7L, new JSONObject()
                .put("id", RZP_PAYMENT).put("status", "captured").put("amount", 24950).put("created_at", 1_700_000_000L));

        ArgumentCaptor<Payment> saved = ArgumentCaptor.forClass(Payment.class);
        verify(paymentRepo).save(saved.capture());
        assertThat(saved.getValue().getUserId()).isEqualTo(BUYER);       // from the attempt, no token needed
        assertThat(saved.getValue().getOrderIdRef()).isEqualTo(ORDER_REF);
        verify(outboxService).recordPaid(ORDER_REF, 11L);
        assertThat(attempt.getStatus()).isEqualTo(PaymentAttempt.Status.PAID);
    }

    @Test
    void paymentVerifiedMeanwhileIsNotRecordedTwice() {
        PaymentAttempt attempt = attempt(PaymentAttempt.Status.OPEN);
        when(attemptRepo.findById(7L)).thenReturn(Optional.of(attempt));
        when(paymentRepo.findByPaymentId(RZP_PAYMENT)).thenReturn(Optional.of(storedPayment(BUYER)));

        paymentService.recordReconciledPayment(7L, new JSONObject()
                .put("id", RZP_PAYMENT).put("status", "captured").put("amount", 24950).put("created_at", 1_700_000_000L));

        verify(paymentRepo, never()).save(any());
        verifyNoInteractions(outboxService);
        assertThat(attempt.getStatus()).isEqualTo(PaymentAttempt.Status.PAID);
    }

    // ---- createPayment: ownership and state failures emit NO event ----------------------

    @Test
    void someoneElsesOrderIsRejectedWithoutTouchingTheOrder() {
        when(orderServiceClient.getOrderByOrderId(ORDER_REF)).thenThrow(new NotFoundException("Order not found"));
        when(paymentRepo.findByPaymentId(RZP_PAYMENT)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> paymentService.createPayment(request(validSignature()), BUYER))
                .isInstanceOf(NotFoundException.class);

        verifyNoInteractions(outboxService); // a stranger must not be able to fail someone's order
        verify(paymentRepo, never()).save(any());
    }

    @Test
    void anOrderThatIsAlreadyPaidCannotBePaidAgain() {
        orderReturns("PAID", 249.5);

        assertThatThrownBy(() -> paymentService.createPayment(request(validSignature()), BUYER))
                .isInstanceOf(IllegalStateException.class);

        verifyNoInteractions(outboxService);
    }

    @Test
    void paymentFinishedAfterTheOrderExpiredIsStillRecorded() {
        // The customer was charged; dropping the payment would lose their money.
        orderReturns("EXPIRED", 249.5);
        razorpayPaymentReturns("captured", 24950, RZP_ORDER);
        when(paymentRepo.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));

        paymentService.createPayment(request(validSignature()), BUYER);

        verify(outboxService).recordPaid(ORDER_REF, null);
    }

    // ---- createPayment: duplicate submissions -------------------------------------------

    @Test
    void resubmittingTheSamePaymentReturnsTheStoredResult() {
        Payment stored = storedPayment(BUYER);
        when(paymentRepo.findByPaymentId(RZP_PAYMENT)).thenReturn(Optional.of(stored));

        PaymentRes res = paymentService.createPayment(request(validSignature()), BUYER);

        assertThat(res.getId()).isEqualTo(stored.getId());
        verify(paymentRepo, never()).save(any());
        verifyNoInteractions(orderServiceClient, outboxService);
        unirest.verifyNoInteractions();
    }

    @Test
    void anotherUserCannotReadAPaymentByResubmittingItsId() {
        when(paymentRepo.findByPaymentId(RZP_PAYMENT)).thenReturn(Optional.of(storedPayment(BUYER)));

        assertThatThrownBy(() -> paymentService.createPayment(request(validSignature()), 99L))
                .isInstanceOf(NotFoundException.class);
    }

    // ---- getPaymentById: only the payer or an admin --------------------------------------

    @Test
    void payerCanReadTheirPayment() {
        when(paymentRepo.findById(10L)).thenReturn(Optional.of(storedPayment(BUYER)));

        assertThat(paymentService.getPaymentById(10L, BUYER, false).getPaymentId()).isEqualTo(RZP_PAYMENT);
    }

    @Test
    void otherUsersGetNotFoundForSomeoneElsesPayment() {
        when(paymentRepo.findById(10L)).thenReturn(Optional.of(storedPayment(BUYER)));

        assertThatThrownBy(() -> paymentService.getPaymentById(10L, 99L, false))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Payment not found");
    }

    @Test
    void adminCanReadAnyPayment() {
        when(paymentRepo.findById(10L)).thenReturn(Optional.of(storedPayment(BUYER)));

        assertThat(paymentService.getPaymentById(10L, 1L, true)).isNotNull();
    }

    // ---- createRazorpayOrder: amount comes from the order, in paise ----------------------

    @Test
    void razorpayOrderAmountIsTheOrderTotalInPaise() {
        orderReturns("PENDING", 1948.5);
        JSONObject sent = razorpayOrderCreation(200);

        paymentService.createRazorpayOrder(ORDER_REF, BUYER);

        ArgumentCaptor<PaymentAttempt> attempt = ArgumentCaptor.forClass(PaymentAttempt.class);
        verify(attemptRepo).save(attempt.capture());   // remembered for the reconciler
        assertThat(attempt.getValue().getRazorpayOrderId()).isEqualTo("order_new");
        assertThat(attempt.getValue().getOrderIdRef()).isEqualTo(ORDER_REF);
        assertThat(attempt.getValue().getUserId()).isEqualTo(BUYER);
        assertThat(attempt.getValue().getAmountPaise()).isEqualTo(194850L);
        assertThat(attempt.getValue().getStatus()).isEqualTo(PaymentAttempt.Status.OPEN);

        assertThat(sent.getLong("amount")).isEqualTo(194850L);
        assertThat(sent.getString("currency")).isEqualTo("INR");
        assertThat(sent.getString("receipt")).isEqualTo(ORDER_REF);
    }

    @Test
    void razorpayOrderIsRefusedForAnOrderThatIsAlreadyPaid() {
        orderReturns("PAID", 100.0);

        assertThatThrownBy(() -> paymentService.createRazorpayOrder(ORDER_REF, BUYER))
                .isInstanceOf(IllegalStateException.class);
        unirest.verifyNoInteractions();
    }

    @Test
    void noNewPaymentCanBeStartedForAnExpiredOrder() {
        orderReturns("EXPIRED", 100.0);

        assertThatThrownBy(() -> paymentService.createRazorpayOrder(ORDER_REF, BUYER))
                .isInstanceOf(IllegalStateException.class);
        unirest.verifyNoInteractions();
    }

    @Test
    void razorpayRejectingTheOrderIsReportedAsUnavailable() {
        orderReturns("PENDING", 100.0);
        razorpayOrderCreation(400);

        assertThatThrownBy(() -> paymentService.createRazorpayOrder(ORDER_REF, BUYER))
                .isInstanceOf(ServiceUnavailableException.class);
    }

    @Test
    void paiseAreExact() {
        assertThat(PaymentService.toPaise(new BigDecimal("249.50"))).isEqualTo(24950L);
        assertThat(PaymentService.toPaise(new BigDecimal("0.30"))).isEqualTo(30L);
        assertThat(PaymentService.toPaise(new BigDecimal("1948.5"))).isEqualTo(194850L);
        assertThat(PaymentService.toPaise(new BigDecimal("99999999.99"))).isEqualTo(9999999999L);
    }

    // ---- helpers -------------------------------------------------------------------------

    private void orderReturns(String status, double total) {
        OrderRes order = new OrderRes();
        order.setOrderId(ORDER_REF);
        order.setStatus(status);
        order.setTotalAmount(BigDecimal.valueOf(total));
        when(orderServiceClient.getOrderByOrderId(ORDER_REF)).thenReturn(new BaseResponse<>(200, "Order found", order));
        org.mockito.Mockito.lenient().when(paymentRepo.findByPaymentId(RZP_PAYMENT)).thenReturn(Optional.empty());
    }

    private void razorpayPaymentReturns(String status, long amountPaise, String razorpayOrderId) {
        GetRequest get = mock(GetRequest.class);
        @SuppressWarnings("unchecked")
        HttpResponse<JsonNode> response = mock(HttpResponse.class);
        unirest.when(() -> Unirest.get("https://api.razorpay.com/v1/payments/" + RZP_PAYMENT)).thenReturn(get);
        when(get.basicAuth("rzp_test_key", SECRET)).thenReturn(get);
        when(get.asJson()).thenReturn(response);
        when(response.getStatus()).thenReturn(200);
        when(response.getBody()).thenReturn(new JsonNode(new JSONObject()
                .put("status", status)
                .put("amount", amountPaise)
                .put("created_at", 1_700_000_000L)
                .put("order_id", razorpayOrderId)
                .toString()));
    }

    // Stubs POST /v1/orders and returns the JSON object the service sends, filled in once it is called.
    private JSONObject razorpayOrderCreation(int httpStatus) {
        HttpRequestWithBody post = mock(HttpRequestWithBody.class);
        RequestBodyEntity entity = mock(RequestBodyEntity.class);
        @SuppressWarnings("unchecked")
        HttpResponse<JsonNode> response = mock(HttpResponse.class);
        JSONObject captured = new JSONObject();
        unirest.when(() -> Unirest.post("https://api.razorpay.com/v1/orders")).thenReturn(post);
        when(post.basicAuth(anyString(), anyString())).thenReturn(post);
        when(post.header(anyString(), anyString())).thenReturn(post);
        when(post.body(any(JSONObject.class))).thenAnswer(inv -> {
            JSONObject body = inv.getArgument(0);
            body.keySet().forEach(k -> captured.put(k, body.get(k)));
            return entity;
        });
        when(entity.asJson()).thenReturn(response);
        when(response.getStatus()).thenReturn(httpStatus);
        org.mockito.Mockito.lenient().when(response.getBody()).thenReturn(new JsonNode("{\"id\":\"order_new\",\"amount\":194850}"));
        return captured;
    }

    private PaymentReq request(String signature) {
        PaymentReq req = new PaymentReq();
        req.setOrderIdRef(ORDER_REF);
        req.setOrderId(RZP_ORDER);
        req.setPaymentId(RZP_PAYMENT);
        req.setSignature(signature);
        return req;
    }

    private String validSignature() {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal((RZP_ORDER + "|" + RZP_PAYMENT).getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private PaymentAttempt attempt(PaymentAttempt.Status status) {
        PaymentAttempt a = new PaymentAttempt();
        a.setId(7L);
        a.setRazorpayOrderId(RZP_ORDER);
        a.setOrderIdRef(ORDER_REF);
        a.setUserId(BUYER);
        a.setAmountPaise(24950);
        a.setStatus(status);
        return a;
    }

    private Payment storedPayment(Long userId) {
        Payment p = new Payment();
        p.setId(10L);
        p.setPaymentId(RZP_PAYMENT);
        p.setOrderIdRef(ORDER_REF);
        p.setStatus("captured");
        p.setUserId(userId);
        return p;
    }
}
