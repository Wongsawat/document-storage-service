package com.invoice.storage.infrastructure.messaging;

import com.invoice.storage.application.service.DocumentStorageService;
import com.invoice.storage.domain.model.DocumentType;
import com.invoice.storage.domain.model.StoredDocument;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

/**
 * Kafka listener for PDF generated events
 * Automatically downloads and stores generated PDFs
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PdfEventListener {

    private final DocumentStorageService storageService;
    private final HttpClient httpClient = HttpClient.newHttpClient();

    /**
     * Listen to pdf.generated topic and store generated PDFs
     */
    @KafkaListener(
        topics = "${kafka.topics.pdf-generated}",
        groupId = "${spring.kafka.consumer.group-id}",
        containerFactory = "kafkaListenerContainerFactory"
    )
    public void handlePdfGenerated(PdfGeneratedEvent event) {
        try {
            log.info("Received PdfGeneratedEvent: invoiceId={}, documentUrl={}",
                event.getInvoiceId(), event.getDocumentUrl());

            // Download PDF from the URL provided by PDF generation service
            byte[] pdfContent = downloadPdf(event.getDocumentUrl());

            // Extract filename from URL or create default
            String fileName = extractFileName(event.getDocumentUrl(), event.getInvoiceNumber());

            // Store document
            StoredDocument document = storageService.storeDocument(
                pdfContent,
                fileName,
                "application/pdf",
                DocumentType.INVOICE_PDF,
                event.getInvoiceId(),
                event.getInvoiceNumber()
            );

            log.info("Successfully stored generated PDF: documentId={}, fileName={}, size={} bytes",
                document.getId(), document.getFileName(), document.getFileSize());

            // TODO: Publish DocumentStoredEvent to notify other services

        } catch (Exception e) {
            log.error("Failed to store generated PDF for invoiceId: {}", event.getInvoiceId(), e);
            // TODO: Send to dead letter queue for retry
            throw new RuntimeException("Failed to process PdfGeneratedEvent", e);
        }
    }

    /**
     * Download PDF content from URL
     */
    private byte[] downloadPdf(String url) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .GET()
                .build();

            HttpResponse<byte[]> response = httpClient.send(request,
                HttpResponse.BodyHandlers.ofByteArray());

            if (response.statusCode() != 200) {
                throw new RuntimeException("Failed to download PDF: HTTP " + response.statusCode());
            }

            return response.body();

        } catch (Exception e) {
            log.error("Error downloading PDF from URL: {}", url, e);
            throw new RuntimeException("Failed to download PDF", e);
        }
    }

    /**
     * Extract filename from URL or generate default
     */
    private String extractFileName(String url, String invoiceNumber) {
        try {
            String path = URI.create(url).getPath();
            String fileName = path.substring(path.lastIndexOf('/') + 1);

            if (fileName.isEmpty() || !fileName.toLowerCase().endsWith(".pdf")) {
                return invoiceNumber + "_invoice.pdf";
            }

            return fileName;

        } catch (Exception e) {
            log.warn("Could not extract filename from URL: {}, using default", url);
            return invoiceNumber + "_invoice.pdf";
        }
    }
}
