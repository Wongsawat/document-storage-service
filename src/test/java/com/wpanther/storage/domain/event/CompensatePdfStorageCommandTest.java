package com.wpanther.storage.domain.event;

import com.wpanther.saga.domain.enums.SagaStep;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("CompensatePdfStorageCommand Tests")
class CompensatePdfStorageCommandTest {

    @Test
    @DisplayName("Should create command with all required fields")
    void shouldCreateCommandWithAllRequiredFields() {
        String sagaId = "saga-123";
        SagaStep sagaStep = SagaStep.PDF_STORAGE;
        String correlationId = "corr-123";
        SagaStep stepToCompensate = SagaStep.PDF_STORAGE;
        String documentId = "doc-123";
        String documentType = "UNSIGNED_PDF";

        CompensatePdfStorageCommand command = new CompensatePdfStorageCommand(
            sagaId, sagaStep, correlationId, stepToCompensate, documentId, documentType
        );

        assertEquals(sagaId, command.getSagaId());
        assertEquals(sagaStep, command.getSagaStep());
        assertEquals(correlationId, command.getCorrelationId());
        assertEquals(stepToCompensate, command.getStepToCompensate());
        assertEquals(documentId, command.getDocumentId());
        assertEquals(documentType, command.getDocumentType());
    }
}
