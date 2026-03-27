package com.wpanther.storage.infrastructure.config.metrics;

import com.wpanther.storage.application.port.out.MetricsPort;
import com.wpanther.storage.domain.model.DocumentType;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Service for tracking custom business metrics for document storage operations.
 * <p>
 * This service provides Micrometer-based metrics that go beyond standard JVM metrics,
 * giving visibility into business operations like document storage, PDF downloads,
 * and orphaned document detection.
 * </p>
 * <p>
 * <b>Metrics exposed to Prometheus:</b>
 * <ul>
 *   <li><code>document_storage_stored_total</code> - Counter of documents stored (by type)</li>
 *   <li><code>document_storage_deleted_total</code> - Counter of documents deleted</li>
 *   <li><code>document_storage_retrieved_total</code> - Counter of documents retrieved</li>
 *   <li><code>document_storage_storage_duration_seconds</code> - Timer for storage operations</li>
 *   <li><code>document_storage_pdf_download_success_total</code> - Counter of successful PDF downloads</li>
 *   <li><code>document_storage_pdf_download_failure_total</code> - Counter of failed PDF downloads</li>
 *   <li><code>document_storage_pdf_download_duration_seconds</code> - Timer for PDF download operations</li>
 *   <li><code>document_storage_orphaned_documents</code> - Gauge for current orphaned document count</li>
 *   <li><code>document_storage_reconciliation_run_total</code> - Counter of reconciliation runs</li>
 *   <li><code>document_storage_reconciliation_orphans_found_total</code> - Counter of orphans found</li>
 * </ul>
 * </p>
 * <p>
 * <b>Accessing metrics:</b>
 * <pre>
 * curl http://localhost:8084/actuator/prometheus
 * </pre>
 * </p>
 */
public class DocumentStorageMetricsService implements MetricsPort {

    private static final Logger log = LoggerFactory.getLogger(DocumentStorageMetricsService.class);

    private final MeterRegistry meterRegistry;
    private final AtomicLong orphanedDocumentsCount;

    // Counters for document operations
    private Counter documentStoredCounter;
    private Counter documentDeletedCounter;
    private Counter documentRetrievedCounter;
    private Counter pdfDownloadSuccessCounter;
    private Counter pdfDownloadFailureCounter;
    private Counter reconciliationRunCounter;
    private Counter orphansFoundCounter;

    // Timers for operation latency
    private Timer storageOperationTimer;
    private Timer pdfDownloadTimer;

    /**
     * Create a new metrics service.
     *
     * @param meterRegistry the Micrometer meter registry
     */
    public DocumentStorageMetricsService(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
        this.orphanedDocumentsCount = new AtomicLong(0);
        initializeMetrics();
    }

    /**
     * Initialize all metrics and register with Micrometer.
     */
    private void initializeMetrics() {
        // Document operation counters
        this.documentStoredCounter = Counter.builder("document_storage_stored_total")
                .description("Total number of documents stored")
                .tag("service", "document-storage")
                .register(meterRegistry);

        this.documentDeletedCounter = Counter.builder("document_storage_deleted_total")
                .description("Total number of documents deleted")
                .tag("service", "document-storage")
                .register(meterRegistry);

        this.documentRetrievedCounter = Counter.builder("document_storage_retrieved_total")
                .description("Total number of documents retrieved")
                .tag("service", "document-storage")
                .register(meterRegistry);

        // PDF download counters
        this.pdfDownloadSuccessCounter = Counter.builder("document_storage_pdf_download_success_total")
                .description("Total number of successful PDF downloads")
                .tag("service", "document-storage")
                .tag("operation", "pdf_download")
                .tag("status", "success")
                .register(meterRegistry);

        this.pdfDownloadFailureCounter = Counter.builder("document_storage_pdf_download_failure_total")
                .description("Total number of failed PDF downloads")
                .tag("service", "document-storage")
                .tag("operation", "pdf_download")
                .tag("status", "failure")
                .register(meterRegistry);

        // Reconciliation counters
        this.reconciliationRunCounter = Counter.builder("document_storage_reconciliation_run_total")
                .description("Total number of reconciliation runs")
                .tag("service", "document-storage")
                .tag("operation", "reconciliation")
                .register(meterRegistry);

        this.orphansFoundCounter = Counter.builder("document_storage_reconciliation_orphans_found_total")
                .description("Total number of orphaned documents found")
                .tag("service", "document-storage")
                .tag("operation", "reconciliation")
                .register(meterRegistry);

        // Storage operation timer with percentiles
        this.storageOperationTimer = Timer.builder("document_storage_storage_duration_seconds")
                .description("Time taken to store documents")
                .tag("service", "document-storage")
                .tag("operation", "storage")
                .publishPercentiles(0.5, 0.95, 0.99) // p50, p95, p99
                .publishPercentileHistogram()
                .sla(java.time.Duration.ofMillis(100),
                     java.time.Duration.ofMillis(500),
                     java.time.Duration.ofMillis(1000),
                     java.time.Duration.ofMillis(5000))
                .minimumExpectedValue(java.time.Duration.ofMillis(1))
                .maximumExpectedValue(java.time.Duration.ofSeconds(30))
                .register(meterRegistry);

        // PDF download timer with percentiles
        this.pdfDownloadTimer = Timer.builder("document_storage_pdf_download_duration_seconds")
                .description("Time taken to download PDFs")
                .tag("service", "document-storage")
                .tag("operation", "pdf_download")
                .publishPercentiles(0.5, 0.95, 0.99) // p50, p95, p99
                .publishPercentileHistogram()
                .sla(java.time.Duration.ofMillis(100),
                     java.time.Duration.ofMillis(500),
                     java.time.Duration.ofMillis(1000),
                     java.time.Duration.ofSeconds(5),
                     java.time.Duration.ofSeconds(10))
                .minimumExpectedValue(java.time.Duration.ofMillis(1))
                .maximumExpectedValue(java.time.Duration.ofSeconds(60))
                .register(meterRegistry);

        // Gauge for orphaned documents (current count, not cumulative)
        Gauge.builder("document_storage_orphaned_documents", orphanedDocumentsCount, AtomicLong::get)
                .description("Current number of orphaned documents")
                .tag("service", "document-storage")
                .tag("status", "orphaned")
                .register(meterRegistry);

        log.info("Custom business metrics initialized and registered with Micrometer");
    }

    // ========== Document Storage Metrics ==========

    /**
     * Record a document storage operation.
     *
     * @param documentType the type of document stored
     */
    @Override
    public void recordDocumentStored(DocumentType documentType) {
        Counter.builder("document_storage_stored_total")
                .description("Total number of documents stored")
                .tag("service", "document-storage")
                .tag("document_type", documentType.name().toLowerCase())
                .register(meterRegistry)
                .increment();

        documentStoredCounter.increment();
        log.debug("Recorded document stored metric for type: {}", documentType);
    }

    /**
     * Record a document deletion operation.
     */
    @Override
    public void recordDocumentDeleted() {
        documentDeletedCounter.increment();
        log.debug("Recorded document deleted metric");
    }

    /**
     * Record a document retrieval operation.
     */
    public void recordDocumentRetrieved() {
        documentRetrievedCounter.increment();
        log.debug("Recorded document retrieved metric");
    }

    /**
     * Record the duration of a storage operation.
     *
     * @param durationMillis the duration in milliseconds
     */
    public void recordStorageDuration(long durationMillis) {
        storageOperationTimer.record(durationMillis, TimeUnit.MILLISECONDS);
        log.debug("Recorded storage duration: {}ms", durationMillis);
    }

    /**
     * Record a storage operation with automatic timing.
     * <p>
     * Use this method with try-with-resources for automatic timing:
     * <pre>
     * try (var unused = metricsService.timeStorageOperation()) {
     *     // storage operation here
     * }
     * </pre>
     *
     * @return an AutoCloseable that records elapsed time on close
     */
    @Override
    public Runnable timeStorageOperation() {
        Timer.Sample sample = Timer.start(meterRegistry);
        return () -> sample.stop(storageOperationTimer);
    }

    /**
     * Stop recording a storage operation.
     *
     * @param sample the timer sample to stop
     */
    public void stopStorageOperation(Timer.Sample sample) {
        sample.stop(storageOperationTimer);
    }

    // ========== PDF Download Metrics ==========

    /**
     * Record a successful PDF download.
     *
     * @param durationMillis the download duration in milliseconds
     */
    public void recordPdfDownloadSuccess(long durationMillis) {
        pdfDownloadSuccessCounter.increment();
        pdfDownloadTimer.record(durationMillis, TimeUnit.MILLISECONDS);
        log.debug("Recorded successful PDF download in {}ms", durationMillis);
    }

    /**
     * Record a failed PDF download.
     *
     * @param durationMillis the download duration before failure
     */
    public void recordPdfDownloadFailure(long durationMillis) {
        pdfDownloadFailureCounter.increment();
        if (durationMillis > 0) {
            pdfDownloadTimer.record(durationMillis, TimeUnit.MILLISECONDS);
        }
        log.debug("Recorded failed PDF download after {}ms", durationMillis);
    }

    /**
     * Get the PDF download success rate.
     *
     * @return success rate as a percentage (0-100), or 0 if no downloads attempted
     */
    public double getPdfDownloadSuccessRate() {
        double total = pdfDownloadSuccessCounter.count() + pdfDownloadFailureCounter.count();
        return total > 0 ? (pdfDownloadSuccessCounter.count() / (double) total) * 100 : 0;
    }

    // ========== Orphaned Document Metrics ==========

    /**
     * Update the current orphaned document count.
     * <p>
     * This updates the gauge metric that tracks the current number
     * of orphaned documents (not cumulative).
     *
     * @param count the current orphaned document count
     */
    public void updateOrphanedDocumentsCount(long count) {
        orphanedDocumentsCount.set(count);
        log.debug("Updated orphaned documents count to: {}", count);
    }

    /**
     * Increment the orphaned documents count.
     */
    public void incrementOrphanedDocumentsCount() {
        orphanedDocumentsCount.incrementAndGet();
        log.debug("Incremented orphaned documents count");
    }

    /**
     * Decrement the orphaned documents count.
     */
    public void decrementOrphanedDocumentsCount() {
        orphanedDocumentsCount.decrementAndGet();
        log.debug("Decremented orphaned documents count");
    }

    /**
     * Get the current orphaned documents count.
     *
     * @return current count
     */
    public long getOrphanedDocumentsCount() {
        return orphanedDocumentsCount.get();
    }

    // ========== Reconciliation Metrics ==========

    /**
     * Record a reconciliation run.
     *
     * @param orphanedFound the number of orphans found in this run
     */
    public void recordReconciliationRun(long orphanedFound) {
        reconciliationRunCounter.increment();
        if (orphanedFound > 0) {
            orphansFoundCounter.increment(orphanedFound);
            updateOrphanedDocumentsCount(orphanedFound);
        }
        log.debug("Recorded reconciliation run with {} orphans found", orphanedFound);
    }

    /**
     * Record orphans found during reconciliation.
     *
     * @param count the number of orphans found
     */
    public void recordOrphansFound(long count) {
        orphansFoundCounter.increment(count);
        updateOrphanedDocumentsCount(count);
        log.debug("Recorded {} orphans found", count);
    }

    // ========== Metric Accessors for Testing ==========

    /**
     * Get the total documents stored counter.
     *
     * @return total count
     */
    public double getTotalDocumentsStored() {
        return documentStoredCounter.count();
    }

    /**
     * Get the total documents deleted counter.
     *
     * @return total count
     */
    public double getTotalDocumentsDeleted() {
        return documentDeletedCounter.count();
    }

    /**
     * Get the total documents retrieved counter.
     *
     * @return total count
     */
    public double getTotalDocumentsRetrieved() {
        return documentRetrievedCounter.count();
    }

    /**
     * Get the total successful PDF downloads counter.
     *
     * @return total count
     */
    public double getTotalPdfDownloadSuccesses() {
        return pdfDownloadSuccessCounter.count();
    }

    /**
     * Get the total failed PDF downloads counter.
     *
     * @return total count
     */
    public double getTotalPdfDownloadFailures() {
        return pdfDownloadFailureCounter.count();
    }

    /**
     * Get the total reconciliation runs counter.
     *
     * @return total count
     */
    public double getTotalReconciliationRuns() {
        return reconciliationRunCounter.count();
    }

    /**
     * Get the total orphans found counter.
     *
     * @return total count
     */
    public double getTotalOrphansFound() {
        return orphansFoundCounter.count();
    }
}
