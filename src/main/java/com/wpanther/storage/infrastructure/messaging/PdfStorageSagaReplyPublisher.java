package com.wpanther.storage.infrastructure.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wpanther.saga.domain.enums.SagaStep;
import com.wpanther.saga.infrastructure.outbox.OutboxService;
import com.wpanther.storage.domain.event.PdfStorageReplyEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

/**
 * Publishes saga reply events for the PDF_STORAGE step via outbox pattern.
 * Replies are sent to orchestrator via saga.reply.pdf-storage topic.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PdfStorageSagaReplyPublisher {

    private static final String REPLY_TOPIC = "saga.reply.pdf-storage";
    private static final String AGGREGATE_TYPE = "StoredDocument";

    private final OutboxService outboxService;
    private final ObjectMapper objectMapper;

    @Transactional(propagation = Propagation.MANDATORY)
    public void publishSuccess(String sagaId, SagaStep sagaStep, String correlationId,
                               String storedDocumentId, String storedDocumentUrl) {
        PdfStorageReplyEvent reply = PdfStorageReplyEvent.success(
                sagaId, sagaStep, correlationId, storedDocumentId, storedDocumentUrl);

        Map<String, String> headers = Map.of(
                "sagaId", sagaId,
                "correlationId", correlationId,
                "status", "SUCCESS"
        );

        outboxService.saveWithRouting(
                reply,
                AGGREGATE_TYPE,
                sagaId,
                REPLY_TOPIC,
                sagaId,
                toJson(headers)
        );

        log.info("Published SUCCESS pdf-storage saga reply for saga {} step {} with storedDocumentUrl={}",
                sagaId, sagaStep, storedDocumentUrl);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void publishFailure(String sagaId, SagaStep sagaStep, String correlationId, String errorMessage) {
        PdfStorageReplyEvent reply = PdfStorageReplyEvent.failure(sagaId, sagaStep, correlationId, errorMessage);

        Map<String, String> headers = Map.of(
                "sagaId", sagaId,
                "correlationId", correlationId,
                "status", "FAILURE"
        );

        outboxService.saveWithRouting(
                reply,
                AGGREGATE_TYPE,
                sagaId,
                REPLY_TOPIC,
                sagaId,
                toJson(headers)
        );

        log.info("Published FAILURE pdf-storage saga reply for saga {} step {}: {}", sagaId, sagaStep, errorMessage);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void publishCompensated(String sagaId, SagaStep sagaStep, String correlationId) {
        PdfStorageReplyEvent reply = PdfStorageReplyEvent.compensated(sagaId, sagaStep, correlationId);

        Map<String, String> headers = Map.of(
                "sagaId", sagaId,
                "correlationId", correlationId,
                "status", "COMPENSATED"
        );

        outboxService.saveWithRouting(
                reply,
                AGGREGATE_TYPE,
                sagaId,
                REPLY_TOPIC,
                sagaId,
                toJson(headers)
        );

        log.info("Published COMPENSATED pdf-storage saga reply for saga {} step {}", sagaId, sagaStep);
    }

    private String toJson(Map<String, String> map) {
        try {
            return objectMapper.writeValueAsString(map);
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize headers to JSON", e);
            return null;
        }
    }
}
