package com.wpanther.storage.infrastructure.config.health;

import com.wpanther.saga.domain.outbox.OutboxStatus;
import com.wpanther.storage.application.port.out.OutboxRepositoryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

/**
 * Health indicator for outbox pattern monitoring.
 * <p>
 * Provides visibility into outbox event lag by reporting:
 * <ul>
 *   <li>Pending events count</li>
 *   <li>Failed events count</li>
 *   <li>Overall outbox health status</li>
 * </ul>
 * </p>
 * <p>
 * The health check is READ ONLY and does not modify any data.
 * </p>
 *
 * @see <a href="https://docs.spring.io/spring-boot/docs/current/reference/html/actuator.html#actuator.health">Spring Boot Actuator Health</a>
 */
@Component
@RequiredArgsConstructor
public class OutboxHealthIndicator implements HealthIndicator {

    private final OutboxRepositoryPort outboxRepository;

    private static final int BATCH_SIZE = 100;

    @Override
    public Health health() {
        try {
            long pendingCount = countPendingEvents();
            long failedCount = countFailedEvents();

            Health.Builder builder = pendingCount > 0
                    ? Health.unknown()
                    : Health.up();

            return builder
                    .withDetail("pendingEvents", pendingCount)
                    .withDetail("failedEvents", failedCount)
                    .withDetail("status", pendingCount > 0 ? "DEGRADED" : "UP")
                    .build();

        } catch (Exception e) {
            return Health.down()
                    .withDetail("error", e.getMessage())
                    .build();
        }
    }

    private long countPendingEvents() {
        return outboxRepository.findPendingEvents(BATCH_SIZE).size();
    }

    private long countFailedEvents() {
        return outboxRepository.findFailedEvents(BATCH_SIZE).size();
    }
}
