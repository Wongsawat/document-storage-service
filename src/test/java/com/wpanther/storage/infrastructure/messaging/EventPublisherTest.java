package com.wpanther.storage.infrastructure.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wpanther.saga.infrastructure.outbox.OutboxService;
import com.wpanther.storage.domain.event.DocumentStoredEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class EventPublisherTest {

    @Mock
    private OutboxService outboxService;

    private EventPublisher publisher;

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        publisher = new EventPublisher(outboxService, objectMapper);
    }

    @Test
    void testPublishDocumentStoredEventCallsOutboxWithCorrectParameters() {
        DocumentStoredEvent event = new DocumentStoredEvent(
                "doc-123",
                "INV-001",
                "INV-2024-001",
                "invoice.pdf",
                "http://storage/doc-123.pdf",
                1024L,
                "abc123",
                "INVOICE_PDF",
                "corr-123"
        );

        publisher.publishDocumentStored(event);

        verify(outboxService).saveWithRouting(
                any(DocumentStoredEvent.class),
                eq("StoredDocument"),
                eq("INV-001"),
                eq("document.stored"),
                eq("INV-001"),
                any()
        );
    }

    @Test
    void testPublishDocumentStoredEventHasCorrectData() {
        DocumentStoredEvent event = new DocumentStoredEvent(
                "doc-123",
                "INV-001",
                "INV-2024-001",
                "invoice.pdf",
                "http://storage/doc-123.pdf",
                1024L,
                "abc123",
                "INVOICE_PDF",
                "corr-123"
        );

        publisher.publishDocumentStored(event);

        ArgumentCaptor<DocumentStoredEvent> eventCaptor = ArgumentCaptor.forClass(DocumentStoredEvent.class);
        verify(outboxService).saveWithRouting(eventCaptor.capture(), any(), any(), any(), any(), any());

        DocumentStoredEvent capturedEvent = eventCaptor.getValue();
        assertEquals("doc-123", capturedEvent.getDocumentId());
        assertEquals("invoice.pdf", capturedEvent.getFileName());
        assertEquals("http://storage/doc-123.pdf", capturedEvent.getStorageUrl());
        assertEquals("INVOICE_PDF", capturedEvent.getDocumentType());
        assertEquals("INV-001", capturedEvent.getInvoiceId());
        assertEquals("INV-2024-001", capturedEvent.getInvoiceNumber());
        assertEquals("corr-123", capturedEvent.getCorrelationId());
    }

    @Test
    void testPublishDocumentStoredEventHasCorrectTopic() {
        DocumentStoredEvent event = new DocumentStoredEvent(
                "doc-123",
                null,
                null,
                "invoice.pdf",
                "http://storage/doc-123.pdf",
                1024L,
                "abc123",
                "OTHER",
                null
        );

        publisher.publishDocumentStored(event);

        ArgumentCaptor<String> topicCaptor = ArgumentCaptor.forClass(String.class);
        verify(outboxService).saveWithRouting(any(), any(), any(), topicCaptor.capture(), any(), any());

        assertEquals("document.stored", topicCaptor.getValue());
    }

    @Test
    void testPublishDocumentStoredEventUsesInvoiceIdAsPartitionKey() {
        DocumentStoredEvent event = new DocumentStoredEvent(
                "doc-123",
                "INV-001",
                "INV-2024-001",
                "invoice.pdf",
                "http://storage/doc-123.pdf",
                1024L,
                "abc123",
                "INVOICE_PDF",
                null
        );

        publisher.publishDocumentStored(event);

        ArgumentCaptor<String> partitionKeyCaptor = ArgumentCaptor.forClass(String.class);
        verify(outboxService).saveWithRouting(any(), any(), any(), any(), partitionKeyCaptor.capture(), any());

        assertEquals("INV-001", partitionKeyCaptor.getValue());
    }
}
