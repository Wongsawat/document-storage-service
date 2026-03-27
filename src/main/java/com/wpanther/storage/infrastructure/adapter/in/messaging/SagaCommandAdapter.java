package com.wpanther.storage.infrastructure.adapter.in.messaging;

import com.wpanther.saga.domain.model.SagaCommand;
import com.wpanther.storage.application.dto.event.*;
import com.wpanther.storage.application.usecase.SagaCommandUseCase;
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
        defineSagaRoute(sagaCommandTopic, "document-storage-store-document",
                "saga-document-storage-command", "saga command",
                ProcessDocumentStorageCommand.class,
                cmd -> sagaCommandUseCase.handleProcessCommand(cmd));

        defineSagaRoute(sagaCompensationTopic, "document-storage-store-document-compensation",
                "saga-document-storage-compensation", "compensation command",
                CompensateDocumentStorageCommand.class,
                cmd -> sagaCommandUseCase.handleCompensation(cmd));

        // ========================================
        // SIGNEDXML_STORAGE saga step routes
        // ========================================
        defineSagaRoute(sagaCommandSignedXmlTopic, "document-storage-signedxml",
                "saga-signedxml-storage-command", "signed XML storage command",
                ProcessSignedXmlStorageCommand.class,
                cmd -> sagaCommandUseCase.handleProcessCommand(cmd));

        defineSagaRoute(sagaCompensationSignedXmlTopic, "document-storage-signedxml-compensation",
                "saga-signedxml-storage-compensation", "signed XML compensation command",
                CompensateSignedXmlStorageCommand.class,
                cmd -> sagaCommandUseCase.handleCompensation(cmd));

        // ========================================
        // PDF_STORAGE saga step routes
        // ========================================
        defineSagaRoute(sagaCommandPdfStorageTopic, "document-storage-pdf",
                "saga-pdf-storage-command", "PDF storage command",
                ProcessPdfStorageCommand.class,
                cmd -> sagaCommandUseCase.handleProcessCommand(cmd));

        defineSagaRoute(sagaCompensationPdfStorageTopic, "document-storage-pdf-compensation",
                "saga-pdf-storage-compensation", "PDF compensation command",
                CompensatePdfStorageCommand.class,
                cmd -> sagaCommandUseCase.handleCompensation(cmd));
    }

    @FunctionalInterface
    private interface SagaCommandHandler<T extends SagaCommand> {
        void handle(T command);
    }

    private <T extends SagaCommand> void defineSagaRoute(String topic, String groupId,
                                                         String routeId, String logLabel,
                                                         Class<T> commandClass,
                                                         SagaCommandHandler<T> handler) {
        from("kafka:" + topic
                + "?brokers=" + kafkaBrokers
                + "&groupId=" + groupId
                + "&autoOffsetReset=" + autoOffsetReset
                + "&autoCommitEnable=false"
                + "&maxPollRecords=" + maxPollRecords
                + "&consumersCount=" + consumersCount)
                .routeId(routeId)
                .log("Received " + logLabel + ": partition=${header[kafka.PARTITION]}, offset=${header[kafka.OFFSET]}")
                .unmarshal().json(JsonLibrary.Jackson, commandClass)
                .process(exchange -> {
                    T cmd = exchange.getIn().getBody(commandClass);
                    log.info("Processing " + logLabel + " for saga: {}", cmd.getSagaId());
                    handler.handle(cmd);
                })
                .log("Successfully processed " + logLabel);
    }
}
