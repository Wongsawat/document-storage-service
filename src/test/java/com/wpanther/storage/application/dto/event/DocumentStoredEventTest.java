package com.wpanther.storage.application.dto.event;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.wpanther.saga.domain.enums.SagaStep;
import com.wpanther.saga.domain.model.TraceEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("DocumentStoredEvent Tests")
class DocumentStoredEventTest {

    private static final ObjectMapper objectMapper = JsonMapper.builder()
        .addModule(new JavaTimeModule())
        .build();

    @Nested
    @DisplayName("Constructor and getters")
    class ConstructorTests {

        @Test
        @DisplayName("Should create event with all fields")
        void shouldCreateEventWithAllFields() {
            String documentId = "doc-123";
            String invoiceId = "INV-001";
            String invoiceNumber = "INV-2024-001";
            String fileName = "invoice.pdf";
            String storageUrl = "http://localhost:8084/api/v1/documents/file-123";
            long fileSize = 1024L;
            String checksum = "abc123def456";
            String documentType = "INVOICE_PDF";
            String correlationId = "corr-123";

            DocumentStoredEvent event = new DocumentStoredEvent(
                documentId, invoiceId, invoiceNumber, fileName, storageUrl,
                fileSize, checksum, documentType, correlationId
            );

            assertEquals(documentId, event.getDocumentId());
            assertEquals(invoiceId, event.getInvoiceId());
            assertEquals(invoiceNumber, event.getInvoiceNumber());
            assertEquals(fileName, event.getFileName());
            assertEquals(storageUrl, event.getStorageUrl());
            assertEquals(fileSize, event.getFileSize());
            assertEquals(checksum, event.getChecksum());
            assertEquals(documentType, event.getDocumentType());
            assertEquals(correlationId, event.getCorrelationId());
        }

        @Test
        @DisplayName("Polymorphic TraceEvent accessor should return correlationId")
        void getCorrelationId_polymorphicAccessorShouldReturnProvidedValue() {
            // Arrange
            DocumentStoredEvent event = new DocumentStoredEvent(
                    "doc-123", "inv-001", "INV-001",
                    "invoice.pdf", "http://localhost/doc.pdf", 12345L,
                    "abc123checksum", "TAX_INVOICE", "corr-abc");

            // Act — call via polymorphic TraceEvent reference
            TraceEvent traceEvent = event;

            // Assert
            assertEquals("corr-abc", traceEvent.getCorrelationId());
        }

        @Test
        @DisplayName("Should return correct event type")
        void shouldReturnCorrectEventType() {
            DocumentStoredEvent event = new DocumentStoredEvent(
                "doc-123", "INV-001", "INV-2024-001", "invoice.pdf",
                "http://localhost:8084/api/v1/documents/file-123", 1024L,
                "abc123", "INVOICE_PDF", "corr-123"
            );

            assertEquals("document.stored", event.getEventType());
        }
    }

    @Nested
    @DisplayName("JSON serialization")
    class JsonSerializationTests {

        @Test
        @DisplayName("Should serialize to JSON correctly")
        void shouldSerializeToJsonCorrectly() throws JsonProcessingException {
            DocumentStoredEvent event = new DocumentStoredEvent(
                "doc-123", "INV-001", "INV-2024-001", "invoice.pdf",
                "http://localhost:8084/api/v1/documents/file-123", 1024L,
                "abc123", "INVOICE_PDF", "corr-123"
            );

            String json = objectMapper.writeValueAsString(event);

            assertTrue(json.contains("\"documentId\":\"doc-123\""));
            assertTrue(json.contains("\"invoiceId\":\"INV-001\""));
            assertTrue(json.contains("\"invoiceNumber\":\"INV-2024-001\""));
            assertTrue(json.contains("\"fileName\":\"invoice.pdf\""));
            assertTrue(json.contains("\"storageUrl\":\"http://localhost:8084/api/v1/documents/file-123\""));
            assertTrue(json.contains("\"fileSize\":1024"));
            assertTrue(json.contains("\"checksum\":\"abc123\""));
            assertTrue(json.contains("\"documentType\":\"INVOICE_PDF\""));
            assertTrue(json.contains("\"correlationId\":\"corr-123\""));
        }

        @Test
        @DisplayName("Should deserialize from JSON correctly")
        void shouldDeserializeFromJsonCorrectly() throws JsonProcessingException {
            String json = """
                {
                    "eventId": "550e8400-e29b-41d4-a716-446655440000",
                    "occurredAt": "2024-03-05T10:00:00Z",
                    "eventType": "document.stored",
                    "version": 1,
                    "sagaId": "saga-123",
                    "source": "document-storage-service",
                    "traceType": "DOCUMENT_STORED",
                    "context": "INV-001",
                    "documentId": "doc-123",
                    "invoiceId": "INV-001",
                    "invoiceNumber": "INV-2024-001",
                    "fileName": "invoice.pdf",
                    "storageUrl": "http://localhost:8084/api/v1/documents/file-123",
                    "fileSize": 1024,
                    "checksum": "abc123",
                    "documentType": "INVOICE_PDF",
                    "correlationId": "corr-123"
                }
                """;

            DocumentStoredEvent event = objectMapper.readValue(json, DocumentStoredEvent.class);

            assertEquals("doc-123", event.getDocumentId());
            assertEquals("INV-001", event.getInvoiceId());
            assertEquals("INV-2024-001", event.getInvoiceNumber());
            assertEquals("invoice.pdf", event.getFileName());
            assertEquals(1024L, event.getFileSize());
            assertEquals("abc123", event.getChecksum());
            assertEquals("INVOICE_PDF", event.getDocumentType());
            assertEquals("corr-123", event.getCorrelationId());
        }
    }

    @Nested
    @DisplayName("Round-trip serialization")
    class RoundTripTests {

        @Test
        @DisplayName("Should maintain data integrity through JSON round-trip")
        void shouldMaintainDataIntegrityThroughJsonRoundTrip() throws JsonProcessingException {
            DocumentStoredEvent original = new DocumentStoredEvent(
                "doc-456", "INV-002", "INV-2024-002", "tax-invoice.pdf",
                "http://localhost:8084/api/v1/documents/file-456", 2048L,
                "def456ghi789", "TAX_INVOICE_PDF", "corr-456"
            );

            String json = objectMapper.writeValueAsString(original);
            DocumentStoredEvent deserialized = objectMapper.readValue(json, DocumentStoredEvent.class);

            assertEquals(original.getDocumentId(), deserialized.getDocumentId());
            assertEquals(original.getInvoiceId(), deserialized.getInvoiceId());
            assertEquals(original.getInvoiceNumber(), deserialized.getInvoiceNumber());
            assertEquals(original.getFileName(), deserialized.getFileName());
            assertEquals(original.getStorageUrl(), deserialized.getStorageUrl());
            assertEquals(original.getFileSize(), deserialized.getFileSize());
            assertEquals(original.getChecksum(), deserialized.getChecksum());
            assertEquals(original.getDocumentType(), deserialized.getDocumentType());
            assertEquals(original.getCorrelationId(), deserialized.getCorrelationId());
        }
    }
}
