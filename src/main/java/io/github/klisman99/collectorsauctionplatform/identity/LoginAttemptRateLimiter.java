package io.github.klisman99.collectorsauctionplatform.identity;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

@Component
class LoginAttemptRateLimiter {

    private static final int MAXIMUM_ATTEMPTS = 5;
    private static final Duration WINDOW = Duration.ofMinutes(1);

    private final ConcurrentHashMap<LoginAttemptKey, Deque<Instant>> attempts = new ConcurrentHashMap<>();
    private final Clock clock;

    LoginAttemptRateLimiter(Clock clock) {
        this.clock = clock;
    }

    void recordAttempt(String clientIp, String normalizedEmail) {
        LoginAttemptKey key = new LoginAttemptKey(clientIp, normalizedEmail);
        Deque<Instant> window = attempts.computeIfAbsent(key, ignored -> new ArrayDeque<>());
        Instant now = Instant.now(clock);

        synchronized (window) {
            Instant cutoff = now.minus(WINDOW);
            while (!window.isEmpty() && !window.peekFirst().isAfter(cutoff)) {
                window.removeFirst();
            }
            if (window.size() >= MAXIMUM_ATTEMPTS) {
                throw IdentityApiException.loginRateLimited();
            }
            window.addLast(now);
        }
    }

    private record LoginAttemptKey(String clientIp, String normalizedEmail) {
    }
}
