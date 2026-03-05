package com.wpanther.storage.domain.event;

import com.wpanther.saga.domain.enums.SagaStep;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ProcessSignedXmlStorageCommand Tests")
class ProcessSignedXmlStorageCommandTest {

    @Test
    @DisplayName("Should create command with all required fields")
    void shouldCreateCommandWithAllRequiredFields() {
        String sagaId = "saga-123";
        SagaStep sagaStep = SagaStep.SIGNEDXML_STORAGE;
        String correlationId = "corr-123";
        String documentId = "doc-123";
        String invoiceNumber = "INV-2024-001";
        String documentType = "TAX_INVOICE_XML";
        String signedXmlUrl = "http://localhost:8084/api/v1/documents/signed-abc123.xml";
        String signatureLevel = "XAdES-BASELINE-T";

        ProcessSignedXmlStorageCommand command = new ProcessSignedXmlStorageCommand(
            sagaId, sagaStep, correlationId, documentId, invoiceNumber, documentType, signedXmlUrl, signatureLevel
        );

        assertEquals(sagaId, command.getSagaId());
        assertEquals(sagaStep, command.getSagaStep());
        assertEquals(correlationId, command.getCorrelationId());
        assertEquals(documentId, command.getDocumentId());
        assertEquals(invoiceNumber, command.getInvoiceNumber());
        assertEquals(documentType, command.getDocumentType());
        assertEquals(signedXmlUrl, command.getSignedXmlUrl());
        assertEquals(signatureLevel, command.getSignatureLevel());
    }
}
