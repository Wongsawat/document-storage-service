package com.wpanther.storage.domain.event;

import com.wpanther.saga.domain.enums.ReplyStatus;
import com.wpanther.saga.domain.enums.SagaStep;
import com.wpanther.saga.domain.model.SagaReply;

/**
 * Saga reply event for the PDF_STORAGE step.
 * Published to Kafka topic: saga.reply.pdf-storage
 *
 * SUCCESS replies include storedDocumentId and storedDocumentUrl so the orchestrator
 * can pass the URL to the SIGN_PDF step via DocumentMetadata.metadata["storedDocumentUrl"].
 */
public class PdfStorageReplyEvent extends SagaReply {

    private static final long serialVersionUID = 1L;

    private String storedDocumentId;
    private String storedDocumentUrl;

    public static PdfStorageReplyEvent success(String sagaId, SagaStep sagaStep, String correlationId,
                                               String storedDocumentId, String storedDocumentUrl) {
        PdfStorageReplyEvent reply = new PdfStorageReplyEvent(sagaId, sagaStep, correlationId, ReplyStatus.SUCCESS);
        reply.storedDocumentId = storedDocumentId;
        reply.storedDocumentUrl = storedDocumentUrl;
        return reply;
    }

    public static PdfStorageReplyEvent failure(String sagaId, SagaStep sagaStep, String correlationId,
                                               String errorMessage) {
        return new PdfStorageReplyEvent(sagaId, sagaStep, correlationId, errorMessage);
    }

    public static PdfStorageReplyEvent compensated(String sagaId, SagaStep sagaStep, String correlationId) {
        return new PdfStorageReplyEvent(sagaId, sagaStep, correlationId, ReplyStatus.COMPENSATED);
    }

    private PdfStorageReplyEvent(String sagaId, SagaStep sagaStep, String correlationId, ReplyStatus status) {
        super(sagaId, sagaStep, correlationId, status);
    }

    private PdfStorageReplyEvent(String sagaId, SagaStep sagaStep, String correlationId, String errorMessage) {
        super(sagaId, sagaStep, correlationId, errorMessage);
    }

    public String getStoredDocumentId() {
        return storedDocumentId;
    }

    public String getStoredDocumentUrl() {
        return storedDocumentUrl;
    }
}
