package com.wpanther.storage.infrastructure.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wpanther.saga.domain.enums.SagaStep;
import com.wpanther.saga.infrastructure.outbox.OutboxService;
import com.wpanther.storage.domain.event.DocumentStorageReplyEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class SagaReplyPublisherTest {

    @Mock
    private OutboxService outboxService;

    private SagaReplyPublisher publisher;

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        publisher = new SagaReplyPublisher(outboxService, objectMapper);
    }

    @Test
    void testPublishSuccessCallsOutboxWithCorrectParameters() {
        publisher.publishSuccess("saga-1", SagaStep.STORE_DOCUMENT, "corr-1");

        verify(outboxService).saveWithRouting(
                any(DocumentStorageReplyEvent.class),
                eq("StoredDocument"),
                eq("saga-1"),
                eq("saga.reply.document-storage"),
                eq("saga-1"),
                contains("SUCCESS")
        );
    }

    @Test
    void testPublishSuccessUsesSagaIdAsPartitionKey() {
        publisher.publishSuccess("my-saga-id", SagaStep.STORE_DOCUMENT, "corr-1");

        ArgumentCaptor<String> partitionKeyCaptor = ArgumentCaptor.forClass(String.class);
        verify(outboxService).saveWithRouting(
                any(), any(), any(), any(),
                partitionKeyCaptor.capture(),
                any()
        );

        assertEquals("my-saga-id", partitionKeyCaptor.getValue());
    }

    @Test
    void testPublishSuccessHeadersContainCorrectFields() {
        publisher.publishSuccess("saga-1", SagaStep.STORE_DOCUMENT, "corr-1");

        ArgumentCaptor<String> headersCaptor = ArgumentCaptor.forClass(String.class);
        verify(outboxService).saveWithRouting(any(), any(), any(), any(), any(), headersCaptor.capture());

        String headers = headersCaptor.getValue();
        assertTrue(headers.contains("saga-1"));
        assertTrue(headers.contains("corr-1"));
        assertTrue(headers.contains("SUCCESS"));
    }

    @Test
    void testPublishFailureCallsOutboxWithCorrectParameters() {
        publisher.publishFailure("saga-1", SagaStep.STORE_DOCUMENT, "corr-1", "Storage error");

        verify(outboxService).saveWithRouting(
                any(DocumentStorageReplyEvent.class),
                eq("StoredDocument"),
                eq("saga-1"),
                eq("saga.reply.document-storage"),
                eq("saga-1"),
                contains("FAILURE")
        );
    }

    @Test
    void testPublishCompensatedCallsOutboxWithCorrectParameters() {
        publisher.publishCompensated("saga-1", SagaStep.STORE_DOCUMENT, "corr-1");

        verify(outboxService).saveWithRouting(
                any(DocumentStorageReplyEvent.class),
                eq("StoredDocument"),
                eq("saga-1"),
                eq("saga.reply.document-storage"),
                eq("saga-1"),
                contains("COMPENSATED")
        );
    }

    @Test
    void testPublishReplyEventHasCorrectTopic() {
        publisher.publishSuccess("saga-1", SagaStep.STORE_DOCUMENT, "corr-1");

        ArgumentCaptor<String> topicCaptor = ArgumentCaptor.forClass(String.class);
        verify(outboxService).saveWithRouting(any(), any(), any(), topicCaptor.capture(), any(), any());

        assertEquals("saga.reply.document-storage", topicCaptor.getValue());
    }

    @Test
    void testPublishReplyEventHasCorrectAggregateType() {
        publisher.publishFailure("saga-1", SagaStep.STORE_DOCUMENT, "corr-1", "error");

        ArgumentCaptor<String> aggregateTypeCaptor = ArgumentCaptor.forClass(String.class);
        verify(outboxService).saveWithRouting(any(), aggregateTypeCaptor.capture(), any(), any(), any(), any());

        assertEquals("StoredDocument", aggregateTypeCaptor.getValue());
    }
}
