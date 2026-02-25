package com.wpanther.storage.domain.event;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.wpanther.saga.domain.enums.SagaStep;
import com.wpanther.saga.domain.model.SagaCommand;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

/**
 * Command received from Saga Orchestrator to store a signed PDF document.
 * Consumed from Kafka topic: saga.command.document-storage
 */
@Getter
public class ProcessDocumentStorageCommand extends SagaCommand {

    private static final long serialVersionUID = 1L;

    @JsonProperty("documentId")
    private final String documentId;

    @JsonProperty("invoiceNumber")
    private final String invoiceNumber;

    @JsonProperty("documentType")
    private final String documentType;

    @JsonProperty("signedPdfUrl")
    private final String signedPdfUrl;

    @JsonProperty("signedDocumentId")
    private final String signedDocumentId;

    @JsonProperty("signatureLevel")
    private final String signatureLevel;

    @JsonCreator
    public ProcessDocumentStorageCommand(
            @JsonProperty("eventId") UUID eventId,
            @JsonProperty("occurredAt") Instant occurredAt,
            @JsonProperty("eventType") String eventType,
            @JsonProperty("version") int version,
            @JsonProperty("sagaId") String sagaId,
            @JsonProperty("sagaStep") SagaStep sagaStep,
            @JsonProperty("correlationId") String correlationId,
            @JsonProperty("documentId") String documentId,
            @JsonProperty("invoiceNumber") String invoiceNumber,
            @JsonProperty("documentType") String documentType,
            @JsonProperty("signedPdfUrl") String signedPdfUrl,
            @JsonProperty("signedDocumentId") String signedDocumentId,
            @JsonProperty("signatureLevel") String signatureLevel) {
        super(eventId, occurredAt, eventType, version, sagaId, sagaStep, correlationId);
        this.documentId = documentId;
        this.invoiceNumber = invoiceNumber;
        this.documentType = documentType;
        this.signedPdfUrl = signedPdfUrl;
        this.signedDocumentId = signedDocumentId;
        this.signatureLevel = signatureLevel;
    }

    /**
     * Convenience constructor for testing.
     */
    public ProcessDocumentStorageCommand(String sagaId, SagaStep sagaStep, String correlationId,
                                         String documentId, String invoiceNumber, String documentType,
                                         String signedPdfUrl, String signedDocumentId, String signatureLevel) {
        super(sagaId, sagaStep, correlationId);
        this.documentId = documentId;
        this.invoiceNumber = invoiceNumber;
        this.documentType = documentType;
        this.signedPdfUrl = signedPdfUrl;
        this.signedDocumentId = signedDocumentId;
        this.signatureLevel = signatureLevel;
    }
}
