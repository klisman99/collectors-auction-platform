package io.github.klisman99.collectorsauctionplatform.platform.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
class AbsoluteSessionExpiryFilter extends OncePerRequestFilter {

  private static final Duration MAXIMUM_SESSION_AGE = Duration.ofDays(90);

  private final Clock clock;

  AbsoluteSessionExpiryFilter(Clock clock) {
    this.clock = clock;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {

    HttpSession session = request.getSession(false);
    if (session != null && hasReachedAbsoluteExpiry(session)) {
      session.invalidate();
    }

    filterChain.doFilter(request, response);
  }

  private boolean hasReachedAbsoluteExpiry(HttpSession session) {
    Instant createdAt = Instant.ofEpochMilli(session.getCreationTime());
    return !Instant.now(clock).isBefore(createdAt.plus(MAXIMUM_SESSION_AGE));
  }
}
