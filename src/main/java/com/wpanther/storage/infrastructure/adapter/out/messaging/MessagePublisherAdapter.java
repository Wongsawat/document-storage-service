package com.wpanther.storage.infrastructure.adapter.out.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wpanther.saga.domain.outbox.OutboxEvent;
import com.wpanther.saga.domain.outbox.OutboxEventRepository;
import com.wpanther.saga.domain.outbox.OutboxStatus;
import com.wpanther.storage.application.dto.event.DocumentStoredEvent;
import com.wpanther.storage.application.dto.event.DocumentStorageReplyEvent;
import com.wpanther.storage.application.dto.event.PdfStorageReplyEvent;
import com.wpanther.storage.application.dto.event.SignedXmlStorageReplyEvent;
import com.wpanther.storage.domain.exception.StorageFailedException;
import com.wpanther.storage.application.port.out.MessagePublisherPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

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
        saveToOutbox(event.getDocumentId(), "StoredDocument", "DocumentStoredEvent",
                "document.stored", "event", event);
    }

    @Override
    public void publishReply(DocumentStorageReplyEvent reply) {
        saveToOutbox(reply.getSagaId(), "DocumentStorageSaga", "DocumentStorageReplyEvent",
                "saga.reply.document-storage", "reply", reply);
    }

    @Override
    public void publishReply(SignedXmlStorageReplyEvent reply) {
        saveToOutbox(reply.getSagaId(), "SignedXmlStorageSaga", "SignedXmlStorageReplyEvent",
                "saga.reply.signedxml-storage", "reply", reply);
    }

    @Override
    public void publishReply(PdfStorageReplyEvent reply) {
        saveToOutbox(reply.getSagaId(), "PdfStorageSaga", "PdfStorageReplyEvent",
                "saga.reply.pdf-storage", "reply", reply);
    }

    private void saveToOutbox(String aggregateId, String aggregateType, String eventType,
                              String topic, String logLabel, Object payloadSource) {
        try {
            String payload = objectMapper.writeValueAsString(payloadSource);
            OutboxEvent outbox = OutboxEvent.builder()
                .id(UUID.randomUUID())
                .aggregateId(aggregateId)
                .aggregateType(aggregateType)
                .eventType(eventType)
                .payload(payload)
                .topic(topic)
                .status(OutboxStatus.PENDING)
                .createdAt(Instant.now())
                .retryCount(0)
                .build();

            outboxRepository.save(outbox);
            log.debug("Published {} for: {}", logLabel, aggregateId);

        } catch (Exception e) {
            log.error("Failed to publish {}", logLabel, e);
            throw new StorageFailedException("Failed to publish " + logLabel.toLowerCase(), e);
        }
    }
}
