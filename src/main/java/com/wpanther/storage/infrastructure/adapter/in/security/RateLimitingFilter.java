package com.wpanther.storage.infrastructure.adapter.in.security;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Rate limiting filter to prevent brute force attacks on authentication endpoints.
 * <p>
 * Uses a sliding window algorithm with Caffeine cache for efficient request tracking.
 * Blocks requests from IPs that exceed the configured rate limit for a specified duration.
 * <p>
 * Configuration:
 * - maxAttempts: Maximum number of requests allowed within the time window (default: 5)
 * - windowSeconds: Time window in seconds (default: 60)
 * - blockDurationSeconds: How long to block the IP after exceeding limits (default: 300 = 5 minutes)
 * <p>
 * Applied to: /api/v1/auth/login, /api/v1/auth/token, /api/v1/auth/refresh
 */
@Component
@Slf4j
public class RateLimitingFilter extends OncePerRequestFilter {

    private static final String RATE_LIMIT_EXCEEDED = "Rate limit exceeded. Please try again later.";
    private static final String RETRY_AFTER_HEADER = "Retry-After";

    private final Cache<String, RequestTracker> requestTrackers;
    private final Cache<String, Instant> blockedIps;

    private final int maxAttempts;
    private final long windowSeconds;
    private final long blockDurationSeconds;
    private final List<String> protectedPaths;

    /**
     * Initialize rate limiting filter with configurable parameters.
     *
     * @param maxAttempts          Maximum requests per time window (default: 5)
     * @param windowSeconds        Time window in seconds (default: 60)
     * @param blockDurationSeconds Block duration in seconds (default: 300)
     */
    public RateLimitingFilter(
            @Value("${app.security.rate-limit.max-attempts:5}") int maxAttempts,
            @Value("${app.security.rate-limit.window-seconds:60}") long windowSeconds,
            @Value("${app.security.rate-limit.block-duration-seconds:300}") long blockDurationSeconds
    ) {
        this.maxAttempts = maxAttempts;
        this.windowSeconds = windowSeconds;
        this.blockDurationSeconds = blockDurationSeconds;
        this.protectedPaths = List.of("/api/v1/auth/login", "/api/v1/auth/token", "/api/v1/auth/refresh");

        // Cache for tracking request counts (expires after window duration)
        this.requestTrackers = Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofSeconds(windowSeconds))
                .maximumSize(10000)
                .build();

        // Cache for blocked IPs (expires after block duration)
        this.blockedIps = Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofSeconds(blockDurationSeconds))
                .maximumSize(10000)
                .build();

        log.info("RateLimitingFilter initialized: maxAttempts={}, windowSeconds={}, blockDurationSeconds={}",
                maxAttempts, windowSeconds, blockDurationSeconds);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String requestPath = request.getRequestURI();
        String clientIp = getClientIp(request);

        // Only apply rate limiting to authentication endpoints
        if (!shouldApplyRateLimit(requestPath)) {
            filterChain.doFilter(request, response);
            return;
        }

        // Check if IP is blocked
        Instant blockedUntil = blockedIps.getIfPresent(clientIp);
        if (blockedUntil != null && Instant.now().isBefore(blockedUntil)) {
            log.warn("Blocked IP attempted request: {}", clientIp);
            sendRateLimitResponse(response, blockDurationSeconds);
            return;
        }

        // Track and check request count
        RequestTracker tracker = requestTrackers.get(clientIp, k -> new RequestTracker());
        synchronized (tracker) {
            tracker.incrementCount();

            if (tracker.getCount() > maxAttempts) {
                // Block the IP
                Instant blockUntil = Instant.now().plusSeconds(blockDurationSeconds);
                blockedIps.put(clientIp, blockUntil);
                requestTrackers.invalidate(clientIp);

                log.warn("IP {} exceeded rate limit ({} requests in {}s), blocked until {}",
                        clientIp, tracker.getCount(), windowSeconds, blockUntil);
                sendRateLimitResponse(response, blockDurationSeconds);
                return;
            }
        }

        filterChain.doFilter(request, response);
    }

    /**
     * Check if the request path should have rate limiting applied.
     */
    private boolean shouldApplyRateLimit(String requestPath) {
        return protectedPaths.stream().anyMatch(requestPath::startsWith);
    }

    /**
     * Get client IP address from request, accounting for proxies.
     */
    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty()) {
            ip = request.getHeader("X-Real-IP");
        }
        if (ip == null || ip.isEmpty()) {
            ip = request.getRemoteAddr();
        }
        // Handle multiple IPs in X-Forwarded-For (take the first one)
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        return ip != null ? ip : "unknown";
    }

    /**
     * Send rate limit exceeded response.
     */
    private void sendRateLimitResponse(HttpServletResponse response, long retryAfter) {
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setContentType("application/json");
        response.setHeader(RETRY_AFTER_HEADER, String.valueOf(retryAfter));
        try {
            response.getWriter().write(String.format("{\"error\": \"%s\", \"retryAfter\": %d}",
                    RATE_LIMIT_EXCEEDED, retryAfter));
        } catch (IOException e) {
            throw new ServletException("Failed to send rate limit response", e);
        }
    }

    /**
     * Clear all rate limit tracking.
     * <p>
     * Useful for testing or administrative reset.
     */
    public void clear() {
        log.warn("Clearing all rate limit tracking");
        requestTrackers.invalidateAll();
        blockedIps.invalidateAll();
    }

    /**
     * Internal class to track request counts for an IP.
     */
    private static class RequestTracker {
        private int count = 0;
        private final Instant firstRequest = Instant.now();

        public void incrementCount() {
            count++;
        }

        public int getCount() {
            return count;
        }

        public Instant getFirstRequest() {
            return firstRequest;
        }
    }
}
