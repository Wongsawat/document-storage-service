package com.wpanther.storage.infrastructure.messaging;

import com.wpanther.storage.domain.event.DocumentStoredEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.camel.ProducerTemplate;
import org.springframework.stereotype.Component;

/**
 * Publisher for integration events using Apache Camel.
 *
 * Uses ProducerTemplate to send events to Camel direct endpoints,
 * which are then routed to Kafka topics by Camel routes.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class EventPublisher {

    private final ProducerTemplate producerTemplate;

    /**
     * Publish document stored event to Kafka via Camel direct route.
     *
     * Sends the event to "direct:publish-document-stored" endpoint,
     * which is processed by DocumentStorageRouteConfig to publish
     * to the "document.stored" Kafka topic.
     *
     * @param event the DocumentStoredEvent to publish
     */
    public void publishDocumentStored(DocumentStoredEvent event) {
        log.info("Publishing document stored event for document: {}", event.getDocumentId());
        try {
            producerTemplate.sendBodyAndHeader(
                "direct:publish-document-stored",
                event,
                "kafka.KEY",
                event.getDocumentId()
            );
            log.info("Successfully published document stored event: documentId={}, invoiceNumber={}",
                event.getDocumentId(), event.getInvoiceNumber());
        } catch (Exception e) {
            log.error("Failed to publish document stored event: documentId={}", event.getDocumentId(), e);
            throw e;
        }
    }
}
