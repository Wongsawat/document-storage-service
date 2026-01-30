package com.invoice.storage.infrastructure.messaging;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.time.LocalDateTime;

/**
 * Base class for all integration events published to Kafka.
 *
 * Provides common fields for event identification and tracing.
 * Follows the same pattern as pdf-signing-service for event consistency.
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public abstract class IntegrationEvent {

    /**
     * Unique identifier for this event
     */
    private String eventId;

    /**
     * Type of event (e.g., "PdfGenerated", "PdfSigned", "DOCUMENT_STORED")
     */
    private String eventType;

    /**
     * Timestamp when the event occurred
     */
    private LocalDateTime occurredAt;

    /**
     * Event schema version
     */
    private String version;

    /**
     * Correlation ID for request tracing across services
     */
    private String correlationId;
}
