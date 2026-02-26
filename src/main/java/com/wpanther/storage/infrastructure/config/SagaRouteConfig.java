package com.wpanther.storage.infrastructure.config;

import com.wpanther.storage.application.service.PdfStorageSagaCommandHandler;
import com.wpanther.storage.application.service.SagaCommandHandler;
import com.wpanther.storage.application.service.SignedXmlStorageSagaCommandHandler;
import com.wpanther.storage.domain.event.CompensateDocumentStorageCommand;
import com.wpanther.storage.domain.event.CompensatePdfStorageCommand;
import com.wpanther.storage.domain.event.CompensateSignedXmlStorageCommand;
import com.wpanther.storage.domain.event.ProcessDocumentStorageCommand;
import com.wpanther.storage.domain.event.ProcessPdfStorageCommand;
import com.wpanther.storage.domain.event.ProcessSignedXmlStorageCommand;
import lombok.extern.slf4j.Slf4j;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.model.dataformat.JsonLibrary;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Apache Camel routes for saga command and compensation consumers.
 * Consumes commands from the Saga Orchestrator and delegates to the appropriate handler.
 * <p>
 * Configuration:
 * - Separate consumer groups per saga step to prevent cross-contamination
 * - Configurable autoOffsetReset (earliest for dev, latest for production)
 * - Manual offset control for exactly-once semantics with outbox pattern
 * - Dead Letter Channel with exponential backoff for error handling
 */
@Component
@Slf4j
public class SagaRouteConfig extends RouteBuilder {

    private final SagaCommandHandler sagaCommandHandler;
    private final SignedXmlStorageSagaCommandHandler signedXmlStorageSagaCommandHandler;
    private final PdfStorageSagaCommandHandler pdfStorageSagaCommandHandler;

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

    public SagaRouteConfig(SagaCommandHandler sagaCommandHandler,
                           SignedXmlStorageSagaCommandHandler signedXmlStorageSagaCommandHandler,
                           PdfStorageSagaCommandHandler pdfStorageSagaCommandHandler) {
        this.sagaCommandHandler = sagaCommandHandler;
        this.signedXmlStorageSagaCommandHandler = signedXmlStorageSagaCommandHandler;
        this.pdfStorageSagaCommandHandler = pdfStorageSagaCommandHandler;
    }

    @Override
    public void configure() throws Exception {

        // Global error handler with Dead Letter Channel
        errorHandler(deadLetterChannel("kafka:" + dlqTopic + "?brokers=" + kafkaBrokers)
                .maximumRedeliveries(maxRedeliveries)
                .redeliveryDelay(1000)
                .useExponentialBackOff()
                .backOffMultiplier(2)
                .maximumRedeliveryDelay(10000)
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
                .routeId("saga-command-consumer")
                .log("Received saga command: partition=${header[kafka.PARTITION]}, offset=${header[kafka.OFFSET]}")
                .unmarshal().json(JsonLibrary.Jackson, ProcessDocumentStorageCommand.class)
                .process(exchange -> {
                    ProcessDocumentStorageCommand cmd = exchange.getIn().getBody(ProcessDocumentStorageCommand.class);
                    log.info("Processing saga command for saga: {}, document: {}",
                            cmd.getSagaId(), cmd.getDocumentId());
                    sagaCommandHandler.handleProcessCommand(cmd);
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
                .routeId("saga-compensation-consumer")
                .log("Received compensation command: partition=${header[kafka.PARTITION]}, offset=${header[kafka.OFFSET]}")
                .unmarshal().json(JsonLibrary.Jackson, CompensateDocumentStorageCommand.class)
                .process(exchange -> {
                    CompensateDocumentStorageCommand cmd = exchange.getIn().getBody(CompensateDocumentStorageCommand.class);
                    log.info("Processing compensation for saga: {}, document: {}",
                            cmd.getSagaId(), cmd.getDocumentId());
                    sagaCommandHandler.handleCompensation(cmd);
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
                .routeId("saga-signedxml-command-consumer")
                .log("Received signed XML storage command: partition=${header[kafka.PARTITION]}, offset=${header[kafka.OFFSET]}")
                .unmarshal().json(JsonLibrary.Jackson, ProcessSignedXmlStorageCommand.class)
                .process(exchange -> {
                    ProcessSignedXmlStorageCommand cmd = exchange.getIn().getBody(ProcessSignedXmlStorageCommand.class);
                    log.info("Processing signed XML storage command for saga: {}, document: {}",
                            cmd.getSagaId(), cmd.getDocumentId());
                    signedXmlStorageSagaCommandHandler.handleProcessCommand(cmd);
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
                .routeId("saga-signedxml-compensation-consumer")
                .log("Received signed XML compensation command: partition=${header[kafka.PARTITION]}, offset=${header[kafka.OFFSET]}")
                .unmarshal().json(JsonLibrary.Jackson, CompensateSignedXmlStorageCommand.class)
                .process(exchange -> {
                    CompensateSignedXmlStorageCommand cmd = exchange.getIn().getBody(CompensateSignedXmlStorageCommand.class);
                    log.info("Processing signed XML compensation for saga: {}, document: {}",
                            cmd.getSagaId(), cmd.getDocumentId());
                    signedXmlStorageSagaCommandHandler.handleCompensation(cmd);
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
                .routeId("saga-pdf-storage-command-consumer")
                .log("Received PDF storage command: partition=${header[kafka.PARTITION]}, offset=${header[kafka.OFFSET]}")
                .unmarshal().json(JsonLibrary.Jackson, ProcessPdfStorageCommand.class)
                .process(exchange -> {
                    ProcessPdfStorageCommand cmd = exchange.getIn().getBody(ProcessPdfStorageCommand.class);
                    log.info("Processing PDF storage command for saga: {}, document: {}",
                            cmd.getSagaId(), cmd.getDocumentId());
                    pdfStorageSagaCommandHandler.handleProcessCommand(cmd);
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
                .routeId("saga-pdf-storage-compensation-consumer")
                .log("Received PDF storage compensation command: partition=${header[kafka.PARTITION]}, offset=${header[kafka.OFFSET]}")
                .unmarshal().json(JsonLibrary.Jackson, CompensatePdfStorageCommand.class)
                .process(exchange -> {
                    CompensatePdfStorageCommand cmd = exchange.getIn().getBody(CompensatePdfStorageCommand.class);
                    log.info("Processing PDF storage compensation for saga: {}, document: {}",
                            cmd.getSagaId(), cmd.getDocumentId());
                    pdfStorageSagaCommandHandler.handleCompensation(cmd);
                })
                .log("Successfully processed PDF storage compensation command");
    }
}
