package com.wpanther.storage.application.dto.event;

import com.wpanther.saga.domain.enums.ReplyStatus;
import com.wpanther.saga.domain.enums.SagaStep;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("DocumentStorageReplyEvent Tests")
class DocumentStorageReplyEventTest {

    @Nested
    @DisplayName("Factory methods")
    class FactoryMethodTests {

        @Test
        @DisplayName("Should create success reply")
        void shouldCreateSuccessReply() {
            String sagaId = "saga-123";
            SagaStep sagaStep = SagaStep.STORE_DOCUMENT;
            String correlationId = "corr-123";

            DocumentStorageReplyEvent event = DocumentStorageReplyEvent.success(
                sagaId, sagaStep, correlationId
            );

            assertEquals(sagaId, event.getSagaId());
            assertEquals(sagaStep, event.getSagaStep());
            assertEquals(correlationId, event.getCorrelationId());
            assertEquals(ReplyStatus.SUCCESS, event.getStatus());
        }

        @Test
        @DisplayName("Should create failure reply")
        void shouldCreateFailureReply() {
            String sagaId = "saga-123";
            SagaStep sagaStep = SagaStep.STORE_DOCUMENT;
            String correlationId = "corr-123";
            String errorMessage = "Failed to store document";

            DocumentStorageReplyEvent event = DocumentStorageReplyEvent.failure(
                sagaId, sagaStep, correlationId, errorMessage
            );

            assertEquals(sagaId, event.getSagaId());
            assertEquals(sagaStep, event.getSagaStep());
            assertEquals(correlationId, event.getCorrelationId());
            assertEquals(ReplyStatus.FAILURE, event.getStatus());
            assertEquals(errorMessage, event.getErrorMessage());
        }

        @Test
        @DisplayName("Should create compensated reply")
        void shouldCreateCompensatedReply() {
            String sagaId = "saga-123";
            SagaStep sagaStep = SagaStep.STORE_DOCUMENT;
            String correlationId = "corr-123";

            DocumentStorageReplyEvent event = DocumentStorageReplyEvent.compensated(
                sagaId, sagaStep, correlationId
            );

            assertEquals(sagaId, event.getSagaId());
            assertEquals(sagaStep, event.getSagaStep());
            assertEquals(correlationId, event.getCorrelationId());
            assertEquals(ReplyStatus.COMPENSATED, event.getStatus());
        }
    }
}
