package com.example.cart_service.client;

import com.example.cart_service.exception.NotFoundException;
import com.example.cart_service.exception.ServiceUnavailableException;

// Fallbacks never return placeholder data: callers used to accept a fake user or a
// product priced at 0 as real. Instead they rethrow what went wrong, so a 404 from the
// other service stays a 404 and everything else (errors, timeouts, open circuit) is a 503.
final class FallbackErrors {
    private FallbackErrors() {
    }

    static RuntimeException translate(Throwable cause, String notFoundMessage, String unavailableMessage) {
        for (Throwable t = cause; t != null; t = t.getCause()) {
            if (t instanceof NotFoundException) {
                return new NotFoundException(notFoundMessage);
            }
            if (t instanceof IllegalArgumentException e) {
                return e;
            }
            if (t instanceof IllegalStateException e) { // 409, e.g. not enough stock
                return e;
            }
        }
        return new ServiceUnavailableException(unavailableMessage, cause);
    }
}
