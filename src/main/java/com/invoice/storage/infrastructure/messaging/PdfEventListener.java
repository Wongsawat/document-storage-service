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
 * Kafka listener for PDF signed events
 * Automatically downloads and stores digitally signed PDFs
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PdfEventListener {

    private final DocumentStorageService storageService;
    private final HttpClient httpClient = HttpClient.newHttpClient();

    /**
     * Listen to pdf-storage-requested topic and store signed PDFs
     */
    @KafkaListener(
        topics = "${kafka.topics.pdf-storage-requested}",
        groupId = "${spring.kafka.consumer.group-id}",
        containerFactory = "kafkaListenerContainerFactory"
    )
    public void handlePdfSigned(PdfSignedEvent event) {
        try {
            log.info("Received PdfSignedEvent: invoiceId={}, signedPdfUrl={}, signatureLevel={}, documentType={}",
                event.getInvoiceId(), event.getSignedPdfUrl(), event.getSignatureLevel(), event.getDocumentType());

            // Download signed PDF from the URL provided by PDF signing service
            byte[] pdfContent = downloadPdf(event.getSignedPdfUrl());

            // Extract filename from URL or create default
            String fileName = extractFileName(event.getSignedPdfUrl(), event.getInvoiceNumber());

            // Map documentType to appropriate DocumentType
            DocumentType documentType = mapDocumentType(event.getDocumentType());

            // Store signed PDF document
            StoredDocument document = storageService.storeDocument(
                pdfContent,
                fileName,
                "application/pdf",
                documentType,
                event.getInvoiceId(),
                event.getInvoiceNumber()
            );

            log.info("Successfully stored signed PDF: documentId={}, fileName={}, size={} bytes, signatureLevel={}",
                document.getId(), document.getFileName(), document.getFileSize(), event.getSignatureLevel());

            // TODO: Publish DocumentStoredEvent to notify other services

        } catch (Exception e) {
            log.error("Failed to store signed PDF for invoiceId: {}", event.getInvoiceId(), e);
            // TODO: Send to dead letter queue for retry
            throw new RuntimeException("Failed to process PdfSignedEvent", e);
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

    /**
     * Map document type from PdfSignedEvent to DocumentType enum
     */
    private DocumentType mapDocumentType(String documentType) {
        if (documentType == null) {
            return DocumentType.OTHER;
        }

        return switch (documentType.toUpperCase()) {
            case "INVOICE", "TAX_INVOICE", "RECEIPT", "DEBIT_CREDIT_NOTE",
                 "CANCELLATION_NOTE", "ABBREVIATED_TAX_INVOICE" -> DocumentType.INVOICE_PDF;
            default -> {
                log.warn("Unknown document type: {}, using OTHER", documentType);
                yield DocumentType.OTHER;
            }
        };
    }
}
