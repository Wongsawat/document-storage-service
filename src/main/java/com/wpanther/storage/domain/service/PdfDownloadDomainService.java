package com.wpanther.storage.domain.service;

import com.wpanther.storage.domain.exception.StorageFailedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Domain service for downloading PDFs from external URLs.
 * Used by SagaOrchestrationService to download PDFs from MinIO.
 */
@Service
public class PdfDownloadDomainService {

    private static final Logger log = LoggerFactory.getLogger(PdfDownloadDomainService.class);
    private static final int CONNECT_TIMEOUT_SECONDS = 30;
    private static final int REQUEST_TIMEOUT_SECONDS = 60;

    private final HttpClient httpClient;

    public PdfDownloadDomainService() {
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(CONNECT_TIMEOUT_SECONDS))
            .build();
    }

    /**
     * Download a PDF from the given URL.
     * @param pdfUrl URL of the PDF to download
     * @return PDF content as byte array
     * @throws StorageFailedException if download fails
     */
    public byte[] downloadPdf(String pdfUrl) {
        log.info("Downloading PDF from: {}", pdfUrl);

        try {
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(pdfUrl))
                .timeout(Duration.ofSeconds(REQUEST_TIMEOUT_SECONDS))
                .GET()
                .build();

            HttpResponse<byte[]> response = httpClient.send(
                request,
                HttpResponse.BodyHandlers.ofByteArray()
            );

            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                log.info("Successfully downloaded PDF, size: {} bytes", response.body().length);
                return response.body();
            } else {
                throw new StorageFailedException(
                    "Failed to download PDF from " + pdfUrl +
                    ", HTTP status: " + response.statusCode()
                );
            }
        } catch (IOException | InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new StorageFailedException("Failed to download PDF from " + pdfUrl, e);
        }
    }

    /**
     * Download content from the given URL as String (for XML, JSON, etc).
     * @param url URL of the content to download
     * @return Content as String
     * @throws StorageFailedException if download fails
     */
    public String downloadContent(String url) {
        log.info("Downloading content from: {}", url);

        try {
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(REQUEST_TIMEOUT_SECONDS))
                .GET()
                .build();

            HttpResponse<String> response = httpClient.send(
                request,
                HttpResponse.BodyHandlers.ofString()
            );

            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                log.info("Successfully downloaded content, size: {} bytes", response.body().length());
                return response.body();
            } else {
                throw new StorageFailedException(
                    "Failed to download content from " + url +
                    ", HTTP status: " + response.statusCode()
                );
            }
        } catch (IOException | InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new StorageFailedException("Failed to download content from " + url, e);
        }
    }
}
