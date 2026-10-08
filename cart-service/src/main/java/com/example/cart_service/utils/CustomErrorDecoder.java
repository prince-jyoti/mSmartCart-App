package com.example.cart_service.utils;

import com.example.cart_service.exception.NotFoundException;
import com.example.cart_service.exception.ServiceUnavailableException;
import com.fasterxml.jackson.databind.ObjectMapper;
import feign.Response;
import feign.codec.ErrorDecoder;

import java.io.InputStream;

// Keeps the meaning of the other service's answer: 400, 404 and 409 pass through as such
// (the fallback factories rethrow them) with that service's message; anything else means
// that service failed.
public class CustomErrorDecoder implements ErrorDecoder {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Override
    public Exception decode(String methodKey, Response response) {
        return switch (response.status()) {
            case 400 -> new IllegalArgumentException(message(response, "Bad request"));
            case 404 -> new NotFoundException(message(response, "Resource not found"));
            case 409 -> new IllegalStateException(message(response, "Conflict"));
            default -> new ServiceUnavailableException(methodKey + " failed with HTTP " + response.status());
        };
    }

    // The "message" of the other service's BaseResponse body, e.g. "Not enough stock for Mug: 2 left".
    private static String message(Response response, String fallback) {
        if (response.body() == null) {
            return fallback;
        }
        try (InputStream body = response.body().asInputStream()) {
            String message = MAPPER.readTree(body).path("message").asText(null);
            return message != null && !message.isBlank() ? message : fallback;
        } catch (Exception e) {
            return fallback;
        }
    }
}
