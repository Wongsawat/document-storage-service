package com.wpanther.storage.infrastructure.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wpanther.saga.domain.enums.SagaStep;
import com.wpanther.saga.infrastructure.outbox.OutboxService;
import com.wpanther.storage.domain.event.SignedXmlStorageReplyEvent;
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
class SignedXmlStorageSagaReplyPublisherTest {

    @Mock
    private OutboxService outboxService;

    private SignedXmlStorageSagaReplyPublisher publisher;

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        publisher = new SignedXmlStorageSagaReplyPublisher(outboxService, objectMapper);
    }

    @Test
    void testPublishSuccessCallsOutboxWithCorrectParameters() {
        publisher.publishSuccess("saga-1", SagaStep.SIGNEDXML_STORAGE, "corr-1");

        verify(outboxService).saveWithRouting(
                any(SignedXmlStorageReplyEvent.class),
                eq("SignedXmlDocument"),
                eq("saga-1"),
                eq("saga.reply.signedxml-storage"),
                eq("saga-1"),
                contains("SUCCESS")
        );
    }

    @Test
    void testPublishSuccessUsesSagaIdAsPartitionKey() {
        publisher.publishSuccess("my-saga-id", SagaStep.SIGNEDXML_STORAGE, "corr-1");

        ArgumentCaptor<String> partitionKeyCaptor = ArgumentCaptor.forClass(String.class);
        verify(outboxService).saveWithRouting(
                any(), any(), any(), any(),
                partitionKeyCaptor.capture(),
                any()
        );

        assertEquals("my-saga-id", partitionKeyCaptor.getValue());
    }

    @Test
    void testPublishFailureCallsOutboxWithCorrectParameters() {
        publisher.publishFailure("saga-1", SagaStep.SIGNEDXML_STORAGE, "corr-1", "Storage error");

        verify(outboxService).saveWithRouting(
                any(SignedXmlStorageReplyEvent.class),
                eq("SignedXmlDocument"),
                eq("saga-1"),
                eq("saga.reply.signedxml-storage"),
                eq("saga-1"),
                contains("FAILURE")
        );
    }

    @Test
    void testPublishCompensatedCallsOutboxWithCorrectParameters() {
        publisher.publishCompensated("saga-1", SagaStep.SIGNEDXML_STORAGE, "corr-1");

        verify(outboxService).saveWithRouting(
                any(SignedXmlStorageReplyEvent.class),
                eq("SignedXmlDocument"),
                eq("saga-1"),
                eq("saga.reply.signedxml-storage"),
                eq("saga-1"),
                contains("COMPENSATED")
        );
    }

    @Test
    void testPublishReplyEventHasCorrectTopic() {
        publisher.publishSuccess("saga-1", SagaStep.SIGNEDXML_STORAGE, "corr-1");

        ArgumentCaptor<String> topicCaptor = ArgumentCaptor.forClass(String.class);
        verify(outboxService).saveWithRouting(any(), any(), any(), topicCaptor.capture(), any(), any());

        assertEquals("saga.reply.signedxml-storage", topicCaptor.getValue());
    }

    @Test
    void testPublishReplyEventHasCorrectAggregateType() {
        publisher.publishFailure("saga-1", SagaStep.SIGNEDXML_STORAGE, "corr-1", "error");

        ArgumentCaptor<String> aggregateTypeCaptor = ArgumentCaptor.forClass(String.class);
        verify(outboxService).saveWithRouting(any(), aggregateTypeCaptor.capture(), any(), any(), any(), any());

        assertEquals("SignedXmlDocument", aggregateTypeCaptor.getValue());
    }
}
