package com.wpanther.storage.application.dto.event;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.wpanther.saga.domain.enums.SagaStep;
import com.wpanther.saga.domain.model.SagaCommand;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

/**
 * Compensation command from Saga Orchestrator to rollback PDF storage.
 * Consumed from Kafka topic: saga.compensation.pdf-storage
 */
@Getter
public class CompensatePdfStorageCommand extends SagaCommand {

    private static final long serialVersionUID = 1L;

    @JsonProperty("stepToCompensate")
    private final SagaStep stepToCompensate;

    @JsonProperty("documentId")
    private final String documentId;

    @JsonProperty("documentType")
    private final String documentType;

    @JsonCreator
    public CompensatePdfStorageCommand(
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
    public CompensatePdfStorageCommand(String sagaId, SagaStep sagaStep, String correlationId,
                                       SagaStep stepToCompensate, String documentId,
                                       String documentType) {
        super(sagaId, sagaStep, correlationId);
        this.stepToCompensate = stepToCompensate;
        this.documentId = documentId;
        this.documentType = documentType;
    }
}
