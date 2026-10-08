package com.example.user_service.service;

import com.example.user_service.exception.TooManyRequestsException;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;

// Slows down password guessing against one account: after `max-failures` wrong passwords within
// `window`, further attempts for that email are refused until the window has passed. Counted per
// email, so it holds no matter which IPs the guesses come from or whether they bypass the gateway.
// Unknown emails are counted too, so the response doesn't reveal which accounts exist.
// Trade-off: someone who knows an email can block its logins for one window; that beats letting
// guesses through. In memory: per instance, reset on restart.
@Service
public class LoginAttemptService {
    private final int maxFailures;
    private final Duration window;
    private final Clock clock;
    private final Cache<String, Failures> failures;

    @Autowired // two constructors: tell Spring which one is its (the other takes a test clock)
    public LoginAttemptService(@Value("${login.throttle.max-failures:5}") int maxFailures,
                               @Value("${login.throttle.window:PT15M}") Duration window) {
        this(maxFailures, window, Clock.systemUTC());
    }

    LoginAttemptService(int maxFailures, Duration window, Clock clock) {
        this.maxFailures = maxFailures;
        this.window = window;
        this.clock = clock;
        this.failures = Caffeine.newBuilder().maximumSize(100_000).expireAfterWrite(window).build();
    }

    // Throws if this email is currently blocked.
    public void checkAllowed(String email) {
        Failures f = failures.getIfPresent(key(email));
        if (f == null || f.count < maxFailures) {
            return;
        }
        Instant now = clock.instant();
        Instant until = f.firstAt.plus(window);
        if (!now.isBefore(until)) {
            failures.invalidate(key(email)); // window over
            return;
        }
        long wait = Math.max(1, Duration.between(now, until).toSeconds());
        throw new TooManyRequestsException(
                "Too many failed login attempts. Try again in " + ((wait + 59) / 60) + " minute(s).", wait);
    }

    public void recordFailure(String email) {
        Instant now = clock.instant();
        failures.asMap().compute(key(email), (k, f) ->
                f == null || !now.isBefore(f.firstAt.plus(window)) ? new Failures(1, now) : new Failures(f.count + 1, f.firstAt));
    }

    public void recordSuccess(String email) {
        failures.invalidate(key(email));
    }

    private static String key(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }

    private record Failures(int count, Instant firstAt) {
    }
}
