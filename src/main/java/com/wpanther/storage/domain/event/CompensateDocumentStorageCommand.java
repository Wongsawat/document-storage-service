package com.wpanther.storage.domain.event;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.wpanther.saga.domain.enums.SagaStep;
import com.wpanther.saga.domain.model.SagaCommand;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

/**
 * Compensation command from Saga Orchestrator to rollback document storage.
 * Consumed from Kafka topic: saga.compensation.document-storage
 */
@Getter
public class CompensateDocumentStorageCommand extends SagaCommand {

    private static final long serialVersionUID = 1L;

    @JsonProperty("stepToCompensate")
    private final SagaStep stepToCompensate;

    @JsonProperty("documentId")
    private final String documentId;

    @JsonProperty("documentType")
    private final String documentType;

    @JsonCreator
    public CompensateDocumentStorageCommand(
            @JsonProperty("eventId") UUID eventId,
            @JsonProperty("occurredAt") Instant occurredAt,
            @JsonProperty("eventType") String eventType,
            @JsonProperty("version") int version,
            @JsonProperty("sagaId") String sagaId,
            @JsonProperty("sagaStep") SagaStep sagaStep,
            @JsonProperty("correlationId") String correlationId,
            @JsonProperty("stepToCompensate") SagaStep stepToCompensate,
            @JsonProperty("documentId") String documentId,
            @JsonProperty("documentType") String documentType) {
        super(eventId, occurredAt, eventType, version, sagaId, sagaStep, correlationId);
        this.stepToCompensate = stepToCompensate;
        this.documentId = documentId;
        this.documentType = documentType;
    }

    /**
     * Convenience constructor for testing.
     */
    public CompensateDocumentStorageCommand(String sagaId, SagaStep sagaStep, String correlationId,
                                             SagaStep stepToCompensate, String documentId,
                                             String documentType) {
        super(sagaId, sagaStep, correlationId);
        this.stepToCompensate = stepToCompensate;
        this.documentId = documentId;
        this.documentType = documentType;
    }
}
