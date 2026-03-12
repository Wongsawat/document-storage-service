package com.wpanther.storage.infrastructure.adapter.out.http;

import com.wpanther.storage.application.port.out.PdfDownloadPort;
import com.wpanther.storage.domain.exception.StorageFailedException;
import com.wpanther.storage.infrastructure.config.metrics.DocumentStorageMetricsService;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Infrastructure adapter for downloading files from external URLs.
 * <p>
 * Implements {@link PdfDownloadPort} for downloading PDFs and other content from
 * external sources like MinIO.
 * Protected with circuit breaker, retry, and timeout patterns for resilience.
 * </p>
 * <p>
 * <b>Resilience Patterns Applied:</b>
 * <ul>
 *   <li><b>Circuit Breaker:</b> Stops calls after repeated failures (pdfDownloadService)</li>
 *   <li><b>Retry:</b> Retries transient failures (pdfDownloadRetry)</li>
 *   <li><b>Metrics:</b> Records PDF download success/failure rates and latency</li>
 * </ul>
 * </p>
 */
@Component
public class PdfDownloadAdapter implements PdfDownloadPort {

    private static final Logger log = LoggerFactory.getLogger(PdfDownloadAdapter.class);
    private static final int CONNECT_TIMEOUT_SECONDS = 30;
    private static final int REQUEST_TIMEOUT_SECONDS = 60;

    private final HttpClient httpClient;
    private final DocumentStorageMetricsService metrics;

    public PdfDownloadAdapter(DocumentStorageMetricsService metrics) {
        this.metrics = metrics;
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(CONNECT_TIMEOUT_SECONDS))
            .build();
    }

    /**
     * Download a PDF from the given URL with resilience patterns.
     *
     * @param pdfUrl URL of the PDF to download
     * @return PDF content as byte array
     * @throws StorageFailedException if download fails after retries and circuit breaker is open
     */
    @Override
    @CircuitBreaker(name = "pdfDownloadService", fallbackMethod = "downloadPdfFallback")
    @Retry(name = "pdfDownloadRetry", fallbackMethod = "downloadPdfRetryFallback")
    public byte[] downloadPdf(String pdfUrl) {
        log.info("Downloading PDF from: {}", pdfUrl);

        long startTime = System.currentTimeMillis();

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

            long duration = System.currentTimeMillis() - startTime;

            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                log.info("Successfully downloaded PDF, size: {} bytes", response.body().length);
                metrics.recordPdfDownloadSuccess(duration);
                return response.body();
            } else {
                metrics.recordPdfDownloadFailure(duration);
                throw new StorageFailedException(
                    "Failed to download PDF from " + pdfUrl +
                    ", HTTP status: " + response.statusCode()
                );
            }
        } catch (IOException | InterruptedException e) {
            Thread.currentThread().interrupt();
            long duration = System.currentTimeMillis() - startTime;
            metrics.recordPdfDownloadFailure(duration);
            throw new StorageFailedException("Failed to download PDF from " + pdfUrl, e);
        }
    }

    /**
     * Fallback method when circuit breaker is open.
     *
     * @param pdfUrl the URL that failed
     * @param exception the exception that triggered the fallback
     * @return empty byte array (will be handled as failure by caller)
     */
    @SuppressWarnings("unused") // Called by CircuitBreaker annotation
    private byte[] downloadPdfFallback(String pdfUrl, Exception exception) {
        log.error("Circuit breaker OPEN for PDF download from: {}, error: {}",
                pdfUrl, exception.getMessage());
        throw new StorageFailedException(
            "PDF download service is temporarily unavailable (circuit breaker open). " +
            "Please retry later. URL: " + pdfUrl,
            exception
        );
    }

    /**
     * Fallback method when retries are exhausted.
     *
     * @param pdfUrl the URL that failed
     * @param exception the exception that triggered the fallback
     * @return empty byte array
     */
    @SuppressWarnings("unused") // Called by Retry annotation
    private byte[] downloadPdfRetryFallback(String pdfUrl, Exception exception) {
        log.error("Retries exhausted for PDF download from: {}, error: {}",
                pdfUrl, exception.getMessage());
        throw new StorageFailedException(
            "PDF download failed after all retry attempts. URL: " + pdfUrl,
            exception
        );
    }

    /**
     * Download content from the given URL as String (for XML, JSON, etc).
     *
     * @param url URL of the content to download
     * @return Content as String
     * @throws StorageFailedException if download fails after retries and circuit breaker is open
     */
    @Override
    @CircuitBreaker(name = "externalHttpService", fallbackMethod = "downloadContentFallback")
    @Retry(name = "externalHttpRetry", fallbackMethod = "downloadContentRetryFallback")
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

    /**
     * Fallback method when circuit breaker is open for content download.
     *
     * @param url the URL that failed
     * @param exception the exception that triggered the fallback
     * @return empty string (will be handled as failure by caller)
     */
    @SuppressWarnings("unused") // Called by CircuitBreaker annotation
    private String downloadContentFallback(String url, Exception exception) {
        log.error("Circuit breaker OPEN for content download from: {}, error: {}",
                url, exception.getMessage());
        throw new StorageFailedException(
            "Content download service is temporarily unavailable (circuit breaker open). " +
            "Please retry later. URL: " + url,
            exception
        );
    }

    /**
     * Fallback method when retries are exhausted for content download.
     *
     * @param url the URL that failed
     * @param exception the exception that triggered the fallback
     * @return empty string
     */
    @SuppressWarnings("unused") // Called by Retry annotation
    private String downloadContentRetryFallback(String url, Exception exception) {
        log.error("Retries exhausted for content download from: {}, error: {}",
                url, exception.getMessage());
        throw new StorageFailedException(
            "Content download failed after all retry attempts. URL: " + url,
            exception
        );
    }
}
