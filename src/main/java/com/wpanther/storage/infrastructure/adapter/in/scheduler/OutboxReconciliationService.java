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

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

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
 * <p>
 * <b>Testing:</b> The {@link Clock} is injected to allow time-based testing.
 * Use {@link Clock#fixed(Instant, ZoneId)} in tests for deterministic results.
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
    private final Clock clock;

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
            LocalDateTime cutoffTime = LocalDateTime.now(clock).minusMinutes(lookbackMinutes);
            List<StoredDocument> recentDocuments = documentRepositoryPort.findByCreatedAtAfter(cutoffTime);

            if (recentDocuments.isEmpty()) {
                log.info("No recent documents to reconcile");
                metrics.recordReconciliationRun(0);
                return;
            }

            // Batch query: single SQL instead of N individual queries
            Set<String> idsWithOutbox = findIdsWithOutboxEvents(recentDocuments);

            int orphanedCount = 0;

            for (StoredDocument doc : recentDocuments) {
                if (!idsWithOutbox.contains(doc.getId())) {
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
            LocalDateTime cutoffTime = LocalDateTime.now(clock).minusDays(retentionDays);
            List<StoredDocument> oldDocuments = documentRepositoryPort.findByCreatedAtBefore(cutoffTime);

            if (oldDocuments.isEmpty()) {
                log.info("No old documents to clean up");
                return;
            }

            // Batch query: single SQL instead of N individual queries
            Set<String> idsWithOutbox = findIdsWithOutboxEvents(oldDocuments);

            int deletedCount = 0;

            for (StoredDocument doc : oldDocuments) {
                if (!idsWithOutbox.contains(doc.getId())) {
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
     * Batch query to find which document IDs already have outbox events.
     * Replaces N individual {@code existsByAggregateId()} calls with a single query.
     *
     * @param documents the documents to check
     * @return set of document IDs that have at least one outbox event
     */
    private Set<String> findIdsWithOutboxEvents(List<StoredDocument> documents) {
        List<String> documentIds = documents.stream()
            .map(StoredDocument::getId)
            .toList();
        return new HashSet<>(outboxRepository.findExistingAggregateIds(documentIds));
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
                    .createdAt(Instant.now(clock))
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
            Instant.now(clock).toString()
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
        LocalDateTime cutoffTime = LocalDateTime.now(clock).minusMinutes(lookbackMinutes);
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
