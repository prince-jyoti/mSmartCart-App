package com.example.payment_service.exception;

// Mapped to 404 by GlobalExceptionHandler. Also used when the caller may not see a
// resource, so an id that exists but isn't theirs looks the same as one that doesn't.
public class NotFoundException extends RuntimeException {
    public NotFoundException(String message) {
        super(message);
    }
}
