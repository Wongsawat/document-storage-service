package com.invoice.storage.domain.event;

import com.invoice.storage.infrastructure.messaging.IntegrationEvent;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * Event published when a document is successfully stored.
 *
 * Consumed by notification-service and other downstream services.
 * Extends IntegrationEvent for consistent event metadata across services.
 */
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class DocumentStoredEvent extends IntegrationEvent implements Serializable {

    private static final long serialVersionUID = 1L;

    // Event metadata (inherited from IntegrationEvent):
    // - eventId: Unique identifier for this event
    // - eventType: Will be set to "DOCUMENT_STORED"
    // - occurredAt: Timestamp when the event occurred
    // - version: Event schema version
    // - correlationId: For request tracing across services

    // Document identifiers
    private String documentId;
    private String invoiceId;
    private String invoiceNumber;

    // Storage details
    private String fileName;
    private String storageUrl;
    private long fileSize;
    private String checksum;
    private String documentType;

    // Original signing metadata (from PdfSignedEvent)
    private String signedDocumentId;
    private String signatureLevel;
    private LocalDateTime signatureTimestamp;
}
