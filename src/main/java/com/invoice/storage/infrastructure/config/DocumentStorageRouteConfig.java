package com.invoice.storage.infrastructure.config;

import com.invoice.storage.application.service.DocumentStorageService;
import com.invoice.storage.application.service.PdfDownloadService;
import com.invoice.storage.domain.event.DocumentStoredEvent;
import com.invoice.storage.domain.model.DocumentType;
import com.invoice.storage.domain.model.StoredDocument;
import com.invoice.storage.infrastructure.messaging.EventPublisher;
import com.invoice.storage.infrastructure.messaging.PdfSignedEvent;
import lombok.extern.slf4j.Slf4j;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.model.dataformat.JsonLibrary;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Apache Camel routes for document storage operations.
 * Replaces Spring Kafka consumer and producer configuration.
 *
 * Routes:
 * - pdf-storage-consumer: Consumes PdfSignedEvent, downloads PDF, stores document
 * - document-stored-producer: Publishes DocumentStoredEvent after successful storage
 */
@Component
@Slf4j
public class DocumentStorageRouteConfig extends RouteBuilder {

    private final DocumentStorageService storageService;
    private final PdfDownloadService downloadService;
    private final EventPublisher eventPublisher;

    @Value("${app.kafka.bootstrap-servers}")
    private String kafkaBrokers;

    @Value("${app.kafka.topics.pdf-storage-requested}")
    private String pdfStorageRequestedTopic;

    @Value("${app.kafka.topics.document-stored}")
    private String documentStoredTopic;

    @Value("${app.kafka.topics.dlq}")
    private String dlqTopic;

    public DocumentStorageRouteConfig(
            DocumentStorageService storageService,
            PdfDownloadService downloadService,
            EventPublisher eventPublisher) {
        this.storageService = storageService;
        this.downloadService = downloadService;
        this.eventPublisher = eventPublisher;
    }

    @Override
    public void configure() throws Exception {

        // ============================================================
        // GLOBAL ERROR HANDLER - Dead Letter Channel with retries
        // ============================================================
        errorHandler(deadLetterChannel("kafka:" + dlqTopic + "?brokers=" + kafkaBrokers)
            .maximumRedeliveries(3)
            .redeliveryDelay(1000)
            .useExponentialBackOff()
            .backOffMultiplier(2)
            .maximumRedeliveryDelay(10000)
            .logExhausted(true)
            .logStackTrace(true)
            .logRetryAttempted(true)
            .retryAttemptedLogLevel(org.apache.camel.LoggingLevel.WARN));

        // ============================================================
        // CONSUMER ROUTE: pdf-storage-requested
        // ============================================================
        from("kafka:" + pdfStorageRequestedTopic
                + "?brokers=" + kafkaBrokers
                + "&groupId=document-storage-service"
                + "&autoOffsetReset=earliest"
                + "&autoCommitEnable=false"
                + "&breakOnFirstError=true"
                + "&maxPollRecords=10"
                + "&consumersCount=3")
            .routeId("pdf-storage-consumer")
            .log("Received PdfSignedEvent: partition=${header[kafka.PARTITION]}, offset=${header[kafka.OFFSET]}")

            // Step 1: Unmarshal JSON to PdfSignedEvent
            .unmarshal().json(JsonLibrary.Jackson, PdfSignedEvent.class)

            // Step 2: Process the event - download PDF and store document
            .process(exchange -> {
                PdfSignedEvent event = exchange.getIn().getBody(PdfSignedEvent.class);
                log.info("Processing PdfSignedEvent: invoiceId={}, signedPdfUrl={}, documentType={}",
                    event.getInvoiceId(), event.getSignedPdfUrl(), event.getDocumentType());

                // Download signed PDF from the URL
                byte[] pdfContent = downloadService.downloadPdf(event.getSignedPdfUrl());

                // Extract filename from URL or create default
                String fileName = downloadService.extractFileName(
                    event.getSignedPdfUrl(), event.getInvoiceNumber());

                // Map documentType to DocumentType enum
                DocumentType documentType = downloadService.mapDocumentType(event.getDocumentType());

                // Store document (calculates checksum, saves to storage, persists metadata)
                StoredDocument document = storageService.storeDocument(
                    pdfContent,
                    fileName,
                    "application/pdf",
                    documentType,
                    event.getInvoiceId(),
                    event.getInvoiceNumber()
                );

                log.info("Successfully stored document: documentId={}, fileName={}, size={} bytes",
                    document.getId(), document.getFileName(), document.getFileSize());

                // Create and publish DocumentStoredEvent
                DocumentStoredEvent storedEvent = DocumentStoredEvent.builder()
                    .eventId(UUID.randomUUID().toString())
                    .eventType("DOCUMENT_STORED")
                    .occurredAt(LocalDateTime.now())
                    .version("1.0")
                    .correlationId(event.getCorrelationId())
                    .documentId(document.getId())
                    .invoiceId(event.getInvoiceId())
                    .invoiceNumber(event.getInvoiceNumber())
                    .fileName(document.getFileName())
                    .storageUrl(document.getStorageUrl())
                    .fileSize(document.getFileSize())
                    .checksum(document.getChecksum())
                    .documentType(document.getDocumentType().name())
                    .signedDocumentId(event.getSignedDocumentId())
                    .signatureLevel(event.getSignatureLevel())
                    .signatureTimestamp(event.getSignatureTimestamp())
                    .build();

                // Publish event to downstream consumers
                eventPublisher.publishDocumentStored(storedEvent);
            })

            .log("Successfully processed and published document storage event");

        // ============================================================
        // PRODUCER ROUTE: document.stored
        // ============================================================
        from("direct:publish-document-stored")
            .routeId("document-stored-producer")
            .log("Publishing DocumentStoredEvent: documentId=${body.documentId}, invoiceNumber=${body.invoiceNumber}")
            .marshal().json(JsonLibrary.Jackson)
            .to("kafka:" + documentStoredTopic
                + "?brokers=" + kafkaBrokers
                + "&key=${header.kafka.KEY}")
            .log("Published DocumentStoredEvent to " + documentStoredTopic);
    }
}
