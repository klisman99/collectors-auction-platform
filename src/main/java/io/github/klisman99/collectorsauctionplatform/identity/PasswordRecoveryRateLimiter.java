package io.github.klisman99.collectorsauctionplatform.identity;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

@Component
class PasswordRecoveryRateLimiter {

    private static final int MAXIMUM_ATTEMPTS = 3;
    private static final Duration WINDOW = Duration.ofHours(1);

    private final ConcurrentHashMap<RecoveryAttemptKey, Deque<Instant>> attempts = new ConcurrentHashMap<>();
    private final Clock clock;

    PasswordRecoveryRateLimiter(Clock clock) {
        this.clock = clock;
    }

    void recordAttempt(String clientIp, String normalizedEmail) {
        RecoveryAttemptKey key = new RecoveryAttemptKey(clientIp, normalizedEmail);
        Deque<Instant> window = attempts.computeIfAbsent(key, ignored -> new ArrayDeque<>());
        Instant now = Instant.now(clock);

        synchronized (window) {
            Instant cutoff = now.minus(WINDOW);
            while (!window.isEmpty() && !window.peekFirst().isAfter(cutoff)) {
                window.removeFirst();
            }
            if (window.size() >= MAXIMUM_ATTEMPTS) {
                throw IdentityApiException.passwordRecoveryRateLimited();
            }
            window.addLast(now);
        }
    }

    private record RecoveryAttemptKey(String clientIp, String normalizedEmail) {
    }
}
