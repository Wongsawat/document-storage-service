package com.wpanther.storage.domain.event;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.wpanther.saga.domain.model.IntegrationEvent;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

/**
 * Event consumed by document-storage-service when a signed XML document needs to be stored.
 * Produced by xml-signing-service.
 */
@Getter
public class XmlStorageRequestedEvent extends IntegrationEvent {

    private static final String EVENT_TYPE = "XmlStorageRequestedEvent";

    @JsonProperty("invoiceId")
    private final String invoiceId;

    @JsonProperty("xmlContent")
    private final String xmlContent;

    @JsonProperty("documentType")
    private final String documentType;

    @JsonProperty("invoiceNumber")
    private final String invoiceNumber;

    @JsonProperty("correlationId")
    private final String correlationId;

    /**
     * JsonCreator for deserialization from Kafka/Debezium.
     */
    @JsonCreator
    public XmlStorageRequestedEvent(
            @JsonProperty("eventId") UUID eventId,
            @JsonProperty("occurredAt") Instant occurredAt,
            @JsonProperty("eventType") String eventType,
            @JsonProperty("version") int version,
            @JsonProperty("invoiceId") String invoiceId,
            @JsonProperty("xmlContent") String xmlContent,
            @JsonProperty("documentType") String documentType,
            @JsonProperty("invoiceNumber") String invoiceNumber,
            @JsonProperty("correlationId") String correlationId) {
        super(eventId, occurredAt, eventType, version);
        this.invoiceId = invoiceId;
        this.xmlContent = xmlContent;
        this.documentType = documentType;
        this.invoiceNumber = invoiceNumber;
        this.correlationId = correlationId;
    }
}
