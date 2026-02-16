package com.wpanther.storage.domain.event;

import com.wpanther.saga.domain.enums.ReplyStatus;
import com.wpanther.saga.domain.model.SagaReply;

/**
 * Concrete SagaReply for document-storage-service.
 * Published to Kafka topic: saga.reply.document-storage
 */
public class DocumentStorageReplyEvent extends SagaReply {

    private static final long serialVersionUID = 1L;

    /**
     * Create a SUCCESS reply.
     */
    public static DocumentStorageReplyEvent success(String sagaId, String sagaStep, String correlationId) {
        return new DocumentStorageReplyEvent(sagaId, sagaStep, correlationId, ReplyStatus.SUCCESS);
    }

    /**
     * Create a FAILURE reply.
     */
    public static DocumentStorageReplyEvent failure(String sagaId, String sagaStep,
                                                     String correlationId, String errorMessage) {
        return new DocumentStorageReplyEvent(sagaId, sagaStep, correlationId, errorMessage);
    }

    /**
     * Create a COMPENSATED reply.
     */
    public static DocumentStorageReplyEvent compensated(String sagaId, String sagaStep, String correlationId) {
        return new DocumentStorageReplyEvent(sagaId, sagaStep, correlationId, ReplyStatus.COMPENSATED);
    }

    private DocumentStorageReplyEvent(String sagaId, String sagaStep, String correlationId, ReplyStatus status) {
        super(sagaId, sagaStep, correlationId, status);
    }

    private DocumentStorageReplyEvent(String sagaId, String sagaStep, String correlationId, String errorMessage) {
        super(sagaId, sagaStep, correlationId, errorMessage);
    }
}
