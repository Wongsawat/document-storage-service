package com.wpanther.storage.infrastructure.config;

import com.wpanther.storage.application.service.StorageRequestHandler;
import com.wpanther.storage.domain.event.XmlStorageRequestedEvent;
import lombok.RequiredArgsConstructor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.model.dataformat.JsonLibrary;
import org.springframework.stereotype.Component;

/**
 * Apache Camel route configuration for consuming XML storage requests.
 * Consumes from xml.storage.requested topic and delegates to StorageRequestHandler.
 */
@Component
@RequiredArgsConstructor
public class XmlStorageRouteConfig extends RouteBuilder {

    private final StorageRequestHandler storageRequestHandler;

    @Override
    public void configure() {
        from("kafka:xml.storage.requested" +
                "?brokers={{KAFKA_BROKERS:localhost:9092}}" +
                "&groupId=document-storage-service" +
                "&autoCommitEnable=false" +
                "&consumersCount=3" +
                "&maxPollRecords=100" +
                "&breakOnErrorHandler=true")
                .routeId("xml-storage-request-consumer")
                .unmarshal().json(JsonLibrary.Jackson, XmlStorageRequestedEvent.class)
                .doTry()
                    .bean(storageRequestHandler, "handleStorageRequest")
                    .log("Processed XML storage request for invoice: ${body.invoiceId}")
                .doCatch(Exception.class)
                    .log("Error processing XML storage request: ${exception.message}")
                    .to("direct:xmlStorageDlq")
                .end();

        // Dead Letter Queue route
        from("direct:xmlStorageDlq")
                .routeId("xml-storage-dlq")
                .log("Sending XML storage request to DLQ")
                .to("kafka:xml.storage.requested.dlq" +
                        "?brokers={{KAFKA_BROKERS:localhost:9092}}");
    }
}
