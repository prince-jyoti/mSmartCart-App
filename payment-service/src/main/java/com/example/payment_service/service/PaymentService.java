package com.example.payment_service.service;

import java.time.Instant;
import java.math.RoundingMode;
import java.math.BigDecimal;
import kong.unirest.UnirestException;
import com.example.payment_service.exception.ServiceUnavailableException;
import com.example.payment_service.exception.NotFoundException;
import com.example.payment_service.client.OrderServiceClient;
import com.example.payment_service.dto.OrderRes;
import com.example.payment_service.dto.PaymentReq;
import com.example.payment_service.dto.PaymentRes;
import com.example.payment_service.entity.Payment;
import com.example.payment_service.entity.PaymentAttempt;
import com.example.payment_service.repository.PaymentAttemptRepo;
import com.example.payment_service.repository.PaymentRepo;
import com.example.payment_service.utils.BaseResponse;
import kong.unirest.HttpResponse;
import kong.unirest.JsonNode;
import kong.unirest.Unirest;
import kong.unirest.json.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@Service
public class PaymentService {
    @Autowired
    private PaymentRepo paymentRepo;
    @Autowired
    private OrderServiceClient orderServiceClient;
    @Autowired
    private OutboxService outboxService;
    @Autowired
    private PaymentAttemptRepo attemptRepo;

    @Value("${razorpay.key}")
    private String key;

    @Value("${razorpay.secret}")
    private String secret;

    // Loaded with the caller's own token, so order-service only returns the order
    // if it belongs to them (or they're an admin).
    private OrderRes getOrderForCaller(String orderIdRef) {
        BaseResponse<OrderRes> res = orderServiceClient.getOrderByOrderId(orderIdRef);
        if (res == null || res.getData() == null) {
            throw new NotFoundException("Order not found");
        }
        return res.getData();
    }

    // Starting a payment: only orders still open for payment.
    private OrderRes getPayableOrderForCaller(String orderIdRef) {
        return getOrderForCallerInStatus(orderIdRef, "PENDING", "PAYMENT_FAILED");
    }

    // Recording a payment: EXPIRED too, because the customer may finish paying at Razorpay
    // after the order's deadline. They have been charged, so the payment must be recorded;
    // order-service then marks the order PAID and product-service takes the stock again.
    private OrderRes getVerifiableOrderForCaller(String orderIdRef) {
        return getOrderForCallerInStatus(orderIdRef, "PENDING", "PAYMENT_FAILED", "EXPIRED");
    }

    private OrderRes getOrderForCallerInStatus(String orderIdRef, String... allowed) {
        OrderRes order = getOrderForCaller(orderIdRef);
        if (!List.of(allowed).contains(order.getStatus())) {
            throw new IllegalStateException("Order is not awaiting payment");
        }
        return order;
    }

    // Razorpay works in paise: 249.50 -> 24950, exactly (amounts have at most 2 decimals).
    static long toPaise(BigDecimal rupees) {
        return rupees.movePointRight(2).setScale(0, RoundingMode.HALF_UP).longValueExact();
    }

    @Transactional
    public PaymentRes createPayment(PaymentReq paymentReq, Long callerId) {
        if (paymentReq.getOrderIdRef() == null || paymentReq.getPaymentId() == null) {
            throw new IllegalArgumentException("orderIdRef and paymentId are required");
        }

        // Same Razorpay payment submitted twice (double click, retry): return the stored
        // result instead of recording it again.
        Optional<Payment> existing = paymentRepo.findByPaymentId(paymentReq.getPaymentId());
        if (existing.isPresent()) {
            assertOwnerOrAdmin(existing.get(), callerId, false);
            return toPaymentRes(existing.get());
        }

        // 1. Caller must own the order and it must still be awaiting payment. Anything
        // that fails here is NOT reported to order-service: the order isn't the caller's
        // to fail, and we don't want a stranger flipping someone else's order status.
        OrderRes order = getVerifiableOrderForCaller(paymentReq.getOrderIdRef());

        try {
            // 2. Verify signature
            String generatedSignature = hmacSHA256(
                    paymentReq.getOrderId() + "|" + paymentReq.getPaymentId(),
                    secret
            );
            if (!MessageDigest.isEqual(generatedSignature.getBytes(), String.valueOf(paymentReq.getSignature()).getBytes())) {
                throw new IllegalArgumentException("Invalid payment signature");
            }

            // 3. Fetch payment details from Razorpay API
            HttpResponse<JsonNode> response;
            try {
                response = Unirest.get("https://api.razorpay.com/v1/payments/" + paymentReq.getPaymentId())
                        .basicAuth(key, secret)
                        .asJson();
            } catch (UnirestException e) {
                throw new ServiceUnavailableException("Payment provider unavailable", e);
            }

            if (response.getStatus() >= 500 || response.getStatus() == 429) {
                throw new ServiceUnavailableException("Failed to fetch payment details from Razorpay");
            }
            if (response.getStatus() != 200) {
                throw new IllegalArgumentException("Razorpay does not recognise this payment");
            }
            JSONObject paymentObj = response.getBody().getObject();
            String status = paymentObj.getString("status");
            long paidPaise = paymentObj.getLong("amount");
            long epochSeconds = paymentObj.getLong("created_at");

            // 4. The money must actually have been taken, for the amount the order is worth,
            // against the Razorpay order this server created for it.
            if (!"captured".equals(status) && !"authorized".equals(status)) {
                throw new IllegalArgumentException("Payment not successful, Razorpay status: " + status);
            }
            if (paidPaise != toPaise(order.getTotalAmount())) {
                throw new IllegalArgumentException("Paid amount does not match order total");
            }
            if (!paymentReq.getOrderId().equals(paymentObj.optString("order_id"))) {
                throw new IllegalArgumentException("Payment does not belong to the given Razorpay order");
            }

            // 5. Record it and tell order-service
            Payment payment = recordVerified(paymentReq.getPaymentId(), paymentReq.getOrderId(), paymentReq.getOrderIdRef(),
                    callerId, paymentReq.getSignature(), status, epochSeconds);
            return toPaymentRes(payment);
        } catch (ServiceUnavailableException e) {
            // Razorpay couldn't be asked, so we don't know yet whether the customer paid. Don't fail
            // the order: the attempt stays OPEN and PaymentReconciler settles it with Razorpay later.
            throw new ServiceUnavailableException(
                    "Your payment couldn't be confirmed with Razorpay right now. It will be confirmed automatically within a few minutes.", e);
        } catch (Exception e) {
            // Saga compensation step: the payment is definitely not good (bad signature, declined,
            // wrong amount...), so tell order-service to mark the order as failed instead of
            // leaving it silently stuck at PENDING.
            outboxService.recordFailed(paymentReq.getOrderIdRef());
            throw e;
        }
    }

    // A payment PaymentReconciler found at Razorpay (successful, right amount) that was never
    // verified here. Recorded exactly like a verified one; safe if it was recorded meanwhile.
    @Transactional
    public void recordReconciledPayment(Long attemptId, JSONObject razorpayPayment) {
        PaymentAttempt attempt = attemptRepo.findById(attemptId)
                .orElseThrow(() -> new NotFoundException("Payment attempt not found: " + attemptId));
        String paymentId = razorpayPayment.getString("id");
        if (paymentRepo.findByPaymentId(paymentId).isPresent()) {
            attempt.moveTo(PaymentAttempt.Status.PAID);
            attemptRepo.save(attempt);
            return;
        }
        recordVerified(paymentId, attempt.getRazorpayOrderId(), attempt.getOrderIdRef(), attempt.getUserId(),
                null, razorpayPayment.getString("status"), razorpayPayment.getLong("created_at"));
        log.warn("Reconciled payment {} for order {}: paid at Razorpay but never verified here",
                paymentId, attempt.getOrderIdRef());
    }

    @Transactional
    public void abandonAttempt(Long attemptId) {
        attemptRepo.findById(attemptId).ifPresent(a -> {
            a.moveTo(PaymentAttempt.Status.ABANDONED);
            attemptRepo.save(a);
        });
    }

    // Saves the payment, records the PAID event in the same transaction (OutboxPublisher delivers
    // it to order-service via RabbitMQ, so it can't be lost if order-service is down) and closes
    // the Razorpay order's attempt.
    private Payment recordVerified(String paymentId, String razorpayOrderId, String orderIdRef, Long userId,
                                   String signature, String status, long createdAtEpochSeconds) {
        Payment payment = new Payment();
        payment.setPaymentId(paymentId);
        payment.setOrderId(razorpayOrderId);
        payment.setOrderIdRef(orderIdRef);
        payment.setSignature(signature);
        payment.setUserId(userId);
        payment.setStatus(status);
        payment.setPaymentDate(Instant.ofEpochSecond(createdAtEpochSeconds)); // Razorpay sends epoch seconds
        payment = paymentRepo.save(payment);
        outboxService.recordPaid(orderIdRef, payment.getId());
        attemptRepo.findByRazorpayOrderId(razorpayOrderId).ifPresent(a -> {
            a.moveTo(PaymentAttempt.Status.PAID);
            attemptRepo.save(a);
        });
        return payment;
    }

    // Utility method for MAC SHA256
    private String hmacSHA256(String data, String secret) {
        try {
            javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA256");
            javax.crypto.spec.SecretKeySpec secretKeySpec = new javax.crypto.spec.SecretKeySpec(secret.getBytes(), "HmacSHA256");
            mac.init(secretKeySpec);
            byte[] hash = mac.doFinal(data.getBytes());
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate HMAC SHA256", e);
        }
    }

    // The amount comes from the order itself, never from the client, and the caller must
    // own the order. The order id doubles as the receipt (ORD-<uuid> is exactly Razorpay's
    // 40-character limit).
    public JSONObject createRazorpayOrder(String orderIdRef, Long callerId) {
        OrderRes order = getPayableOrderForCaller(orderIdRef);

        JSONObject orderRequest = new JSONObject();
        orderRequest.put("amount", toPaise(order.getTotalAmount()));
        orderRequest.put("currency", "INR");
        orderRequest.put("receipt", orderIdRef);

        HttpResponse<JsonNode> response;
        try {
            response = Unirest.post("https://api.razorpay.com/v1/orders")
                    .basicAuth(key, secret)
                    .header("Content-Type", "application/json")
                    .body(orderRequest)
                    .asJson();
        } catch (UnirestException e) {
            throw new ServiceUnavailableException("Payment provider unavailable", e);
        }
        if (response.getStatus() != 200 && response.getStatus() != 201) {
            // Razorpay's error body stays in the log; the client only learns that it failed.
            log.error("Razorpay order creation failed: HTTP {} {}", response.getStatus(), response.getBody());
            throw new ServiceUnavailableException("Payment provider rejected the order");
        }
        JSONObject razorpayOrder = response.getBody().getObject();

        // Remember it, so a payment against it is found even if it's never verified here.
        LocalDateTime now = LocalDateTime.now();
        PaymentAttempt attempt = new PaymentAttempt();
        attempt.setRazorpayOrderId(razorpayOrder.getString("id"));
        attempt.setOrderIdRef(orderIdRef);
        attempt.setUserId(callerId);
        attempt.setAmountPaise(razorpayOrder.getLong("amount"));
        attempt.setStatus(PaymentAttempt.Status.OPEN);
        attempt.setCreatedAt(now);
        attempt.setUpdatedAt(now);
        attemptRepo.save(attempt);
        return razorpayOrder;
    }

    public List<PaymentRes> getAllPayments() {
        return paymentRepo.findAll().stream().map(this::toPaymentRes).collect(Collectors.toList());
    }

    public PaymentRes getPaymentById(Long id, Long callerId, boolean isAdmin) {
        Payment payment = paymentRepo.findById(id).orElseThrow(() -> new NotFoundException("Payment not found"));
        assertOwnerOrAdmin(payment, callerId, isAdmin);
        return toPaymentRes(payment);
    }

    // Same message as a missing payment, so ids can't be probed to find other users' payments.
    private void assertOwnerOrAdmin(Payment payment, Long callerId, boolean isAdmin) {
        if (!isAdmin && !Objects.equals(payment.getUserId(), callerId)) {
            throw new NotFoundException("Payment not found");
        }
    }

    private PaymentRes toPaymentRes(Payment payment) {
        PaymentRes res = new PaymentRes();
        res.setId(payment.getId());
        res.setPaymentId(payment.getPaymentId());
        res.setOrderId(payment.getOrderId());
        res.setStatus(payment.getStatus());
        res.setPaymentDate(payment.getPaymentDate());
        res.setOrderIdRef(payment.getOrderIdRef());
        return res;
    }
}
