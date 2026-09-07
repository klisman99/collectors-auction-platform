package io.github.klisman99.collectorsauctionplatform.identity;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

@Component
class IdentityAttemptRateLimiter {

  private final ConcurrentHashMap<AttemptKey, Deque<Instant>> attempts = new ConcurrentHashMap<>();
  private final Clock clock;

  IdentityAttemptRateLimiter(Clock clock) {
    this.clock = clock;
  }

  void recordLoginAttempt(String clientIp, String normalizedEmail) {
    recordAttempt(AttemptType.LOGIN, clientIp, normalizedEmail);
  }

  void recordPasswordRecoveryAttempt(String clientIp, String normalizedEmail) {
    recordAttempt(AttemptType.PASSWORD_RECOVERY, clientIp, normalizedEmail);
  }

  private void recordAttempt(AttemptType type, String clientIp, String normalizedEmail) {
    AttemptKey key = new AttemptKey(type, clientIp, normalizedEmail);
    Deque<Instant> window = attempts.computeIfAbsent(key, ignored -> new ArrayDeque<>());
    Instant now = Instant.now(clock);

    synchronized (window) {
      Instant cutoff = now.minus(type.window());
      while (!window.isEmpty() && !window.peekFirst().isAfter(cutoff)) {
        window.removeFirst();
      }
      if (window.size() >= type.maximumAttempts()) {
        throw type.rateLimitExceeded();
      }
      window.addLast(now);
    }
  }

  private record AttemptKey(AttemptType type, String clientIp, String normalizedEmail) {}

  private enum AttemptType {
    LOGIN(5, Duration.ofMinutes(1)) {
      @Override
      IdentityApiException rateLimitExceeded() {
        return IdentityApiException.loginRateLimited();
      }
    },
    PASSWORD_RECOVERY(3, Duration.ofHours(1)) {
      @Override
      IdentityApiException rateLimitExceeded() {
        return IdentityApiException.passwordRecoveryRateLimited();
      }
    };

    private final int maximumAttempts;
    private final Duration window;

    AttemptType(int maximumAttempts, Duration window) {
      this.maximumAttempts = maximumAttempts;
      this.window = window;
    }

    int maximumAttempts() {
      return maximumAttempts;
    }

    Duration window() {
      return window;
    }

    abstract IdentityApiException rateLimitExceeded();
  }
}
