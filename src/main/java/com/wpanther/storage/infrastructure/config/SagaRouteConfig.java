package com.wpanther.storage.infrastructure.config;

import com.wpanther.storage.application.service.SagaCommandHandler;
import com.wpanther.storage.domain.event.CompensateDocumentStorageCommand;
import com.wpanther.storage.domain.event.ProcessDocumentStorageCommand;
import lombok.extern.slf4j.Slf4j;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.model.dataformat.JsonLibrary;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Apache Camel routes for saga command and compensation consumers.
 * Consumes commands from the Saga Orchestrator and delegates to SagaCommandHandler.
 */
@Component
@Slf4j
public class SagaRouteConfig extends RouteBuilder {

    private final SagaCommandHandler sagaCommandHandler;

    @Value("${app.kafka.bootstrap-servers}")
    private String kafkaBrokers;

    @Value("${app.kafka.topics.saga-command-document-storage}")
    private String sagaCommandTopic;

    @Value("${app.kafka.topics.saga-compensation-document-storage}")
    private String sagaCompensationTopic;

    @Value("${app.kafka.topics.dlq:document-storage.dlq}")
    private String dlqTopic;

    public SagaRouteConfig(SagaCommandHandler sagaCommandHandler) {
        this.sagaCommandHandler = sagaCommandHandler;
    }

    @Override
    public void configure() throws Exception {

        errorHandler(deadLetterChannel("kafka:" + dlqTopic + "?brokers=" + kafkaBrokers)
                .maximumRedeliveries(3)
                .redeliveryDelay(1000)
                .useExponentialBackOff()
                .backOffMultiplier(2)
                .maximumRedeliveryDelay(10000)
                .logExhausted(true)
                .logStackTrace(true));

        // Consume ProcessDocumentStorageCommand from orchestrator
        from("kafka:" + sagaCommandTopic
                + "?brokers=" + kafkaBrokers
                + "&groupId=document-storage-service"
                + "&autoOffsetReset=earliest"
                + "&autoCommitEnable=false"
                + "&breakOnFirstError=true"
                + "&maxPollRecords=100"
                + "&consumersCount=3")
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
                + "&groupId=document-storage-service"
                + "&autoOffsetReset=earliest"
                + "&autoCommitEnable=false"
                + "&breakOnFirstError=true"
                + "&maxPollRecords=100"
                + "&consumersCount=3")
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
    }
}
