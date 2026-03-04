package com.wpanther.storage.infrastructure.adapter.inbound.messaging;

import com.wpanther.storage.domain.event.*;
import com.wpanther.storage.domain.port.inbound.SagaCommandUseCase;
import lombok.extern.slf4j.Slf4j;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.model.dataformat.JsonLibrary;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Camel-based messaging adapter for saga commands.
 * Consumes from Kafka topics and delegates to SagaCommandUseCase.
 * <p>
 * Configuration:
 * - Separate consumer groups per saga step to prevent cross-contamination
 * - Configurable autoOffsetReset (earliest for dev, latest for production)
 * - Manual offset control for exactly-once semantics with outbox pattern
 * - Dead Letter Channel with exponential backoff for error handling
 */
@Component
@Slf4j
public class SagaCommandAdapter extends RouteBuilder {

    // Retry configuration constants
    private static final long INITIAL_REDELIVERY_DELAY_MS = 1000L;
    private static final int BACKOFF_MULTIPLIER = 2;
    private static final long MAX_REDELIVERY_DELAY_MS = 10000L;

    private final SagaCommandUseCase sagaCommandUseCase;

    @Value("${app.kafka.bootstrap-servers}")
    private String kafkaBrokers;

    @Value("${app.kafka.topics.saga-command-document-storage}")
    private String sagaCommandTopic;

    @Value("${app.kafka.topics.saga-compensation-document-storage}")
    private String sagaCompensationTopic;

    @Value("${app.kafka.topics.saga-command-signedxml-storage}")
    private String sagaCommandSignedXmlTopic;

    @Value("${app.kafka.topics.saga-compensation-signedxml-storage}")
    private String sagaCompensationSignedXmlTopic;

    @Value("${app.kafka.topics.saga-command-pdf-storage}")
    private String sagaCommandPdfStorageTopic;

    @Value("${app.kafka.topics.saga-compensation-pdf-storage}")
    private String sagaCompensationPdfStorageTopic;

    @Value("${app.kafka.topics.dlq:document-storage.dlq}")
    private String dlqTopic;

    @Value("${app.kafka.consumer.auto-offset-reset:latest}")
    private String autoOffsetReset;

    @Value("${app.kafka.consumer.max-poll-records:100}")
    private int maxPollRecords;

    @Value("${app.kafka.consumer.consumers-count:3}")
    private int consumersCount;

    @Value("${app.kafka.consumer.max-redeliveries:3}")
    private int maxRedeliveries;

    public SagaCommandAdapter(SagaCommandUseCase sagaCommandUseCase) {
        this.sagaCommandUseCase = sagaCommandUseCase;
    }

    @Override
    public void configure() throws Exception {

        // Global error handler with Dead Letter Channel
        errorHandler(deadLetterChannel("kafka:" + dlqTopic + "?brokers=" + kafkaBrokers)
                .maximumRedeliveries(maxRedeliveries)
                .redeliveryDelay(INITIAL_REDELIVERY_DELAY_MS)
                .useExponentialBackOff()
                .backOffMultiplier(BACKOFF_MULTIPLIER)
                .maximumRedeliveryDelay(MAX_REDELIVERY_DELAY_MS)
                .logExhausted(true)
                .logStackTrace(true)
                .retryAttemptedLogLevel(org.apache.camel.LoggingLevel.WARN));

        // ========================================
        // STORE_DOCUMENT saga step routes
        // ========================================

        // Consume ProcessDocumentStorageCommand from orchestrator
        from("kafka:" + sagaCommandTopic
                + "?brokers=" + kafkaBrokers
                + "&groupId=document-storage-store-document"  // Unique consumer group per step
                + "&autoOffsetReset=" + autoOffsetReset
                + "&autoCommitEnable=false"  // Manual commit for exactly-once
                + "&maxPollRecords=" + maxPollRecords
                + "&consumersCount=" + consumersCount)
                .routeId("saga-document-storage-command")
                .log("Received saga command: partition=${header[kafka.PARTITION]}, offset=${header[kafka.OFFSET]}")
                .unmarshal().json(JsonLibrary.Jackson, ProcessDocumentStorageCommand.class)
                .process(exchange -> {
                    ProcessDocumentStorageCommand cmd = exchange.getIn().getBody(ProcessDocumentStorageCommand.class);
                    log.info("Processing saga command for saga: {}, document: {}",
                            cmd.getSagaId(), cmd.getDocumentId());
                    sagaCommandUseCase.handleProcessCommand(cmd);
                })
                .log("Successfully processed saga command");

        // Consume CompensateDocumentStorageCommand from orchestrator
        from("kafka:" + sagaCompensationTopic
                + "?brokers=" + kafkaBrokers
                + "&groupId=document-storage-store-document-compensation"  // Unique consumer group
                + "&autoOffsetReset=" + autoOffsetReset
                + "&autoCommitEnable=false"
                + "&maxPollRecords=" + maxPollRecords
                + "&consumersCount=" + consumersCount)
                .routeId("saga-document-storage-compensation")
                .log("Received compensation command: partition=${header[kafka.PARTITION]}, offset=${header[kafka.OFFSET]}")
                .unmarshal().json(JsonLibrary.Jackson, CompensateDocumentStorageCommand.class)
                .process(exchange -> {
                    CompensateDocumentStorageCommand cmd = exchange.getIn().getBody(CompensateDocumentStorageCommand.class);
                    log.info("Processing compensation for saga: {}, document: {}",
                            cmd.getSagaId(), cmd.getDocumentId());
                    sagaCommandUseCase.handleCompensation(cmd);
                })
                .log("Successfully processed compensation command");

        // ========================================
        // SIGNEDXML_STORAGE saga step routes
        // ========================================

        // Consume ProcessSignedXmlStorageCommand from orchestrator
        from("kafka:" + sagaCommandSignedXmlTopic
                + "?brokers=" + kafkaBrokers
                + "&groupId=document-storage-signedxml"  // Unique consumer group per step
                + "&autoOffsetReset=" + autoOffsetReset
                + "&autoCommitEnable=false"
                + "&maxPollRecords=" + maxPollRecords
                + "&consumersCount=" + consumersCount)
                .routeId("saga-signedxml-storage-command")
                .log("Received signed XML storage command: partition=${header[kafka.PARTITION]}, offset=${header[kafka.OFFSET]}")
                .unmarshal().json(JsonLibrary.Jackson, ProcessSignedXmlStorageCommand.class)
                .process(exchange -> {
                    ProcessSignedXmlStorageCommand cmd = exchange.getIn().getBody(ProcessSignedXmlStorageCommand.class);
                    log.info("Processing signed XML storage command for saga: {}, document: {}",
                            cmd.getSagaId(), cmd.getDocumentId());
                    sagaCommandUseCase.handleProcessCommand(cmd);
                })
                .log("Successfully processed signed XML storage command");

        // Consume CompensateSignedXmlStorageCommand from orchestrator
        from("kafka:" + sagaCompensationSignedXmlTopic
                + "?brokers=" + kafkaBrokers
                + "&groupId=document-storage-signedxml-compensation"  // Unique consumer group
                + "&autoOffsetReset=" + autoOffsetReset
                + "&autoCommitEnable=false"
                + "&maxPollRecords=" + maxPollRecords
                + "&consumersCount=" + consumersCount)
                .routeId("saga-signedxml-storage-compensation")
                .log("Received signed XML compensation command: partition=${header[kafka.PARTITION]}, offset=${header[kafka.OFFSET]}")
                .unmarshal().json(JsonLibrary.Jackson, CompensateSignedXmlStorageCommand.class)
                .process(exchange -> {
                    CompensateSignedXmlStorageCommand cmd = exchange.getIn().getBody(CompensateSignedXmlStorageCommand.class);
                    log.info("Processing signed XML compensation for saga: {}, document: {}",
                            cmd.getSagaId(), cmd.getDocumentId());
                    sagaCommandUseCase.handleCompensation(cmd);
                })
                .log("Successfully processed signed XML compensation command");

        // ========================================
        // PDF_STORAGE saga step routes
        // ========================================

        // Consume ProcessPdfStorageCommand from orchestrator (PDF_STORAGE step)
        from("kafka:" + sagaCommandPdfStorageTopic
                + "?brokers=" + kafkaBrokers
                + "&groupId=document-storage-pdf"  // Unique consumer group per step
                + "&autoOffsetReset=" + autoOffsetReset
                + "&autoCommitEnable=false"
                + "&maxPollRecords=" + maxPollRecords
                + "&consumersCount=" + consumersCount)
                .routeId("saga-pdf-storage-command")
                .log("Received PDF storage command: partition=${header[kafka.PARTITION]}, offset=${header[kafka.OFFSET]}")
                .unmarshal().json(JsonLibrary.Jackson, ProcessPdfStorageCommand.class)
                .process(exchange -> {
                    ProcessPdfStorageCommand cmd = exchange.getIn().getBody(ProcessPdfStorageCommand.class);
                    log.info("Processing PDF storage command for saga: {}, document: {}",
                            cmd.getSagaId(), cmd.getDocumentId());
                    sagaCommandUseCase.handleProcessCommand(cmd);
                })
                .log("Successfully processed PDF storage command");

        // Consume CompensatePdfStorageCommand from orchestrator (PDF_STORAGE compensation)
        from("kafka:" + sagaCompensationPdfStorageTopic
                + "?brokers=" + kafkaBrokers
                + "&groupId=document-storage-pdf-compensation"  // Unique consumer group
                + "&autoOffsetReset=" + autoOffsetReset
                + "&autoCommitEnable=false"
                + "&maxPollRecords=" + maxPollRecords
                + "&consumersCount=" + consumersCount)
                .routeId("saga-pdf-storage-compensation")
                .log("Received PDF compensation command: partition=${header[kafka.PARTITION]}, offset=${header[kafka.OFFSET]}")
                .unmarshal().json(JsonLibrary.Jackson, CompensatePdfStorageCommand.class)
                .process(exchange -> {
                    CompensatePdfStorageCommand cmd = exchange.getIn().getBody(CompensatePdfStorageCommand.class);
                    log.info("Processing PDF storage compensation for saga: {}, document: {}",
                            cmd.getSagaId(), cmd.getDocumentId());
                    sagaCommandUseCase.handleCompensation(cmd);
                })
                .log("Successfully processed PDF storage compensation command");
    }
}
