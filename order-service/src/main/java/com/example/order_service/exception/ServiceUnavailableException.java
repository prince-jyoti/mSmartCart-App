package com.example.order_service.exception;

// Mapped to 503 by GlobalExceptionHandler: a service or API we depend on failed.
public class ServiceUnavailableException extends RuntimeException {
    public ServiceUnavailableException(String message) {
        super(message);
    }

    public ServiceUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
