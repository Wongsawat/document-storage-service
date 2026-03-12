package com.wpanther.storage.infrastructure.config.metrics;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration for custom business metrics.
 * <p>
 * This configuration sets up Micrometer metrics for the document storage service,
 * enabling observability of business operations beyond standard JVM metrics.
 * </p>
 * <p>
 * <b>Custom Metrics:</b>
 * <ul>
 *   <li>Document storage counters (by type)</li>
 *   <li>Storage operation timers with percentiles</li>
 *   <li>PDF download success/failure rates</li>
 *   <li>Orphaned document detection gauges</li>
 * </ul>
 * </p>
 *
 * @see DocumentStorageMetricsService
 */
@Configuration
public class MetricsConfig {

    /**
     * Create the document storage metrics service bean.
     *
     * @param meterRegistry the Micrometer meter registry
     * @return DocumentStorageMetricsService instance
     */
    @Bean
    public DocumentStorageMetricsService documentStorageMetricsService(MeterRegistry meterRegistry) {
        return new DocumentStorageMetricsService(meterRegistry);
    }
}
