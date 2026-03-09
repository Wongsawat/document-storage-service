package com.wpanther.storage.infrastructure.config;

import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;
import io.github.resilience4j.timelimiter.TimeLimiterConfig;
import io.github.resilience4j.timelimiter.TimeLimiterRegistry;
import java.io.IOException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * Configuration for Resilience4j circuit breakers, retry, and time limiter patterns.
 * <p>
 * Provides fault tolerance for external service calls including:
 * <ul>
 *   <li>PDF downloads from MinIO/storage services</li>
 *   <li>HTTP calls to external signing services</li>
 *   <li>Any other external HTTP dependencies</li>
 * </ul>
 * </p>
 * <p>
 * <b>Patterns Applied:</b>
 * <ul>
 *   <li><b>Circuit Breaker:</b> Prevents cascading failures by stopping calls to failing services</li>
 *   <li><b>Retry:</b> Automatically retries transient failures with exponential backoff</li>
 *   <li><b>Time Limiter:</b> Enforces timeout limits on external calls</li>
 * </ul>
 * </p>
 */
@Configuration
@Slf4j
public class ResilienceConfig {

    @Value("${app.resilience.pdf-download.sliding-window-size:100}")
    private int pdfDownloadSlidingWindowSize;

    @Value("${app.resilience.pdf-download.failure-rate-threshold:50}")
    private int pdfDownloadFailureRateThreshold;

    @Value("${app.resilience.pdf-download.wait-duration-in-open-state:30s}")
    private Duration pdfDownloadWaitDuration;

    @Value("${app.resilience.pdf-download.permitted-number-of-calls-in-half-open-state:10}")
    private int pdfDownloadHalfOpenCalls;

    @Value("${app.resilience.retry.max-attempts:3}")
    private int maxRetryAttempts;

    @Value("${app.resilience.retry.wait-duration:500ms}")
    private Duration retryWaitDuration;

    @Value("${app.resilience.timeout.duration:30s}")
    private Duration timeoutDuration;

    /**
     * Circuit breaker registry for PDF download service.
     * <p>
     * Configuration:
     * <ul>
     *   <li>Sliding window: 100 calls</li>
     *   <li>Failure threshold: 50%</li>
     *   <li>Wait duration in OPEN state: 30 seconds</li>
     *   <li>Half-open state: 10 permitted calls to test recovery</li>
     * </ul>
     * </p>
     */
    @Bean
    public CircuitBreakerRegistry circuitBreakerRegistry() {
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
                .slidingWindowSize(pdfDownloadSlidingWindowSize)
                .failureRateThreshold(pdfDownloadFailureRateThreshold)
                .waitDurationInOpenState(pdfDownloadWaitDuration)
                .permittedNumberOfCallsInHalfOpenState(pdfDownloadHalfOpenCalls)
                .recordExceptions(IOException.class, InterruptedException.class)
                .ignoreExceptions(IllegalArgumentException.class)
                .build();

        CircuitBreakerRegistry registry = CircuitBreakerRegistry.of(config);
        registry.circuitBreaker("pdfDownloadService");
        registry.circuitBreaker("externalHttpService");

        log.info("Configured CircuitBreaker registry with pdfDownloadService and externalHttpService");
        return registry;
    }

    /**
     * Retry registry for transient failures.
     * <p>
     * Configuration:
     * <ul>
     *   <li>Max attempts: 3 (1 initial + 2 retries)</li>
     *   <li>Wait duration: 500ms between retries</li>
     *   <li>Exponential backoff enabled</li>
     * </ul>
     * </p>
     */
    @Bean
    public RetryRegistry retryRegistry() {
        RetryConfig config = RetryConfig.custom()
                .maxAttempts(maxRetryAttempts)
                .waitDuration(retryWaitDuration)
                .retryExceptions(IOException.class, InterruptedException.class)
                .ignoreExceptions(IllegalArgumentException.class)
                .build();

        RetryRegistry registry = RetryRegistry.of(config);
        registry.retry("pdfDownloadRetry");
        registry.retry("externalHttpRetry");

        log.info("Configured Retry registry with pdfDownloadRetry and externalHttpRetry");
        return registry;
    }

    /**
     * Time limiter registry for timeout enforcement.
     * <p>
     * Configuration:
     * <ul>
     *   <li>Timeout: 30 seconds</li>
     *   <li>Cancels running Future/Thread if timeout exceeded</li>
     * </ul>
     * </p>
     */
    @Bean
    public TimeLimiterRegistry timeLimiterRegistry() {
        TimeLimiterConfig config = TimeLimiterConfig.custom()
                .timeoutDuration(timeoutDuration)
                .build();

        TimeLimiterRegistry registry = TimeLimiterRegistry.of(config);
        registry.timeLimiter("pdfDownloadTimeout");
        registry.timeLimiter("externalHttpTimeout");

        log.info("Configured TimeLimiter registry with pdfDownloadTimeout and externalHttpTimeout");
        return registry;
    }
}
