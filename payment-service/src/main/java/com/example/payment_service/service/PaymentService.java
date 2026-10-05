package com.example.payment_service.service;

import com.example.payment_service.client.OrderServiceClient;
import com.example.payment_service.dto.OrderRes;
import com.example.payment_service.dto.PaymentReq;
import com.example.payment_service.dto.PaymentRes;
import com.example.payment_service.entity.Payment;
import com.example.payment_service.repository.PaymentRepo;
import com.example.payment_service.utils.BaseResponse;
import kong.unirest.HttpResponse;
import kong.unirest.JsonNode;
import kong.unirest.Unirest;
import kong.unirest.json.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.MessageDigest;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;


@Slf4j
@Service
public class PaymentService {
    @Autowired
    private PaymentRepo paymentRepo;
    @Autowired
    private ModelMapper modelMapper;
    @Autowired
    private OrderServiceClient orderServiceClient;
    @Autowired
    private OutboxService outboxService;

    @Value("${razorpay.key}")
    private String key;

    @Value("${razorpay.secret}")
    private String secret;


    // Loaded with the caller's own token, so order-service only returns the order
    // if it belongs to them (or they're an admin).
    private OrderRes getOrderForCaller(String orderIdRef) {
        BaseResponse<OrderRes> res = orderServiceClient.getOrderByOrderId(orderIdRef);
        if (res == null || res.getStatus() != 200 || res.getData() == null) {
            throw new RuntimeException("Order not found or order service unavailable");
        }
        return res.getData();
    }

    @Transactional
    public PaymentRes createPayment(PaymentReq paymentReq) {
        if (paymentReq.getOrderIdRef() == null || paymentReq.getPaymentId() == null) {
            throw new IllegalArgumentException("orderIdRef and paymentId are required");
        }

        // Same Razorpay payment submitted twice (double click, retry): return the stored
        // result instead of recording it again.
        Optional<Payment> existing = paymentRepo.findByPaymentId(paymentReq.getPaymentId());
        if (existing.isPresent()) {
            return toPaymentRes(existing.get());
        }

        // 1. Caller must own the order and it must still be awaiting payment. Anything
        // that fails here is NOT reported to order-service: the order isn't the caller's
        // to fail, and we don't want a stranger flipping someone else's order status.
        OrderRes order = getOrderForCaller(paymentReq.getOrderIdRef());
        if (!"PENDING".equals(order.getStatus()) && !"PAYMENT_FAILED".equals(order.getStatus())) {
            throw new IllegalStateException("Order is not awaiting payment");
        }

        try {
            // 2. Verify signature
            String generatedSignature = hmacSHA256(
                    paymentReq.getOrderId() + "|" + paymentReq.getPaymentId(),
                    secret
            );
            if (!MessageDigest.isEqual(generatedSignature.getBytes(), String.valueOf(paymentReq.getSignature()).getBytes())) {
                throw new RuntimeException("Invalid payment signature");
            }

            // 3. Fetch payment details from Razorpay API
            HttpResponse<JsonNode> response = Unirest.get("https://api.razorpay.com/v1/payments/" + paymentReq.getPaymentId())
                    .basicAuth(key, secret)
                    .asJson();

            if (response.getStatus() != 200) {
                throw new RuntimeException("Failed to fetch payment details from Razorpay");
            }
            JSONObject paymentObj = response.getBody().getObject();
            String status = paymentObj.getString("status");
            long paidPaise = paymentObj.getLong("amount");
            long epochSeconds = paymentObj.getLong("created_at");

            // 4. The money must actually have been taken, for the amount the order is worth,
            // against the Razorpay order this server created for it.
            if (!"captured".equals(status) && !"authorized".equals(status)) {
                throw new RuntimeException("Payment not successful, Razorpay status: " + status);
            }
            if (paidPaise != Math.round(order.getTotalAmount() * 100)) {
                throw new RuntimeException("Paid amount does not match order total");
            }
            if (!paymentReq.getOrderId().equals(paymentObj.optString("order_id"))) {
                throw new RuntimeException("Payment does not belong to the given Razorpay order");
            }

            // 5. Save payment with verified status and date
            Payment payment = new Payment();
            payment.setPaymentId(paymentReq.getPaymentId());
            payment.setOrderId(paymentReq.getOrderId());
            payment.setOrderIdRef(paymentReq.getOrderIdRef());
            payment.setSignature(paymentReq.getSignature());
            payment.setStatus(status);
            payment.setPaymentDate(java.time.Instant.ofEpochSecond(epochSeconds)
                    .atZone(java.time.ZoneId.systemDefault())
                    .toLocalDateTime());
            payment = paymentRepo.save(payment);

            // 6. Saga completion step: record a PAID event in the same transaction as the
            // payment. OutboxPublisher delivers it to order-service via RabbitMQ, retrying
            // until the broker confirms, so it can't be lost if order-service is down.
            outboxService.recordPaid(paymentReq.getOrderIdRef(), payment.getId());

            return toPaymentRes(payment);
        } catch (Exception e) {
            // Saga compensation step: our local transaction failed (bad signature, declined
            // payment, wrong amount...), so tell order-service to mark the order as failed
            // instead of leaving it silently stuck at PENDING forever.
            outboxService.recordFailed(paymentReq.getOrderIdRef());
            throw e;
        }
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


    public JSONObject createRazorpayOrder(int amount, String currency, String receipt) {

        JSONObject orderRequest = new JSONObject();
        orderRequest.put("amount", amount * 100); // amount in paise
        orderRequest.put("currency", currency);
        orderRequest.put("receipt", receipt);

        try {
            HttpResponse<JsonNode> response = Unirest.post("https://api.razorpay.com/v1/orders")
                    .basicAuth(key, secret)
                    .header("Content-Type", "application/json")
                    .body(orderRequest)
                    .asJson();
            if (response.getStatus() == 200 || response.getStatus() == 201) {
                return response.getBody().getObject();
            } else {
                throw new RuntimeException("Failed to create Razorpay order: " + response.getBody().toString());
            }
        } catch (Exception e) {
            throw new RuntimeException("Error creating Razorpay order: " + e.getMessage(), e);
        }
    }

    public List<PaymentRes> getAllPayments() {
        return paymentRepo.findAll().stream().map(this::toPaymentRes).collect(Collectors.toList());
    }

    public PaymentRes getPaymentById(Long id) {
        Payment payment = paymentRepo.findById(id).orElseThrow(() -> new RuntimeException("Payment not found"));
        return toPaymentRes(payment);
    }

    @Transactional
    public PaymentRes updatePayment(Long id, PaymentReq paymentReq) {
        Payment payment = paymentRepo.findById(id).orElseThrow(() -> new RuntimeException("Payment not found"));
        payment.setPaymentId(paymentReq.getPaymentId());
        payment.setOrderId(paymentReq.getOrderId());
        payment.setSignature(paymentReq.getSignature());
        payment.setStatus(paymentReq.getStatus());
        payment.setPaymentDate(paymentReq.getPaymentDate());
        payment = paymentRepo.save(payment);
        return toPaymentRes(payment);
    }

    @Transactional
    public void deletePayment(Long id) {
        Payment payment = paymentRepo.findById(id).orElseThrow(() -> new RuntimeException("Payment not found"));
        paymentRepo.delete(payment);
    }

    private PaymentRes toPaymentRes(Payment payment) {
        PaymentRes res = new PaymentRes();
        res.setId(payment.getId());
        res.setPaymentId(payment.getPaymentId());
        res.setOrderId(payment.getOrderId());
        res.setSignature(payment.getSignature());
        res.setStatus(payment.getStatus());
        res.setPaymentDate(payment.getPaymentDate());
        res.setOrderIdRef(payment.getOrderIdRef());
        return res;
    }
}
