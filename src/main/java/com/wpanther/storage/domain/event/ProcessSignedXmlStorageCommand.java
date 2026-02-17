package com.wpanther.storage.domain.event;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.wpanther.saga.domain.model.IntegrationEvent;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

/**
 * Command received from Saga Orchestrator to store a signed XML document.
 * Consumed from Kafka topic: saga.command.signedxml-storage
 */
@Getter
public class ProcessSignedXmlStorageCommand extends IntegrationEvent {

    private static final long serialVersionUID = 1L;

    @JsonProperty("sagaId")
    private final String sagaId;

    @JsonProperty("sagaStep")
    private final String sagaStep;

    @JsonProperty("correlationId")
    private final String correlationId;

    @JsonProperty("documentId")
    private final String documentId;

    @JsonProperty("invoiceNumber")
    private final String invoiceNumber;

    @JsonProperty("documentType")
    private final String documentType;

    @JsonProperty("signedXmlContent")
    private final String signedXmlContent;

    @JsonProperty("signatureLevel")
    private final String signatureLevel;

    @JsonCreator
    public ProcessSignedXmlStorageCommand(
            @JsonProperty("eventId") UUID eventId,
            @JsonProperty("occurredAt") Instant occurredAt,
            @JsonProperty("eventType") String eventType,
            @JsonProperty("version") int version,
            @JsonProperty("sagaId") String sagaId,
            @JsonProperty("sagaStep") String sagaStep,
            @JsonProperty("correlationId") String correlationId,
            @JsonProperty("documentId") String documentId,
            @JsonProperty("invoiceNumber") String invoiceNumber,
            @JsonProperty("documentType") String documentType,
            @JsonProperty("signedXmlContent") String signedXmlContent,
            @JsonProperty("signatureLevel") String signatureLevel) {
        super(eventId, occurredAt, eventType, version);
        this.sagaId = sagaId;
        this.sagaStep = sagaStep;
        this.correlationId = correlationId;
        this.documentId = documentId;
        this.invoiceNumber = invoiceNumber;
        this.documentType = documentType;
        this.signedXmlContent = signedXmlContent;
        this.signatureLevel = signatureLevel;
    }

    /**
     * Convenience constructor for testing.
     */
    public ProcessSignedXmlStorageCommand(String sagaId, String sagaStep, String correlationId,
                                          String documentId, String invoiceNumber, String documentType,
                                          String signedXmlContent, String signatureLevel) {
        super();
        this.sagaId = sagaId;
        this.sagaStep = sagaStep;
        this.correlationId = correlationId;
        this.documentId = documentId;
        this.invoiceNumber = invoiceNumber;
        this.documentType = documentType;
        this.signedXmlContent = signedXmlContent;
        this.signatureLevel = signatureLevel;
    }
}
