package com.example.payment_service.service;

import com.example.payment_service.client.OrderServiceClient;
import com.example.payment_service.client.UserServiceClient;
import com.example.payment_service.dto.OrderRes;
import com.example.payment_service.dto.PaymentReq;
import com.example.payment_service.dto.PaymentRes;
import com.example.payment_service.dto.UserDTO;
import com.example.payment_service.entity.Payment;
import com.example.payment_service.repository.PaymentRepo;
import com.example.payment_service.utils.BaseResponse;
import kong.unirest.HttpResponse;
import kong.unirest.JsonNode;
import kong.unirest.Unirest;
import kong.unirest.json.JSONObject;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;


@Service
public class PaymentService {
    @Autowired
    private PaymentRepo paymentRepo;
    @Autowired
    private ModelMapper modelMapper;
    @Autowired
    private OrderServiceClient orderServiceClient;
    @Autowired
    private UserServiceClient userServiceClient;

    @Value("${razorpay.key}")
    private String key;

    @Value("${razorpay.secret}")
    private String secret;


//These feign clients are not used because PammentRes DTO does not direct reference to User or Order details
    private UserDTO getUserDTO() {
        BaseResponse<UserDTO> userResponse = userServiceClient.createOrUpdate();
        if (userResponse == null || userResponse.getData() == null) {
            throw new RuntimeException("User not found");
        }
        return userResponse.getData();
    }

    private OrderRes getOrderDTO(Long orderIdRef) {
        BaseResponse<OrderRes> productResponse = orderServiceClient.getOrderById(orderIdRef);
        if (productResponse == null || productResponse.getData() == null) {
            throw new RuntimeException("Product not found");
        }
        return productResponse.getData();
    }


    @Transactional
    public PaymentRes createPayment(PaymentReq paymentReq) {
        // 1. Verify signature
        String generatedSignature = hmacSHA256(
                paymentReq.getOrderId() + "|" + paymentReq.getPaymentId(),
                secret
        );
        if (!generatedSignature.equals(paymentReq.getSignature())) {
            throw new RuntimeException("Invalid payment signature");
        }

        // 2. Fetch payment details from Razorpay API
        HttpResponse<JsonNode> response = Unirest.get("https://api.razorpay.com/v1/payments/" + paymentReq.getPaymentId())
                .basicAuth(key, secret)
                .asJson();

        if (response.getStatus() != 200) {
            throw new RuntimeException("Failed to fetch payment details from Razorpay");
        }
        JSONObject paymentObj = response.getBody().getObject();
        String status = paymentObj.getString("status");
        String paymentDate = paymentObj.getString("created_at"); // epoch seconds

        // 3. Save payment with verified status and date
        Payment payment = new Payment();
        payment.setPaymentId(paymentReq.getPaymentId());
        payment.setOrderId(paymentReq.getOrderId());
        payment.setSignature(paymentReq.getSignature());
        payment.setStatus(status);
        long epochSeconds = Long.parseLong(paymentDate);
        LocalDateTime dateTime = java.time.Instant.ofEpochSecond(epochSeconds)
                .atZone(java.time.ZoneId.systemDefault())
                .toLocalDateTime();
        payment.setPaymentDate(dateTime);
//        if (paymentReq.getOrderIdRef() != null) {
//            Order order = orderRepo.findByOrderId(String.valueOf(paymentReq.getOrderIdRef()))
//                    .orElseThrow(() -> new RuntimeException("Order not found"));
//            payment.setOrder(order);
//        }
        payment = paymentRepo.save(payment);
        return toPaymentRes(payment);
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
        res.setOrderIdRef(String.valueOf(payment.getOrderIdRef())); // Default to null
        return res;
    }
}
