package com.wpanther.storage.domain.event;

import com.wpanther.saga.domain.enums.SagaStep;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("CompensateDocumentStorageCommand Tests")
class CompensateDocumentStorageCommandTest {

    @Test
    @DisplayName("Should create command with all required fields")
    void shouldCreateCommandWithAllRequiredFields() {
        String sagaId = "saga-123";
        SagaStep sagaStep = SagaStep.STORE_DOCUMENT;
        String correlationId = "corr-123";
        SagaStep stepToCompensate = SagaStep.STORE_DOCUMENT;
        String documentId = "doc-123";
        String documentType = "INVOICE_PDF";

        CompensateDocumentStorageCommand command = new CompensateDocumentStorageCommand(
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
