package com.example.order_service.client;

import com.example.order_service.exception.NotFoundException;
import com.example.order_service.exception.ServiceUnavailableException;
import feign.RetryableException;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

// Fallbacks must never hand back placeholder data; they rethrow with the right meaning.
class FallbackErrorsTest {
    private final ProductServiceFallbackFactory products = new ProductServiceFallbackFactory();

    @Test
    void notFoundFromTheOtherServiceStaysNotFound() {
        assertThatThrownBy(() -> products.create(new NotFoundException("Resource not found")).getById(1L))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Product not found");
    }

    @Test
    void notFoundIsRecognisedEvenWhenWrapped() {
        Throwable wrapped = new RuntimeException("wrapper", new NotFoundException("Resource not found"));

        assertThatThrownBy(() -> products.create(wrapped).getById(1L)).isInstanceOf(NotFoundException.class);
    }

    @Test
    void badRequestPassesThrough() {
        IllegalArgumentException cause = new IllegalArgumentException("Bad request");

        assertThatThrownBy(() -> products.create(cause).getById(1L)).isSameAs(cause);
    }

    @Test
    void conflictPassesThroughWithItsMessage() {
        IllegalStateException cause = new IllegalStateException("Not enough stock for Mug: 2 left");

        assertThatThrownBy(() -> products.create(cause).reserve(null)).isSameAs(cause);
    }

    @Test
    void connectionFailuresAndAnOpenCircuitBecomeUnavailable() {
        Throwable refused = mock(RetryableException.class);
        Throwable open = CallNotPermittedException.createCallNotPermittedException(CircuitBreaker.ofDefaults("product-service"));

        for (Throwable cause : new Throwable[]{refused, open, new ServiceUnavailableException("HTTP 500")}) {
            assertThatThrownBy(() -> products.create(cause).getById(1L))
                    .isInstanceOf(ServiceUnavailableException.class)
                    .hasMessage("Product service unavailable");
        }
    }

    @Test
    void everyMethodOfAMultiMethodClientThrows() {
        UserServiceClient users = new UserServiceFallbackFactory().create(new RuntimeException("timeout"));

        assertThatThrownBy(users::getCurrentUser).isInstanceOf(ServiceUnavailableException.class);
        assertThatThrownBy(() -> users.getUserById(1L)).isInstanceOf(ServiceUnavailableException.class);
        assertThat(new PaymentServiceFallbackFactory().create(new NotFoundException("x"))).isNotNull();
    }
}
