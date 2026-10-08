package com.example.payment_service.razorpay;

import com.example.payment_service.exception.ServiceUnavailableException;
import kong.unirest.HttpResponse;
import kong.unirest.JsonNode;
import kong.unirest.Unirest;
import kong.unirest.UnirestException;
import kong.unirest.json.JSONArray;
import kong.unirest.json.JSONObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

// Server-to-server Razorpay calls used by PaymentReconciler. Kept out of the client package so the
// Feign logging aspect (client.*Client) doesn't treat it as a Feign client. (authenticated with the secret key,
// so no browser signature is needed to trust the answer).
@Component
public class RazorpayClient {
    @Value("${razorpay.key}")
    private String key;

    @Value("${razorpay.secret}")
    private String secret;

    // Every payment made against a Razorpay order (failed attempts included).
    public List<JSONObject> paymentsForOrder(String razorpayOrderId) {
        HttpResponse<JsonNode> response;
        try {
            response = Unirest.get("https://api.razorpay.com/v1/orders/" + razorpayOrderId + "/payments")
                    .basicAuth(key, secret)
                    .asJson();
        } catch (UnirestException e) {
            throw new ServiceUnavailableException("Payment provider unavailable", e);
        }
        if (response.getStatus() >= 500 || response.getStatus() == 429) {
            throw new ServiceUnavailableException("Razorpay returned HTTP " + response.getStatus() + " for order " + razorpayOrderId);
        }
        if (response.getStatus() != 200) {
            // A definite answer (e.g. unknown order), not an outage.
            throw new IllegalArgumentException("Razorpay returned HTTP " + response.getStatus() + " for order " + razorpayOrderId);
        }
        JSONArray items = response.getBody().getObject().optJSONArray("items");
        List<JSONObject> payments = new ArrayList<>();
        for (int i = 0; items != null && i < items.length(); i++) {
            payments.add(items.getJSONObject(i));
        }
        return payments;
    }
}
