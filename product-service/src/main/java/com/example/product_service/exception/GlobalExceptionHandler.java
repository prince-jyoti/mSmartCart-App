package com.example.product_service.exception;

import com.example.product_service.utils.BaseResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.TypeMismatchException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.LinkedHashMap;
import java.util.Map;

// Turns exceptions into BaseResponse bodies with a matching HTTP status, so controllers
// just throw. Anything unexpected becomes a 500 with a generic message; details go to the log.
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<Object> notFound(NotFoundException e) {
        return body(HttpStatus.NOT_FOUND, e.getMessage(), null);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Object> badRequest(IllegalArgumentException e) {
        return body(HttpStatus.BAD_REQUEST, e.getMessage(), null);
    }

    // The resource exists but is in the wrong state for this request (already paid, email taken...).
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Object> conflict(IllegalStateException e) {
        return body(HttpStatus.CONFLICT, e.getMessage(), null);
    }

    // @PreAuthorize failures are thrown inside the controller, so they land here, not in the filter chain.
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Object> forbidden(AccessDeniedException e) {
        return body(HttpStatus.FORBIDDEN, "Access denied", null);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Object> unexpected(Exception e) {
        log.error("Unhandled exception", e);
        return body(HttpStatus.INTERNAL_SERVER_ERROR, "Internal server error", null);
    }

    // @Valid failures: 400 with one message per invalid field.
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex, HttpHeaders headers,
                                                                  HttpStatusCode status, WebRequest request) {
        Map<String, String> errors = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(f -> errors.putIfAbsent(f.getField(), f.getDefaultMessage()));
        return body(HttpStatus.BAD_REQUEST, "Validation failed", errors);
    }

    // Every other Spring MVC error (unreadable JSON, missing parameter, wrong method, unknown path...)
    // keeps Spring's status but uses our body format.
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception ex, Object body, HttpHeaders headers,
                                                             HttpStatusCode statusCode, WebRequest request) {
        String message;
        if (ex instanceof HttpMessageNotReadableException) {
            message = "Malformed or unreadable request body";
        } else if (ex instanceof TypeMismatchException tm) {
            message = "Invalid value for '" + tm.getPropertyName() + "'";
        } else if (ex instanceof ErrorResponse er && er.getBody().getDetail() != null) {
            message = er.getBody().getDetail();
        } else {
            HttpStatus known = HttpStatus.resolve(statusCode.value());
            message = known != null ? known.getReasonPhrase() : "Request failed";
        }
        return ResponseEntity.status(statusCode).headers(headers)
                .body(new BaseResponse<>(statusCode.value(), message, null));
    }

    private ResponseEntity<Object> body(HttpStatus status, String message, Object data) {
        return ResponseEntity.status(status).body(new BaseResponse<>(status.value(), message, data));
    }
}
