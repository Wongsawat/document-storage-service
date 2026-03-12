package com.wpanther.storage.application.dto.event;

import com.wpanther.saga.domain.enums.SagaStep;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("CompensateSignedXmlStorageCommand Tests")
class CompensateSignedXmlStorageCommandTest {

    @Test
    @DisplayName("Should create command with all required fields")
    void shouldCreateCommandWithAllRequiredFields() {
        String sagaId = "saga-123";
        SagaStep sagaStep = SagaStep.SIGNEDXML_STORAGE;
        String correlationId = "corr-123";
        SagaStep stepToCompensate = SagaStep.SIGNEDXML_STORAGE;
        String documentId = "doc-123";
        String documentType = "SIGNED_XML";

        CompensateSignedXmlStorageCommand command = new CompensateSignedXmlStorageCommand(
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
