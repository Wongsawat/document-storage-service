package com.wpanther.storage.application.service;

import com.wpanther.storage.domain.model.DocumentType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

/**
 * Service for downloading PDFs from URLs.
 *
 * Extracted from PdfEventListener for reuse in Camel routes.
 * Provides methods to download PDFs, extract filenames, and map document types.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PdfDownloadService {

    private final HttpClient httpClient;

    /**
     * Download PDF content from URL.
     *
     * @param url the URL to download from
     * @return byte array of PDF content
     * @throws RuntimeException if download fails
     */
    public byte[] downloadPdf(String url) {
        try {
            log.debug("Downloading PDF from URL: {}", url);

            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .GET()
                .build();

            HttpResponse<byte[]> response = httpClient.send(request,
                HttpResponse.BodyHandlers.ofByteArray());

            if (response.statusCode() != 200) {
                throw new RuntimeException("Failed to download PDF: HTTP " + response.statusCode());
            }

            log.debug("Downloaded PDF: {} bytes", response.body().length);
            return response.body();

        } catch (Exception e) {
            log.error("Error downloading PDF from URL: {}", url, e);
            throw new RuntimeException("Failed to download PDF", e);
        }
    }

    /**
     * Extract filename from URL or generate default.
     *
     * @param url the source URL
     * @param invoiceNumber fallback invoice number for filename
     * @return extracted or generated filename
     */
    public String extractFileName(String url, String invoiceNumber) {
        try {
            String path = URI.create(url).getPath();
            String fileName = path.substring(path.lastIndexOf('/') + 1);

            if (fileName.isEmpty() || !fileName.toLowerCase().endsWith(".pdf")) {
                return invoiceNumber + "_signed.pdf";
            }

            return fileName;

        } catch (Exception e) {
            log.warn("Could not extract filename from URL: {}, using default", url);
            return invoiceNumber + "_signed.pdf";
        }
    }

    /**
     * Map document type string to DocumentType enum.
     *
     * Maps document types from PdfSignedEvent to the appropriate DocumentType enum value.
     * All invoice-like documents map to INVOICE_PDF for storage purposes.
     *
     * @param documentType the document type string from event
     * @return mapped DocumentType enum value
     */
    public DocumentType mapDocumentType(String documentType) {
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
