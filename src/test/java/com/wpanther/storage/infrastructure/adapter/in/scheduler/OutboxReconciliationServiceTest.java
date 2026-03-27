package com.wpanther.storage.infrastructure.adapter.in.scheduler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wpanther.storage.domain.model.DocumentType;
import com.wpanther.storage.domain.model.StoredDocument;
import com.wpanther.storage.domain.repository.DocumentRepositoryPort;
import com.wpanther.storage.infrastructure.adapter.out.persistence.outbox.SpringDataOutboxRepository;
import com.wpanther.storage.infrastructure.config.metrics.DocumentStorageMetricsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Collection;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("OutboxReconciliationService Tests")
class OutboxReconciliationServiceTest {

    @Mock
    private DocumentRepositoryPort documentRepositoryPort;

    @Mock
    private SpringDataOutboxRepository outboxRepository;

    @Mock
    private DocumentStorageMetricsService metrics;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Clock fixedClock = Clock.fixed(
        java.time.Instant.parse("2026-03-27T09:00:00Z"),
        ZoneId.of("UTC")
    );

    private OutboxReconciliationService service;

    private ArgumentCaptor<Collection<String>> aggregateIdsCaptor = ArgumentCaptor.forClass(Collection.class);

    @BeforeEach
    void setUp() {
        service = new OutboxReconciliationService(
            documentRepositoryPort,
            outboxRepository,
            metrics,
            objectMapper,
            fixedClock
        );
        // @Value fields are not injected by @RequiredArgsConstructor; set them manually
        ReflectionTestUtils.setField(service, "enabled", true);
        ReflectionTestUtils.setField(service, "lookbackMinutes", 60);
        ReflectionTestUtils.setField(service, "retentionDays", 30);
    }

    private StoredDocument createDocument(String id, String invoiceId, DocumentType type) {
        return StoredDocument.builder()
            .id(id)
            .fileName("doc-" + id + ".pdf")
            .contentType("application/pdf")
            .storagePath("/2026/03/27/" + id + ".pdf")
            .storageUrl("http://localhost:8084/api/v1/documents/" + id)
            .fileSize(1024L)
            .checksum("sha256-" + id)
            .documentType(type)
            .createdAt(LocalDateTime.now(fixedClock))
            .invoiceId(invoiceId)
            .build();
    }

    @Nested
    @DisplayName("reconcileOrphanedDocuments()")
    class ReconcileOrphanedDocumentsTests {

        @Test
        @DisplayName("Should use single batch query instead of N individual queries")
        void shouldUseSingleBatchQueryInsteadOfNIndividualQueries() {
            List<StoredDocument> docs = List.of(
                createDocument("doc-1", "inv-1", DocumentType.INVOICE_PDF),
                createDocument("doc-2", "inv-1", DocumentType.SIGNED_XML),
                createDocument("doc-3", "inv-2", DocumentType.UNSIGNED_PDF)
            );

            when(documentRepositoryPort.findByCreatedAtAfter(any(LocalDateTime.class)))
                .thenReturn(docs);
            when(outboxRepository.findExistingAggregateIds(anyCollection()))
                .thenReturn(List.of("doc-1", "doc-3"));

            service.reconcileOrphanedDocuments();

            // Verify batch query was called once, not per-document
            verify(outboxRepository, times(1)).findExistingAggregateIds(anyCollection());
            verify(outboxRepository, never()).existsByAggregateId(anyString());
            verify(outboxRepository, never()).existsByAggregateIdAndEventType(anyString(), anyString());

            // Verify the batch contained all document IDs
            verify(outboxRepository).findExistingAggregateIds(aggregateIdsCaptor.capture());
            assertThat(aggregateIdsCaptor.getValue()).containsExactlyInAnyOrder("doc-1", "doc-2", "doc-3");
        }

        @Test
        @DisplayName("Should identify orphaned documents (no outbox event)")
        void shouldIdentifyOrphanedDocuments() {
            List<StoredDocument> docs = List.of(
                createDocument("doc-1", "inv-1", DocumentType.INVOICE_PDF),
                createDocument("doc-2", "inv-1", DocumentType.SIGNED_XML)
            );

            when(documentRepositoryPort.findByCreatedAtAfter(any(LocalDateTime.class)))
                .thenReturn(docs);
            // Only doc-1 has an outbox event
            when(outboxRepository.findExistingAggregateIds(anyCollection()))
                .thenReturn(List.of("doc-1"));

            service.reconcileOrphanedDocuments();

            // Only doc-2 should have a compensating event saved
            verify(outboxRepository).save(argThat(entity ->
                entity.getAggregateId().equals("doc-2") &&
                entity.getEventType().equals("DocumentOrphanedEvent")
            ));
        }

        @Test
        @DisplayName("Should handle no orphaned documents")
        void shouldHandleNoOrphanedDocuments() {
            List<StoredDocument> docs = List.of(
                createDocument("doc-1", "inv-1", DocumentType.INVOICE_PDF),
                createDocument("doc-2", "inv-1", DocumentType.SIGNED_XML)
            );

            when(documentRepositoryPort.findByCreatedAtAfter(any(LocalDateTime.class)))
                .thenReturn(docs);
            // All documents have outbox events
            when(outboxRepository.findExistingAggregateIds(anyCollection()))
                .thenReturn(List.of("doc-1", "doc-2"));

            service.reconcileOrphanedDocuments();

            // No compensating events should be saved
            verify(outboxRepository, never()).save(any());
        }

        @Test
        @DisplayName("Should handle empty document list")
        void shouldHandleEmptyDocumentList() {
            when(documentRepositoryPort.findByCreatedAtAfter(any(LocalDateTime.class)))
                .thenReturn(List.of());

            service.reconcileOrphanedDocuments();

            // No batch query should be made for empty list
            verify(outboxRepository, never()).findExistingAggregateIds(anyCollection());
            verify(outboxRepository, never()).save(any());
        }

        @Test
        @DisplayName("Should record reconciliation metrics")
        void shouldRecordReconciliationMetrics() {
            List<StoredDocument> docs = List.of(
                createDocument("doc-1", "inv-1", DocumentType.INVOICE_PDF),
                createDocument("doc-2", "inv-1", DocumentType.SIGNED_XML)
            );

            when(documentRepositoryPort.findByCreatedAtAfter(any(LocalDateTime.class)))
                .thenReturn(docs);
            when(outboxRepository.findExistingAggregateIds(anyCollection()))
                .thenReturn(List.of("doc-1"));

            service.reconcileOrphanedDocuments();

            verify(metrics).recordReconciliationRun(1);
        }
    }

    @Nested
    @DisplayName("cleanupOldOrphanedDocuments()")
    class CleanupOldOrphanedDocumentsTests {

        @Test
        @DisplayName("Should use single batch query for cleanup")
        void shouldUseSingleBatchQueryForCleanup() {
            List<StoredDocument> docs = List.of(
                createDocument("old-doc-1", "inv-1", DocumentType.INVOICE_PDF),
                createDocument("old-doc-2", "inv-2", DocumentType.SIGNED_XML)
            );

            when(documentRepositoryPort.findByCreatedAtBefore(any(LocalDateTime.class)))
                .thenReturn(docs);
            when(outboxRepository.findExistingAggregateIds(anyCollection()))
                .thenReturn(List.of("old-doc-1"));

            service.cleanupOldOrphanedDocuments();

            // Single batch query, not per-document
            verify(outboxRepository, times(1)).findExistingAggregateIds(anyCollection());
            verify(outboxRepository, never()).existsByAggregateId(anyString());

            // Only orphaned doc should be deleted
            verify(documentRepositoryPort).deleteById("old-doc-2");
            verify(documentRepositoryPort, never()).deleteById("old-doc-1");
        }

        @Test
        @DisplayName("Should not delete documents that have outbox events")
        void shouldNotDeleteDocumentsWithOutboxEvents() {
            List<StoredDocument> docs = List.of(
                createDocument("old-doc-1", "inv-1", DocumentType.INVOICE_PDF)
            );

            when(documentRepositoryPort.findByCreatedAtBefore(any(LocalDateTime.class)))
                .thenReturn(docs);
            when(outboxRepository.findExistingAggregateIds(anyCollection()))
                .thenReturn(List.of("old-doc-1"));

            service.cleanupOldOrphanedDocuments();

            verify(documentRepositoryPort, never()).deleteById(anyString());
        }

        @Test
        @DisplayName("Should handle empty old documents list")
        void shouldHandleEmptyOldDocumentsList() {
            when(documentRepositoryPort.findByCreatedAtBefore(any(LocalDateTime.class)))
                .thenReturn(List.of());

            service.cleanupOldOrphanedDocuments();

            verify(outboxRepository, never()).findExistingAggregateIds(anyCollection());
            verify(documentRepositoryPort, never()).deleteById(anyString());
        }
    }
}
