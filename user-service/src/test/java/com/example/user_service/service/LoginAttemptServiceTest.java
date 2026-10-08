package com.example.user_service.service;

import com.example.user_service.exception.TooManyRequestsException;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LoginAttemptServiceTest {
    private final MutableClock clock = new MutableClock();
    private final LoginAttemptService attempts = new LoginAttemptService(3, Duration.ofMinutes(15), clock);

    @Test
    void anEmailIsBlockedAfterTooManyFailuresUntilTheWindowPasses() {
        fail("a@test.local", 3);

        assertThatThrownBy(() -> attempts.checkAllowed("a@test.local"))
                .isInstanceOf(TooManyRequestsException.class)
                .hasMessageContaining("15 minute")
                .satisfies(e -> assertThat(((TooManyRequestsException) e).getRetryAfterSeconds()).isEqualTo(15 * 60));

        clock.advance(Duration.ofMinutes(15));
        assertThatCode(() -> attempts.checkAllowed("a@test.local")).doesNotThrowAnyException();
    }

    @Test
    void fewerFailuresThanTheLimitAreFine() {
        fail("a@test.local", 2);

        assertThatCode(() -> attempts.checkAllowed("a@test.local")).doesNotThrowAnyException();
    }

    @Test
    void failuresSpreadOverMoreThanOneWindowDoNotAddUp() {
        fail("a@test.local", 2);
        clock.advance(Duration.ofMinutes(16));
        fail("a@test.local", 2);

        assertThatCode(() -> attempts.checkAllowed("a@test.local")).doesNotThrowAnyException();
    }

    @Test
    void aSuccessfulLoginClearsTheCount() {
        fail("a@test.local", 2);
        attempts.recordSuccess("a@test.local");
        fail("a@test.local", 2);

        assertThatCode(() -> attempts.checkAllowed("a@test.local")).doesNotThrowAnyException();
    }

    @Test
    void emailsAreCountedCaseInsensitivelyAndSeparately() {
        fail("A@Test.Local", 3);

        assertThatThrownBy(() -> attempts.checkAllowed(" a@test.local ")).isInstanceOf(TooManyRequestsException.class);
        assertThatCode(() -> attempts.checkAllowed("b@test.local")).doesNotThrowAnyException();
    }

    private void fail(String email, int times) {
        for (int i = 0; i < times; i++) {
            attempts.recordFailure(email);
        }
    }

    private static final class MutableClock extends Clock {
        private Instant now = Instant.parse("2026-10-08T10:00:00Z");

        void advance(Duration d) {
            now = now.plus(d);
        }

        @Override
        public Instant instant() {
            return now;
        }

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }
    }
}
