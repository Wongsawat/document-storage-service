# Circuit Breaker Guide

## Overview

The Document Storage Service uses **Resilience4j** for fault tolerance, protecting against cascading failures when calling external services like MinIO, signing services, and other HTTP endpoints.

## Architecture

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                        Resilience4j Patterns                              │
└─────────────────────────────────────────────────────────────────────────────┘

Request → @Retry → @CircuitBreaker → @TimeLimiter → External Service
              ↓              ↓               ↓
         Retries on       Fails fast on    Enforces
      transient errors   repeated failures  timeout
              ↓              ↓               ↓
         Fallback       Fallback        Fallback
```

## Resilience Patterns

### 1. Circuit Breaker

Prevents cascading failures by stopping calls to failing services:

**States:**
- **CLOSED**: Normal operation, requests pass through
- **OPEN**: Circuit is tripped, requests fail immediately (use fallback)
- **HALF_OPEN**: Testing if service has recovered

**Configuration:**
```yaml
resilience4j.circuitbreaker:
  instances:
    pdfDownloadService:
      sliding-window-size: 100      # Number of calls to evaluate
      failure-rate-threshold: 50   # Open circuit at 50% failure rate
      wait-duration-in-open-state: 30s  # Wait before trying again
```

### 2. Retry

Automatically retries transient failures with exponential backoff:

**Configuration:**
```yaml
resilience4j.retry:
  instances:
    pdfDownloadRetry:
      max-attempts: 3
      wait-duration: 500ms
      exponential-backoff-multiplier: 2
```

### 3. Time Limiter

Enforces timeout limits on external calls:

**Configuration:**
```yaml
resilience4j.timelimiter:
  instances:
    pdfDownloadTimeout:
      timeout-duration: 30s
```

## Protected Services

### PdfDownloadDomainService

```java
@Service
public class PdfDownloadDomainService {

    @CircuitBreaker(name = "pdfDownloadService",
                    fallbackMethod = "downloadPdfFallback")
    @Retry(name = "pdfDownloadRetry",
            fallbackMethod = "downloadPdfRetryFallback")
    public byte[] downloadPdf(String pdfUrl) {
        // Download logic
    }

    private byte[] downloadPdfFallback(String pdfUrl, Exception e) {
        // Circuit breaker is open - fail fast
        throw new StorageFailedException("Service unavailable", e);
    }
}
```

**Protected Calls:**
- PDF downloads from MinIO (`localhost:9000`)
- Content downloads from external URLs
- Any HTTP-based external service calls

## Configuration Properties

### Environment Variables

| Variable | Default | Description |
|----------|---------|-------------|
| `app.resilience.pdf-download.sliding-window-size` | 100 | Circuit breaker evaluation window |
| `app.resilience.pdf-download.failure-rate-threshold` | 50 | Failure rate threshold (percentage) |
| `app.resilience.pdf-download.wait-duration-in-open-state` | 30s | Wait duration in OPEN state |
| `app.resilience.retry.max-attempts` | 3 | Maximum retry attempts |
| `app.resilience.retry.wait-duration` | 500ms | Initial retry wait duration |
| `app.resilience.timeout.duration` | 30s | Timeout for external calls |

### Application Properties

```yaml
# application.yml
resilience4j:
  circuitbreaker:
    instances:
      pdfDownloadService:
        sliding-window-size: 100
        failure-rate-threshold: 50
        wait-duration-in-open-state: 30s

  retry:
    instances:
      pdfDownloadRetry:
        max-attempts: 3
        wait-duration: 500ms

  timelimiter:
    instances:
      pdfDownloadTimeout:
        timeout-duration: 30s
```

## Monitoring Circuit Breakers

### Actuator Endpoints

```bash
# Check circuit breaker status
curl http://localhost:8084/actuator/health

# Get metrics
curl http://localhost:8084/actuator/metrics/resilience4j.circuitbreaker.state
```

### Prometheus Metrics

```
resilience4j_circuitbreaker_state{name="pdfDownloadService"}
resilience4j_circuitbreaker_failure_rate{name="pdfDownloadService"}
resilience4j_retry_calls{name="pdfDownloadRetry",kind="successful"}
resilience4j_timelimiter_calls{name="pdfDownloadTimeout",kind="successful"}
```

## Fallback Strategies

### Graceful Degradation

```java
private byte[] downloadPdfFallback(String pdfUrl, Exception e) {
    log.error("Circuit breaker OPEN, using cached or default PDF");

    // Option 1: Return cached PDF
    byte[] cached = cacheService.getCachedPdf(pdfUrl);
    if (cached != null) {
        return cached;
    }

    // Option 2: Return default PDF
    return getDefaultPdf();

    // Option 3: Fail fast with error
    throw new StorageFailedException("Service unavailable", e);
}
```

### Business Logic Fallbacks

```java
// For saga operations, return failure reply
private PdfStorageReplyEvent pdfDownloadFallback(String pdfUrl, Exception e) {
    return PdfStorageReplyEvent.failure(
        documentId,
        "PDF download service unavailable (circuit breaker open)"
    );
}
```

## Best Practices

### 1. Tune Thresholds Based on SLAs

```yaml
# Critical operations: Faster failure, lower threshold
pdfDownloadService:
  failure-rate-threshold: 30  # Fail at 30% for critical ops

# Non-critical operations: More lenient
cacheRefreshService:
  failure-rate-threshold: 70  # Allow more failures
```

### 2. Separate Circuit Breakers per Dependency

```java
// Good: Separate circuit breakers
@CircuitBreaker(name = "minioDownload")
@CircuitBreaker(name = "signingService")

// Bad: Shared circuit breaker
@CircuitBreaker(name = "externalServices")
```

### 3. Add Circuit Breaker Events for Monitoring

```java
@Component
public class CircuitBreakerEventHandler {

    @EventListener
    public void onCircuitBreakerEvent(CircuitBreakerEvent event) {
        log.info("Circuit breaker event: {} - {}",
            event.getCircuitBreakerName(),
            event.getEventType());

        // Send alerts when circuit opens
        if (event.getEventType() == CircuitBreakerEvent.Type.STATE_TRANSITION) {
            CircuitBreakerStateTransitionEvent transition =
                (CircuitBreakerStateTransitionEvent) event;
            if (transition.getStateTransition().getToState() == CircuitBreaker.State.OPEN) {
                alertService.sendAlert("Circuit breaker opened: " +
                    event.getCircuitBreakerName());
            }
        }
    }
}
```

### 4. Test Circuit Breaker Behavior

```java
@Test
void shouldOpenCircuitBreakerAfterThreshold() {
    // Simulate failures
    for (int i = 0; i < 60; i++) {
        assertThatThrownBy(() -> pdfDownloadService.downloadPdf(badUrl))
            .isInstanceOf(StorageFailedException.class);
    }

    // Circuit should be open
    CircuitBreaker circuitBreaker = circuitBreakerRegistry
        .circuitBreaker("pdfDownloadService");
    assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.OPEN);

    // Subsequent calls should fail fast
    assertThatThrownBy(() -> pdfDownloadService.downloadPdf(anyUrl))
        .hasMessageContaining("circuit breaker open");
}
```

## Troubleshooting

### Circuit Breaker Not Opening

**Symptoms:** Service continues to call failing endpoint

**Solutions:**
1. Check `failure-rate-threshold` is set appropriately
2. Verify `sliding-window-size` is large enough to capture failures
3. Ensure exceptions are in `record-exceptions` list
4. Verify logging shows circuit breaker state transitions

### Retries Not Working

**Symptoms:** No retries occurring on failures

**Solutions:**
1. Check `max-attempts` is greater than 1
2. Verify exceptions are in `retry-exceptions` list
3. Ensure `@Retry` annotation is placed before `@CircuitBreaker`
4. Check fallback method isn't intercepting retries

### Timeouts Not Triggering

**Symptoms:** Calls hang longer than configured timeout

**Solutions:**
1. Verify `@TimeLimiter` annotation is present
2. Check timeout duration is properly formatted (`PT30s`)
3. Ensure method returns `CompletableFuture` or `CompletionStage`
4. For synchronous methods, use `HttpClient.timeout()` instead

## Integration with Saga

### Circuit Breaker in Saga Commands

```java
@SagaCommandHandler
public PdfStorageReplyEvent handleProcessCommand(ProcessPdfStorageCommand command) {
    try {
        byte[] pdf = pdfDownloadService.downloadPdf(command.getPdfUrl());
        // Continue saga
    } catch (StorageFailedException e) {
        // Circuit breaker is open or retries exhausted
        return PdfStorageReplyEvent.failure(
            command.getDocumentId(),
            "PDF download unavailable after retries"
        );
    }
}
```

### Fallback Triggers Compensation

```java
private byte[] downloadPdfFallback(String pdfUrl, Exception e) {
    // Trigger saga compensation
    sagaEventPublisher.publishFailure(
        documentId,
        "PDF download service unavailable",
        e
    );

    throw new StorageFailedException("Service unavailable", e);
}
```

## References

- [Resilience4j Documentation](https://resilience4j.readme.io/)
- [Spring Boot Integration](https://resilience4j.readme.io/docs/getting-started-3)
- [Circuit Breaker Pattern](https://martinfowler.com/bliki/CircuitBreaker.html)
- [Spring Cloud Circuit Breaker](https://cloud.spring.io/spring-cloud-circuitbreaker/)
