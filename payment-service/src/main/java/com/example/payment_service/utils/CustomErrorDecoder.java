package com.example.payment_service.utils;

import feign.Response;
import feign.codec.ErrorDecoder;

public class CustomErrorDecoder implements ErrorDecoder {
    @Override
    public Exception decode(String methodKey, Response response) {
        switch (response.status()) {
            case 400:
                return new IllegalArgumentException("Bad Request");
            case 404:
                return new RuntimeException("Resource Not Found");
            case 500:
                return new RuntimeException("Internal Server Error");
            default:
                return new Exception("Generic Error");
        }
    }
}
