package com.example.seat_booking_system.ratelimit;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RRateLimiter;
import org.redisson.api.RateIntervalUnit;
import org.redisson.api.RateType;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.lang.NonNull;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@ConditionalOnProperty(name = "spring.cache.type", havingValue = "redis", matchIfMissing = true)
@RequiredArgsConstructor
@Slf4j
public class RateLimiterFilter extends OncePerRequestFilter {

  private final RedissonClient redissonClient;

  @Value("${app.rate-limit.requests-per-second:5}")
  private int requestsPerSecond;

  @Override
  protected void doFilterInternal(
      @NonNull HttpServletRequest request,
      @NonNull HttpServletResponse response,
      @NonNull FilterChain filterChain)
      throws ServletException, IOException {

    if (!request.getRequestURI().startsWith("/api/bookings")) {
      filterChain.doFilter(request, response);
      return;
    }

    String clientKey = resolveClientKey(request);
    String rateLimiterKey = "rate:limit:" + clientKey;

    RRateLimiter rateLimiter = redissonClient.getRateLimiter(rateLimiterKey);

    rateLimiter.trySetRate(RateType.OVERALL, requestsPerSecond, 1, RateIntervalUnit.SECONDS);

    if (!rateLimiter.tryAcquire()) {
      log.warn(
          "[RATE LIMIT] User '{}' exceeded rate limit ({} req/s) — returning 429",
          clientKey,
          requestsPerSecond);

      response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
      response.setContentType("application/json");

      response.setHeader("Retry-After", "1");
      response.getWriter().write("{\"error\":\"Rate limit exceeded\",\"retryAfter\":\"1s\"}");
      return;
    }

    filterChain.doFilter(request, response);
  }

  private String resolveClientKey(HttpServletRequest request) {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth != null && auth.isAuthenticated() && !"anonymousUser".equals(auth.getPrincipal())) {
      return "user:" + auth.getName();
    }

    String ip = request.getHeader("X-Forwarded-For");
    if (ip == null || ip.isEmpty()) {
      ip = request.getRemoteAddr();
    }
    return "ip:" + ip;
  }
}
