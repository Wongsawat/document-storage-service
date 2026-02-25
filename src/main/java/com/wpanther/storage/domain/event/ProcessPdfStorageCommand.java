package com.wpanther.storage.domain.event;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.wpanther.saga.domain.enums.SagaStep;
import com.wpanther.saga.domain.model.SagaCommand;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

/**
 * Command received from Saga Orchestrator to store an unsigned tax invoice PDF.
 * Consumed from Kafka topic: saga.command.pdf-storage
 *
 * The PDF is downloaded from MinIO (pdfUrl) and stored in document-storage-service.
 * The stored document's URL is included in the SUCCESS reply for the SIGN_PDF step.
 */
@Getter
public class ProcessPdfStorageCommand extends SagaCommand {

    private static final long serialVersionUID = 1L;

    @JsonProperty("documentId")
    private final String documentId;

    @JsonProperty("invoiceNumber")
    private final String invoiceNumber;

    @JsonProperty("documentType")
    private final String documentType;

    @JsonProperty("pdfUrl")
    private final String pdfUrl;

    @JsonProperty("pdfSize")
    private final Long pdfSize;

    @JsonCreator
    public ProcessPdfStorageCommand(
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
            @JsonProperty("pdfUrl") String pdfUrl,
            @JsonProperty("pdfSize") Long pdfSize) {
        super(eventId, occurredAt, eventType, version, sagaId, sagaStep, correlationId);
        this.documentId = documentId;
        this.invoiceNumber = invoiceNumber;
        this.documentType = documentType;
        this.pdfUrl = pdfUrl;
        this.pdfSize = pdfSize;
    }

    /**
     * Convenience constructor for testing.
     */
    public ProcessPdfStorageCommand(String sagaId, SagaStep sagaStep, String correlationId,
                                    String documentId, String invoiceNumber, String documentType,
                                    String pdfUrl, Long pdfSize) {
        super(sagaId, sagaStep, correlationId);
        this.documentId = documentId;
        this.invoiceNumber = invoiceNumber;
        this.documentType = documentType;
        this.pdfUrl = pdfUrl;
        this.pdfSize = pdfSize;
    }
}
