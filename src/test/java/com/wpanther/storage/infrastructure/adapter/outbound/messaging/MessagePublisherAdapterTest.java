package com.wpanther.storage.infrastructure.adapter.outbound.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wpanther.saga.domain.outbox.OutboxEvent;
import com.wpanther.saga.domain.outbox.OutboxEventRepository;
import com.wpanther.storage.domain.event.DocumentStoredEvent;
import com.wpanther.storage.domain.event.DocumentStorageReplyEvent;
import com.wpanther.storage.domain.event.PdfStorageReplyEvent;
import com.wpanther.storage.domain.event.SignedXmlStorageReplyEvent;
import com.wpanther.storage.domain.exception.StorageFailedException;
import com.wpanther.saga.domain.enums.SagaStep;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@DisplayName("MessagePublisherAdapter Tests")
@ExtendWith(MockitoExtension.class)
class MessagePublisherAdapterTest {

    @Mock
    private OutboxEventRepository outboxRepository;

    @Mock
    private ObjectMapper objectMapper;

    private MessagePublisherAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new MessagePublisherAdapter(outboxRepository, objectMapper);
    }

    @Nested
    @DisplayName("publishEvent(DocumentStoredEvent)")
    class PublishEventTests {

        @Test
        @DisplayName("Should publish DocumentStoredEvent to outbox")
        void shouldPublishDocumentStoredEvent() throws Exception {
            DocumentStoredEvent event = new DocumentStoredEvent(
                "doc-123", "INV-001", "INV-2024-001", "invoice.pdf",
                "http://localhost:8084/api/v1/documents/file-123", 1024L,
                "abc123", "INVOICE_PDF", "corr-123"
            );

            String expectedPayload = "{\"documentId\":\"doc-123\"}";
            when(objectMapper.writeValueAsString(event)).thenReturn(expectedPayload);

            adapter.publishEvent(event);

            ArgumentCaptor<OutboxEvent> captor = ArgumentCaptor.forClass(OutboxEvent.class);
            verify(outboxRepository).save(captor.capture());

            OutboxEvent saved = captor.getValue();
            assertEquals("doc-123", saved.getAggregateId());
            assertEquals("StoredDocument", saved.getAggregateType());
            assertEquals("DocumentStoredEvent", saved.getEventType());
            assertEquals(expectedPayload, saved.getPayload());
            assertEquals("document.stored", saved.getTopic());
        }

        @Test
        @DisplayName("Should throw StorageFailedException when JSON serialization fails")
        void shouldThrowExceptionWhenSerializationFails() throws Exception {
            DocumentStoredEvent event = new DocumentStoredEvent(
                "doc-123", "INV-001", "INV-2024-001", "invoice.pdf",
                "http://localhost:8084/api/v1/documents/file-123", 1024L,
                "abc123", "INVOICE_PDF", "corr-123"
            );

            when(objectMapper.writeValueAsString(event))
                .thenThrow(new RuntimeException("Serialization error"));

            StorageFailedException ex = assertThrows(StorageFailedException.class,
                () -> adapter.publishEvent(event));

            assertTrue(ex.getMessage().contains("Failed to publish event"));
            assertNotNull(ex.getCause());
        }
    }

    @Nested
    @DisplayName("publishReply(DocumentStorageReplyEvent)")
    class PublishDocumentStorageReplyTests {

        @Test
        @DisplayName("Should publish DocumentStorageReplyEvent to outbox")
        void shouldPublishDocumentStorageReplyEvent() throws Exception {
            DocumentStorageReplyEvent reply = DocumentStorageReplyEvent.success(
                "saga-123", SagaStep.STORE_DOCUMENT, "corr-123"
            );

            String expectedPayload = "{\"sagaId\":\"saga-123\"}";
            when(objectMapper.writeValueAsString(reply)).thenReturn(expectedPayload);

            adapter.publishReply(reply);

            ArgumentCaptor<OutboxEvent> captor = ArgumentCaptor.forClass(OutboxEvent.class);
            verify(outboxRepository).save(captor.capture());

            OutboxEvent saved = captor.getValue();
            assertEquals("saga-123", saved.getAggregateId());
            assertEquals("DocumentStorageSaga", saved.getAggregateType());
            assertEquals("DocumentStorageReplyEvent", saved.getEventType());
            assertEquals(expectedPayload, saved.getPayload());
            assertEquals("saga.reply.document-storage", saved.getTopic());
        }

        @Test
        @DisplayName("Should throw StorageFailedException when JSON serialization fails")
        void shouldThrowExceptionWhenReplySerializationFails() throws Exception {
            DocumentStorageReplyEvent reply = DocumentStorageReplyEvent.success(
                "saga-123", SagaStep.STORE_DOCUMENT, "corr-123"
            );

            when(objectMapper.writeValueAsString(reply))
                .thenThrow(new RuntimeException("Serialization error"));

            StorageFailedException ex = assertThrows(StorageFailedException.class,
                () -> adapter.publishReply(reply));

            assertTrue(ex.getMessage().contains("Failed to publish reply"));
            assertNotNull(ex.getCause());
        }
    }

    @Nested
    @DisplayName("publishReply(SignedXmlStorageReplyEvent)")
    class PublishSignedXmlReplyTests {

        @Test
        @DisplayName("Should publish SignedXmlStorageReplyEvent to outbox")
        void shouldPublishSignedXmlReplyEvent() throws Exception {
            SignedXmlStorageReplyEvent reply = SignedXmlStorageReplyEvent.success(
                "saga-456", SagaStep.SIGNEDXML_STORAGE, "corr-456"
            );

            String expectedPayload = "{\"sagaId\":\"saga-456\"}";
            when(objectMapper.writeValueAsString(reply)).thenReturn(expectedPayload);

            adapter.publishReply(reply);

            ArgumentCaptor<OutboxEvent> captor = ArgumentCaptor.forClass(OutboxEvent.class);
            verify(outboxRepository).save(captor.capture());

            OutboxEvent saved = captor.getValue();
            assertEquals("saga-456", saved.getAggregateId());
            assertEquals("SignedXmlStorageSaga", saved.getAggregateType());
            assertEquals("SignedXmlStorageReplyEvent", saved.getEventType());
            assertEquals(expectedPayload, saved.getPayload());
            assertEquals("saga.reply.signedxml-storage", saved.getTopic());
        }

        @Test
        @DisplayName("Should throw StorageFailedException when signed XML reply serialization fails")
        void shouldThrowExceptionWhenSignedXmlReplySerializationFails() throws Exception {
            SignedXmlStorageReplyEvent reply = SignedXmlStorageReplyEvent.failure(
                "saga-456", SagaStep.SIGNEDXML_STORAGE, "corr-456", "Test error"
            );

            when(objectMapper.writeValueAsString(reply))
                .thenThrow(new RuntimeException("Serialization error"));

            StorageFailedException ex = assertThrows(StorageFailedException.class,
                () -> adapter.publishReply(reply));

            assertTrue(ex.getMessage().contains("Failed to publish reply"));
            assertNotNull(ex.getCause());
        }
    }

    @Nested
    @DisplayName("publishReply(PdfStorageReplyEvent)")
    class PublishPdfReplyTests {

        @Test
        @DisplayName("Should publish PdfStorageReplyEvent to outbox")
        void shouldPublishPdfReplyEvent() throws Exception {
            PdfStorageReplyEvent reply = PdfStorageReplyEvent.success(
                "saga-789", SagaStep.PDF_STORAGE, "corr-789",
                "doc-789", "http://localhost:8084/api/v1/documents/doc-789"
            );

            String expectedPayload = "{\"sagaId\":\"saga-789\"}";
            when(objectMapper.writeValueAsString(reply)).thenReturn(expectedPayload);

            adapter.publishReply(reply);

            ArgumentCaptor<OutboxEvent> captor = ArgumentCaptor.forClass(OutboxEvent.class);
            verify(outboxRepository).save(captor.capture());

            OutboxEvent saved = captor.getValue();
            assertEquals("saga-789", saved.getAggregateId());
            assertEquals("PdfStorageSaga", saved.getAggregateType());
            assertEquals("PdfStorageReplyEvent", saved.getEventType());
            assertEquals(expectedPayload, saved.getPayload());
            assertEquals("saga.reply.pdf-storage", saved.getTopic());
        }

        @Test
        @DisplayName("Should throw StorageFailedException when PDF reply serialization fails")
        void shouldThrowExceptionWhenPdfReplySerializationFails() throws Exception {
            PdfStorageReplyEvent reply = PdfStorageReplyEvent.failure(
                "saga-789", SagaStep.PDF_STORAGE, "corr-789", "Test error"
            );

            when(objectMapper.writeValueAsString(reply))
                .thenThrow(new RuntimeException("Serialization error"));

            StorageFailedException ex = assertThrows(StorageFailedException.class,
                () -> adapter.publishReply(reply));

            assertTrue(ex.getMessage().contains("Failed to publish reply"));
            assertNotNull(ex.getCause());
        }
    }

    @Nested
    @DisplayName("Constructor")
    class ConstructorTests {

        @Test
        @DisplayName("Should create adapter with all dependencies")
        void shouldCreateAdapterWithDependencies() {
            assertNotNull(adapter);
        }
    }
}
