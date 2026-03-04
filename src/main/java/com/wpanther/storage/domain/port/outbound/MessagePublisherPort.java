package com.wpanther.storage.domain.port.outbound;

import com.wpanther.storage.domain.event.DocumentStoredEvent;
import com.wpanther.storage.domain.event.DocumentStorageReplyEvent;
import com.wpanther.storage.domain.event.SignedXmlStorageReplyEvent;
import com.wpanther.storage.domain.event.PdfStorageReplyEvent;

/**
 * Outbound port for publishing messages to Kafka via the Transactional Outbox pattern.
 * <p>
 * This port abstracts message publishing, implemented by
 * {@link com.wpanther.storage.infrastructure.adapter.outbound.messaging.MessagePublisherAdapter}.
 * </p>
 * <p>
 * All methods write to the outbox table within the current transaction. Debezium CDC
 * then publishes the events to Kafka asynchronously, ensuring exactly-once semantics.
 * </p>
 * <p>
 * The port supports two types of messages:
 * </p>
 * <ul>
 *   <li><b>Domain Events</b>: Published to Kafka topics for downstream consumers</li>
 *   <li><b>Saga Replies</b>: Published to orchestrator to coordinate saga steps</li>
 * </ul>
 *
 * @see com.wpanther.storage.infrastructure.adapter.outbound.messaging.MessagePublisherAdapter
 */
public interface MessagePublisherPort {

    /**
     * Publish a domain event after document storage.
     * <p>
     * This event is published to the {@code document.stored} topic and consumed
     * by the Notification Service to update users on processing progress.
     * </p>
     *
     * @param event the document stored event containing document metadata
     * @throws com.wpanther.storage.domain.exception.StorageFailedException if publishing fails
     */
    void publishEvent(DocumentStoredEvent event);

    /**
     * Publish a saga reply for the STORE_DOCUMENT step.
     * <p>
     * Indicates completion (success or failure) of storing a signed PDF document.
     * Published to the {@code saga.reply.document-storage} topic for the orchestrator.
     * </p>
     *
     * @param reply the saga reply with status, saga ID, and optional error message
     * @throws com.wpanther.storage.domain.exception.StorageFailedException if publishing fails
     */
    void publishReply(DocumentStorageReplyEvent reply);

    /**
     * Publish a saga reply for the SIGNEDXML_STORAGE step.
     * <p>
     * Indicates completion (success or failure) of storing a signed XML document.
     * Published to the {@code saga.reply.signedxml-storage} topic for the orchestrator.
     * </p>
     *
     * @param reply the saga reply with status, saga ID, and optional error message
     * @throws com.wpanther.storage.domain.exception.StorageFailedException if publishing fails
     */
    void publishReply(SignedXmlStorageReplyEvent reply);

    /**
     * Publish a saga reply for the PDF_STORAGE step.
     * <p>
     * Indicates completion (success or failure) of storing an unsigned PDF from MinIO.
     * Published to the {@code saga.reply.pdf-storage} topic for the orchestrator.
     * </p>
     * <p>
     * On success, the reply includes the {@code storedDocumentId} and {@code storedDocumentUrl}
     * for the SIGN_PDF step to download and sign the PDF.
     * </p>
     *
     * @param reply the saga reply with status, saga ID, document ID, and URL on success
     * @throws com.wpanther.storage.domain.exception.StorageFailedException if publishing fails
     */
    void publishReply(PdfStorageReplyEvent reply);
}
