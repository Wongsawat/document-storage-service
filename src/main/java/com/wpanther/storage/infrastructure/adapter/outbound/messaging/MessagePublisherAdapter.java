package com.wpanther.storage.infrastructure.adapter.outbound.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wpanther.saga.domain.outbox.OutboxEvent;
import com.wpanther.saga.domain.outbox.OutboxEventRepository;
import com.wpanther.storage.domain.event.DocumentStoredEvent;
import com.wpanther.storage.domain.event.DocumentStorageReplyEvent;
import com.wpanther.storage.domain.event.PdfStorageReplyEvent;
import com.wpanther.storage.domain.event.SignedXmlStorageReplyEvent;
import com.wpanther.storage.domain.exception.StorageFailedException;
import com.wpanther.storage.domain.port.outbound.MessagePublisherPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Message publisher adapter using the outbox pattern.
 * Implements MessagePublisherPort.
 */
@Component
public class MessagePublisherAdapter implements MessagePublisherPort {

    private static final Logger log = LoggerFactory.getLogger(MessagePublisherAdapter.class);

    private final OutboxEventRepository outboxRepository;
    private final ObjectMapper objectMapper;

    public MessagePublisherAdapter(OutboxEventRepository outboxRepository, ObjectMapper objectMapper) {
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
    }

    @Override
    public void publishEvent(DocumentStoredEvent event) {
        try {
            String payload = objectMapper.writeValueAsString(event);
            OutboxEvent outbox = OutboxEvent.builder()
                .aggregateId(event.getDocumentId())
                .aggregateType("StoredDocument")
                .eventType("DocumentStoredEvent")
                .payload(payload)
                .topic("document.stored")
                .build();

            outboxRepository.save(outbox);
            log.debug("Published DocumentStoredEvent for document: {}", event.getDocumentId());

        } catch (Exception e) {
            log.error("Failed to publish DocumentStoredEvent", e);
            throw new StorageFailedException("Failed to publish event", e);
        }
    }

    @Override
    public void publishReply(DocumentStorageReplyEvent reply) {
        try {
            String payload = objectMapper.writeValueAsString(reply);
            OutboxEvent outbox = OutboxEvent.builder()
                .aggregateId(reply.getSagaId())
                .aggregateType("DocumentStorageSaga")
                .eventType("DocumentStorageReplyEvent")
                .payload(payload)
                .topic("saga.reply.document-storage")
                .build();

            outboxRepository.save(outbox);
            log.debug("Published DocumentStorageReplyEvent for saga: {}", reply.getSagaId());

        } catch (Exception e) {
            log.error("Failed to publish DocumentStorageReplyEvent", e);
            throw new StorageFailedException("Failed to publish reply", e);
        }
    }

    @Override
    public void publishReply(SignedXmlStorageReplyEvent reply) {
        try {
            String payload = objectMapper.writeValueAsString(reply);
            OutboxEvent outbox = OutboxEvent.builder()
                .aggregateId(reply.getSagaId())
                .aggregateType("SignedXmlStorageSaga")
                .eventType("SignedXmlStorageReplyEvent")
                .payload(payload)
                .topic("saga.reply.signedxml-storage")
                .build();

            outboxRepository.save(outbox);
            log.debug("Published SignedXmlStorageReplyEvent for saga: {}", reply.getSagaId());

        } catch (Exception e) {
            log.error("Failed to publish SignedXmlStorageReplyEvent", e);
            throw new StorageFailedException("Failed to publish reply", e);
        }
    }

    @Override
    public void publishReply(PdfStorageReplyEvent reply) {
        try {
            String payload = objectMapper.writeValueAsString(reply);
            OutboxEvent outbox = OutboxEvent.builder()
                .aggregateId(reply.getSagaId())
                .aggregateType("PdfStorageSaga")
                .eventType("PdfStorageReplyEvent")
                .payload(payload)
                .topic("saga.reply.pdf-storage")
                .build();

            outboxRepository.save(outbox);
            log.debug("Published PdfStorageReplyEvent for saga: {}", reply.getSagaId());

        } catch (Exception e) {
            log.error("Failed to publish PdfStorageReplyEvent", e);
            throw new StorageFailedException("Failed to publish reply", e);
        }
    }
}
