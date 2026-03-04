package com.wpanther.storage.domain.port.outbound;

import com.wpanther.storage.domain.event.DocumentStoredEvent;
import com.wpanther.storage.domain.event.DocumentStorageReplyEvent;
import com.wpanther.storage.domain.event.SignedXmlStorageReplyEvent;
import com.wpanther.storage.domain.event.PdfStorageReplyEvent;

/**
 * Outbound port for publishing messages to Kafka.
 * Implemented by MessagePublisherAdapter using the outbox pattern.
 */
public interface MessagePublisherPort {
    void publishEvent(DocumentStoredEvent event);
    void publishReply(DocumentStorageReplyEvent reply);
    void publishReply(SignedXmlStorageReplyEvent reply);
    void publishReply(PdfStorageReplyEvent reply);
}
