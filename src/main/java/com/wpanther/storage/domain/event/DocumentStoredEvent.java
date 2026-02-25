package com.wpanther.storage.domain.event;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.wpanther.saga.domain.model.TraceEvent;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

/**
 * Event published when a document is successfully stored.
 * Consumed by notification-service and other downstream services.
 */
@Getter
public class DocumentStoredEvent extends TraceEvent {

    private static final long serialVersionUID = 1L;
    private static final String EVENT_TYPE = "document.stored";
    private static final String SOURCE = "document-storage-service";
    private static final String TRACE_TYPE = "DOCUMENT_STORED";

    @JsonProperty("documentId")
    private final String documentId;

    @JsonProperty("invoiceId")
    private final String invoiceId;

    @JsonProperty("invoiceNumber")
    private final String invoiceNumber;

    @JsonProperty("fileName")
    private final String fileName;

    @JsonProperty("storageUrl")
    private final String storageUrl;

    @JsonProperty("fileSize")
    private final long fileSize;

    @JsonProperty("checksum")
    private final String checksum;

    @JsonProperty("documentType")
    private final String documentType;

    @JsonProperty("correlationId")
    private final String correlationId;

    public DocumentStoredEvent(String documentId, String invoiceId, String invoiceNumber,
                                String fileName, String storageUrl, long fileSize,
                                String checksum, String documentType, String correlationId) {
        super(invoiceId, SOURCE, TRACE_TYPE);
        this.documentId = documentId;
        this.invoiceId = invoiceId;
        this.invoiceNumber = invoiceNumber;
        this.fileName = fileName;
        this.storageUrl = storageUrl;
        this.fileSize = fileSize;
        this.checksum = checksum;
        this.documentType = documentType;
        this.correlationId = correlationId;
    }

    @Override
    public String getEventType() {
        return EVENT_TYPE;
    }

    @JsonCreator
    public DocumentStoredEvent(
            @JsonProperty("eventId") UUID eventId,
            @JsonProperty("occurredAt") Instant occurredAt,
            @JsonProperty("eventType") String eventType,
            @JsonProperty("version") int version,
            @JsonProperty("sagaId") String sagaId,
            @JsonProperty("source") String source,
            @JsonProperty("traceType") String traceType,
            @JsonProperty("context") String context,
            @JsonProperty("documentId") String documentId,
            @JsonProperty("invoiceId") String invoiceId,
            @JsonProperty("invoiceNumber") String invoiceNumber,
            @JsonProperty("fileName") String fileName,
            @JsonProperty("storageUrl") String storageUrl,
            @JsonProperty("fileSize") long fileSize,
            @JsonProperty("checksum") String checksum,
            @JsonProperty("documentType") String documentType,
            @JsonProperty("correlationId") String correlationId) {
        super(eventId, occurredAt, eventType, version, sagaId, source, traceType, context);
        this.documentId = documentId;
        this.invoiceId = invoiceId;
        this.invoiceNumber = invoiceNumber;
        this.fileName = fileName;
        this.storageUrl = storageUrl;
        this.fileSize = fileSize;
        this.checksum = checksum;
        this.documentType = documentType;
        this.correlationId = correlationId;
    }
}
