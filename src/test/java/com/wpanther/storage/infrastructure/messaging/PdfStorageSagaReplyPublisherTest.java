package com.wpanther.storage.infrastructure.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wpanther.saga.domain.enums.SagaStep;
import com.wpanther.saga.infrastructure.outbox.OutboxService;
import com.wpanther.storage.domain.event.PdfStorageReplyEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PdfStorageSagaReplyPublisherTest {

    @Mock
    private OutboxService outboxService;

    private PdfStorageSagaReplyPublisher publisher;

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        publisher = new PdfStorageSagaReplyPublisher(outboxService, objectMapper);
    }

    @Test
    void testPublishSuccessCallsOutboxWithCorrectParameters() {
        publisher.publishSuccess("saga-1", SagaStep.PDF_STORAGE, "corr-1", "doc-123", "http://storage/doc-123.pdf");

        verify(outboxService).saveWithRouting(
                any(PdfStorageReplyEvent.class),
                eq("StoredDocument"),
                eq("saga-1"),
                eq("saga.reply.pdf-storage"),
                eq("saga-1"),
                contains("SUCCESS")
        );
    }

    @Test
    void testPublishSuccessUsesSagaIdAsPartitionKey() {
        publisher.publishSuccess("my-saga-id", SagaStep.PDF_STORAGE, "corr-1", "doc-123", "http://storage/doc-123.pdf");

        ArgumentCaptor<String> partitionKeyCaptor = ArgumentCaptor.forClass(String.class);
        verify(outboxService).saveWithRouting(
                any(), any(), any(), any(),
                partitionKeyCaptor.capture(),
                any()
        );

        assertEquals("my-saga-id", partitionKeyCaptor.getValue());
    }

    @Test
    void testPublishSuccessIncludesDocumentIdAndUrl() {
        publisher.publishSuccess("saga-1", SagaStep.PDF_STORAGE, "corr-1", "doc-123", "http://storage/doc-123.pdf");

        ArgumentCaptor<PdfStorageReplyEvent> eventCaptor = ArgumentCaptor.forClass(PdfStorageReplyEvent.class);
        verify(outboxService).saveWithRouting(eventCaptor.capture(), any(), any(), any(), any(), any());

        PdfStorageReplyEvent event = eventCaptor.getValue();
        assertEquals("doc-123", event.getStoredDocumentId());
        assertEquals("http://storage/doc-123.pdf", event.getStoredDocumentUrl());
    }

    @Test
    void testPublishFailureCallsOutboxWithCorrectParameters() {
        publisher.publishFailure("saga-1", SagaStep.PDF_STORAGE, "corr-1", "Download failed");

        verify(outboxService).saveWithRouting(
                any(PdfStorageReplyEvent.class),
                eq("StoredDocument"),
                eq("saga-1"),
                eq("saga.reply.pdf-storage"),
                eq("saga-1"),
                contains("FAILURE")
        );
    }

    @Test
    void testPublishCompensatedCallsOutboxWithCorrectParameters() {
        publisher.publishCompensated("saga-1", SagaStep.PDF_STORAGE, "corr-1");

        verify(outboxService).saveWithRouting(
                any(PdfStorageReplyEvent.class),
                eq("StoredDocument"),
                eq("saga-1"),
                eq("saga.reply.pdf-storage"),
                eq("saga-1"),
                contains("COMPENSATED")
        );
    }

    @Test
    void testPublishReplyEventHasCorrectTopic() {
        publisher.publishSuccess("saga-1", SagaStep.PDF_STORAGE, "corr-1", "doc-123", "http://storage/doc-123.pdf");

        ArgumentCaptor<String> topicCaptor = ArgumentCaptor.forClass(String.class);
        verify(outboxService).saveWithRouting(any(), any(), any(), topicCaptor.capture(), any(), any());

        assertEquals("saga.reply.pdf-storage", topicCaptor.getValue());
    }

    @Test
    void testPublishReplyEventHasCorrectAggregateType() {
        publisher.publishFailure("saga-1", SagaStep.PDF_STORAGE, "corr-1", "error");

        ArgumentCaptor<String> aggregateTypeCaptor = ArgumentCaptor.forClass(String.class);
        verify(outboxService).saveWithRouting(any(), aggregateTypeCaptor.capture(), any(), any(), any(), any());

        assertEquals("StoredDocument", aggregateTypeCaptor.getValue());
    }
}
