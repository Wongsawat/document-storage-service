package com.wpanther.storage.domain.event;

import com.wpanther.saga.domain.enums.ReplyStatus;
import com.wpanther.saga.domain.model.SagaReply;

/**
 * Concrete SagaReply for signed XML storage.
 * Published to Kafka topic: saga.reply.signedxml-storage
 */
public class SignedXmlStorageReplyEvent extends SagaReply {

    private static final long serialVersionUID = 1L;

    /**
     * Create a SUCCESS reply.
     */
    public static SignedXmlStorageReplyEvent success(String sagaId, String sagaStep, String correlationId) {
        return new SignedXmlStorageReplyEvent(sagaId, sagaStep, correlationId, ReplyStatus.SUCCESS);
    }

    /**
     * Create a FAILURE reply.
     */
    public static SignedXmlStorageReplyEvent failure(String sagaId, String sagaStep,
                                                     String correlationId, String errorMessage) {
        return new SignedXmlStorageReplyEvent(sagaId, sagaStep, correlationId, errorMessage);
    }

    /**
     * Create a COMPENSATED reply.
     */
    public static SignedXmlStorageReplyEvent compensated(String sagaId, String sagaStep, String correlationId) {
        return new SignedXmlStorageReplyEvent(sagaId, sagaStep, correlationId, ReplyStatus.COMPENSATED);
    }

    private SignedXmlStorageReplyEvent(String sagaId, String sagaStep, String correlationId, ReplyStatus status) {
        super(sagaId, sagaStep, correlationId, status);
    }

    private SignedXmlStorageReplyEvent(String sagaId, String sagaStep, String correlationId, String errorMessage) {
        super(sagaId, sagaStep, correlationId, errorMessage);
    }
}
