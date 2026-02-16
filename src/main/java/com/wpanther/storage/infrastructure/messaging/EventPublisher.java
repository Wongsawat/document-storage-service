package com.wpanther.storage.infrastructure.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wpanther.storage.domain.event.DocumentStoredEvent;
import com.wpanther.saga.infrastructure.outbox.OutboxService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;

/**
 * Publisher for domain events via outbox pattern.
 * Events are written to the outbox table within a transaction,
 * then published to Kafka by Debezium CDC.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class EventPublisher {

    private static final String DOCUMENT_STORED_TOPIC = "document.stored";
    private static final String AGGREGATE_TYPE = "StoredDocument";

    private final OutboxService outboxService;
    private final ObjectMapper objectMapper;

    @Transactional(propagation = Propagation.MANDATORY)
    public void publishDocumentStored(DocumentStoredEvent event) {
        Map<String, String> headers = new HashMap<>();
        if (event.getCorrelationId() != null) {
            headers.put("correlationId", event.getCorrelationId());
        }
        if (event.getInvoiceNumber() != null) {
            headers.put("invoiceNumber", event.getInvoiceNumber());
        }

        outboxService.saveWithRouting(
                event,
                AGGREGATE_TYPE,
                event.getInvoiceId(),
                DOCUMENT_STORED_TOPIC,
                event.getInvoiceId(),
                toJson(headers)
        );

        log.info("Published DocumentStoredEvent to outbox: documentId={}, invoiceNumber={}",
                event.getDocumentId(), event.getInvoiceNumber());
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
