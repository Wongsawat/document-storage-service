package com.wpanther.storage.infrastructure.adapter.in.scheduler;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wpanther.saga.domain.outbox.OutboxStatus;
import com.wpanther.storage.domain.repository.DocumentRepositoryPort;
import com.wpanther.storage.domain.model.StoredDocument;
import com.wpanther.storage.infrastructure.adapter.out.persistence.outbox.OutboxEventEntity;
import com.wpanther.storage.infrastructure.adapter.out.persistence.outbox.SpringDataOutboxRepository;
import com.wpanther.storage.infrastructure.config.metrics.DocumentStorageMetricsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Reconciliation service for detecting and handling orphaned documents.
 * <p>
 * This service addresses the dual-write consistency problem where documents are stored
 * in MongoDB but outbox events fail to be persisted in PostgreSQL. The reconciliation
 * job periodically checks for orphaned documents and triggers compensation.
 * </p>
 * <p>
 * An orphaned document is one that:
 * <ul>
 *   <li>Exists in MongoDB (StoredDocumentEntity)</li>
 *   <li>Has no corresponding outbox event published within the configured window</li>
 * </ul>
 * </p>
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class OutboxReconciliationService {

    private final DocumentRepositoryPort documentRepositoryPort;
    private final SpringDataOutboxRepository outboxRepository;
    private final DocumentStorageMetricsService metrics;
    private final ObjectMapper objectMapper;

    @Value("${app.reconciliation.batch-size:100}")
    private int batchSize;

    @Value("${app.reconciliation.lookback-minutes:60}")
    private int lookbackMinutes;

    @Value("${app.reconciliation.enabled:true}")
    private boolean enabled;

    @Value("${app.reconciliation.retention-days:30}")
    private int retentionDays;

    /**
     * Scheduled job to find and reconcile orphaned documents.
     * <p>
     * Runs every 15 minutes to check for documents without corresponding
     * outbox events.
     * </p>
     */
    @Scheduled(cron = "${app.reconciliation.cron:0 */15 * * * *}")
    @Transactional
    public void reconcileOrphanedDocuments() {
        if (!enabled) {
            log.debug("Reconciliation is disabled");
            return;
        }

        log.info("Starting orphaned document reconciliation (lookback: {} minutes)", lookbackMinutes);

        try {
            LocalDateTime cutoffTime = LocalDateTime.now().minusMinutes(lookbackMinutes);
            List<StoredDocument> recentDocuments = documentRepositoryPort.findByCreatedAtAfter(cutoffTime);

            int orphanedCount = 0;

            for (StoredDocument doc : recentDocuments) {
                if (!hasAnyOutboxEvent(doc.getId())) {
                    log.warn("Found orphaned document: id={}, type={}, createdAt={}",
                            doc.getId(), doc.getDocumentType(), doc.getCreatedAt());

                    handleOrphanedDocument(doc);
                    orphanedCount++;
                }
            }

            // Record reconciliation metrics
            metrics.recordReconciliationRun(orphanedCount);

            log.info("Reconciliation complete: processed {} documents, {} orphaned",
                    recentDocuments.size(), orphanedCount);

        } catch (Exception e) {
            log.error("Error during reconciliation", e);
        }
    }

    /**
     * Scheduled job to clean up old orphaned documents.
     * <p>
     * Runs daily at 2 AM to remove orphaned documents older than the
     * retention period.
     * </p>
     */
    @Scheduled(cron = "${app.reconciliation.cleanup-cron:0 0 2 * * *}")
    @Transactional
    public void cleanupOldOrphanedDocuments() {
        if (!enabled) {
            log.debug("Reconciliation is disabled");
            return;
        }

        log.info("Starting cleanup of orphaned documents older than {} days", retentionDays);

        try {
            LocalDateTime cutoffTime = LocalDateTime.now().minusDays(retentionDays);
            List<StoredDocument> oldDocuments = documentRepositoryPort.findByCreatedAtBefore(cutoffTime);

            int deletedCount = 0;

            for (StoredDocument doc : oldDocuments) {
                if (!hasAnyOutboxEvent(doc.getId())) {
                    documentRepositoryPort.deleteById(doc.getId());
                    deletedCount++;
                    log.warn("Deleted old orphaned document: id={}, createdAt={}",
                            doc.getId(), doc.getCreatedAt());
                }
            }

            log.info("Cleanup complete: deleted {} orphaned documents", deletedCount);

        } catch (Exception e) {
            log.error("Error during cleanup", e);
        }
    }

    /**
     * Check if a document has any outbox event at all.
     *
     * @param documentId the document ID to check
     * @return true if any outbox event exists, false otherwise
     */
    private boolean hasAnyOutboxEvent(String documentId) {
        return outboxRepository.existsByAggregateId(documentId);
    }

    /**
     * Handle an orphaned document by creating a compensating outbox event.
     * <p>
     * This ensures the saga orchestration can continue even if the original
     * outbox event was lost.
     * </p>
     *
     * @param document the orphaned document
     */
    private void handleOrphanedDocument(StoredDocument document) {
        String invoiceId = document.getInvoiceId();
        log.warn("Handling orphaned document: id={}, invoiceId={}",
                document.getId(),
                invoiceId != null ? invoiceId : "N/A");

        try {
            // Create a compensating event to notify the system
            OutboxEventEntity compensatingEvent = OutboxEventEntity.builder()
                    .aggregateId(document.getId())
                    .aggregateType("StoredDocument")
                    .eventType("DocumentOrphanedEvent")
                    .payload(formatOrphanedEventPayload(document, invoiceId))
                    .createdAt(java.time.Instant.now())
                    .status(OutboxStatus.PENDING)
                    .build();

            outboxRepository.save(compensatingEvent);

            log.info("Created compensating event for orphaned document: id={}", document.getId());

        } catch (Exception e) {
            log.error("Failed to create compensating event for orphaned document: id={}",
                    document.getId(), e);
            // Don't throw - we want to continue processing other documents
        }
    }

    /**
     * Format the payload for the orphaned event.
     *
     * @param document the orphaned document
     * @param invoiceId the invoice ID (may be null if not set)
     * @return JSON payload string
     */
    private String formatOrphanedEventPayload(StoredDocument document, String invoiceId) {
        OrphanedDocumentPayload payload = new OrphanedDocumentPayload(
            document.getId(),
            invoiceId,
            document.getDocumentType().name(),
            document.getStorageUrl(),
            document.getCreatedAt().toString(),
            Instant.now().toString()
        );
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize orphaned document payload", e);
            throw new RuntimeException("Failed to serialize orphaned document payload", e);
        }
    }

    /**
     * Payload record for orphaned document events.
     */
    private record OrphanedDocumentPayload(
        String documentId,
        String invoiceId,
        String documentType,
        String storageUrl,
        String createdAt,
        String orphanedAt
    ) {}

    /**
     * Get reconciliation statistics.
     *
     * @return summary of reconciliation status
     */
    @Transactional(readOnly = true)
    public ReconciliationStats getStats() {
        LocalDateTime cutoffTime = LocalDateTime.now().minusMinutes(lookbackMinutes);
        long totalDocuments = documentRepositoryPort.countByCreatedAtAfter(cutoffTime);

        // Use efficient aggregation query to count orphaned documents (no N+1)
        long orphanedCount = documentRepositoryPort.countOrphanedDocumentsAfter(cutoffTime);

        return new ReconciliationStats(totalDocuments, orphanedCount);
    }

    /**
     * Statistics about reconciliation status.
     */
    public record ReconciliationStats(long totalDocuments, long orphanedCount) {
        public double orphanRate() {
            return totalDocuments > 0 ? (double) orphanedCount / totalDocuments : 0;
        }

        public boolean hasOrphans() {
            return orphanedCount > 0;
        }

        public String getOrphanRatePercentage() {
            return String.format("%.2f%%", orphanRate() * 100);
        }
    }
}
