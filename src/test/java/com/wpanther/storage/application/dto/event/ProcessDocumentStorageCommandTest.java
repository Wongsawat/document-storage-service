package com.wpanther.storage.application.dto.event;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.wpanther.saga.domain.enums.SagaStep;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ProcessDocumentStorageCommand Tests")
class ProcessDocumentStorageCommandTest {

    private static final ObjectMapper objectMapper = JsonMapper.builder()
        .addModule(new JavaTimeModule())
        .build();

    @Nested
    @DisplayName("Convenience constructor")
    class ConvenienceConstructorTests {

        @Test
        @DisplayName("Should create command with all fields")
        void shouldCreateCommandWithAllFields() {
            String sagaId = "saga-123";
            SagaStep sagaStep = SagaStep.STORE_DOCUMENT;
            String correlationId = "corr-123";
            String documentId = "doc-123";
            String invoiceNumber = "INV-2024-001";
            String documentType = "INVOICE_PDF";
            String signedPdfUrl = "http://minio:9000/taxinvoices/abc123.pdf";
            String signedDocumentId = "signed-doc-123";
            String signatureLevel = "PAdES-BASELINE-T";

            ProcessDocumentStorageCommand command = new ProcessDocumentStorageCommand(
                sagaId, sagaStep, correlationId, documentId, invoiceNumber,
                documentType, signedPdfUrl, signedDocumentId, signatureLevel
            );

            assertEquals(sagaId, command.getSagaId());
            assertEquals(sagaStep, command.getSagaStep());
            assertEquals(correlationId, command.getCorrelationId());
            assertEquals(documentId, command.getDocumentId());
            assertEquals(invoiceNumber, command.getInvoiceNumber());
            assertEquals(documentType, command.getDocumentType());
            assertEquals(signedPdfUrl, command.getSignedPdfUrl());
            assertEquals(signedDocumentId, command.getSignedDocumentId());
            assertEquals(signatureLevel, command.getSignatureLevel());
        }

        @Test
        @DisplayName("Should handle null values in optional fields")
        void shouldHandleNullValuesInOptionalFields() {
            ProcessDocumentStorageCommand command = new ProcessDocumentStorageCommand(
                "saga-123", SagaStep.STORE_DOCUMENT, "corr-123",
                "doc-123", null, "INVOICE_PDF",
                "http://minio:9000/file.pdf", "signed-doc-123", null
            );

            assertNull(command.getInvoiceNumber());
            assertNull(command.getSignatureLevel());
            assertNotNull(command.getDocumentId());
            assertNotNull(command.getDocumentType());
        }
    }

    @Nested
    @DisplayName("JSON serialization")
    class JsonSerializationTests {

        @Test
        @DisplayName("Should serialize to JSON correctly")
        void shouldSerializeToJsonCorrectly() throws JsonProcessingException {
            ProcessDocumentStorageCommand command = new ProcessDocumentStorageCommand(
                "saga-123", SagaStep.STORE_DOCUMENT, "corr-123",
                "doc-123", "INV-2024-001", "INVOICE_PDF",
                "http://minio:9000/file.pdf", "signed-doc-123", "PAdES-BASELINE-T"
            );

            String json = objectMapper.writeValueAsString(command);

            assertTrue(json.contains("\"sagaId\":\"saga-123\""));
            assertTrue(json.contains("\"sagaStep\":\"store-document\""));
            assertTrue(json.contains("\"correlationId\":\"corr-123\""));
            assertTrue(json.contains("\"documentId\":\"doc-123\""));
            assertTrue(json.contains("\"invoiceNumber\":\"INV-2024-001\""));
            assertTrue(json.contains("\"documentType\":\"INVOICE_PDF\""));
            assertTrue(json.contains("\"signedPdfUrl\":\"http://minio:9000/file.pdf\""));
            assertTrue(json.contains("\"signedDocumentId\":\"signed-doc-123\""));
            assertTrue(json.contains("\"signatureLevel\":\"PAdES-BASELINE-T\""));
        }

        @Test
        @DisplayName("Should deserialize from JSON correctly")
        void shouldDeserializeFromJsonCorrectly() throws JsonProcessingException {
            String json = """
                {
                    "eventId": "550e8400-e29b-41d4-a716-446655440000",
                    "occurredAt": "2024-03-05T10:00:00Z",
                    "eventType": "ProcessDocumentStorageCommand",
                    "version": 1,
                    "sagaId": "saga-123",
                    "sagaStep": "store-document",
                    "correlationId": "corr-123",
                    "documentId": "doc-123",
                    "invoiceNumber": "INV-2024-001",
                    "documentType": "INVOICE_PDF",
                    "signedPdfUrl": "http://minio:9000/file.pdf",
                    "signedDocumentId": "signed-doc-123",
                    "signatureLevel": "PAdES-BASELINE-T"
                }
                """;

            ProcessDocumentStorageCommand command = objectMapper.readValue(json, ProcessDocumentStorageCommand.class);

            assertEquals("saga-123", command.getSagaId());
            assertEquals(SagaStep.STORE_DOCUMENT, command.getSagaStep());
            assertEquals("corr-123", command.getCorrelationId());
            assertEquals("doc-123", command.getDocumentId());
            assertEquals("INV-2024-001", command.getInvoiceNumber());
            assertEquals("INVOICE_PDF", command.getDocumentType());
            assertEquals("http://minio:9000/file.pdf", command.getSignedPdfUrl());
            assertEquals("signed-doc-123", command.getSignedDocumentId());
            assertEquals("PAdES-BASELINE-T", command.getSignatureLevel());
        }
    }

    @Nested
    @DisplayName("Round-trip serialization")
    class RoundTripTests {

        @Test
        @DisplayName("Should maintain data integrity through JSON round-trip")
        void shouldMaintainDataIntegrityThroughJsonRoundTrip() throws JsonProcessingException {
            ProcessDocumentStorageCommand original = new ProcessDocumentStorageCommand(
                "saga-456", SagaStep.SIGN_PDF, "corr-456",
                "doc-456", "INV-2024-002", "TAX_INVOICE_PDF",
                "http://minio:9000/taxinvoices/xyz.pdf", "signed-doc-456", "XAdES-BASELINE-T"
            );

            String json = objectMapper.writeValueAsString(original);
            ProcessDocumentStorageCommand deserialized = objectMapper.readValue(json, ProcessDocumentStorageCommand.class);

            assertEquals(original.getSagaId(), deserialized.getSagaId());
            assertEquals(original.getSagaStep(), deserialized.getSagaStep());
            assertEquals(original.getCorrelationId(), deserialized.getCorrelationId());
            assertEquals(original.getDocumentId(), deserialized.getDocumentId());
            assertEquals(original.getInvoiceNumber(), deserialized.getInvoiceNumber());
            assertEquals(original.getDocumentType(), deserialized.getDocumentType());
            assertEquals(original.getSignedPdfUrl(), deserialized.getSignedPdfUrl());
            assertEquals(original.getSignedDocumentId(), deserialized.getSignedDocumentId());
            assertEquals(original.getSignatureLevel(), deserialized.getSignatureLevel());
        }
    }
}
