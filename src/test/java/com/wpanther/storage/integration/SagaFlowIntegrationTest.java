package com.wpanther.storage.integration;

import com.wpanther.saga.domain.outbox.OutboxStatus;
import com.wpanther.storage.domain.model.DocumentType;
import com.wpanther.storage.domain.model.StoredDocument;
import com.wpanther.storage.domain.port.outbound.DocumentRepositoryPort;
import com.wpanther.storage.infrastructure.adapter.outbound.persistence.outbox.OutboxEventEntity;
import com.wpanther.storage.infrastructure.adapter.outbound.persistence.outbox.SpringDataOutboxRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for the Saga flow with CDC (Change Data Capture) foundation.
 * <p>
 * Tests the transactional outbox pattern that enables Debezium CDC:
 * <ol>
 *   <li>Document stored in MongoDB</li>
 *   <li>Outbox event written to PostgreSQL (same transaction)</li>
 *   <li>Repository methods support reconciliation queries</li>
 * </ol>
 * </p>
 * <p>
 * <b>Note:</b> Full Debezium CDC testing with Kafka requires additional Debezium
 * container setup. These tests verify the outbox pattern foundation works correctly
 * with real databases, which enables CDC in production.
 * </p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Testcontainers
@Tag("integration")
@DisplayName("Saga Flow Integration Tests (CDC Foundation)")
@ActiveProfiles("test")
public class SagaFlowIntegrationTest {

    private static final Logger log = LoggerFactory.getLogger(SagaFlowIntegrationTest.class);

    @Container
    static KafkaContainer kafka = new KafkaContainer(
            DockerImageName.parse("confluentinc/cp-kafka:7.5.0"))
            .withEmbeddedZookeeper()
            .withExposedPorts(9093)
            .withStartupTimeout(Duration.ofMinutes(2));

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(
            DockerImageName.parse("postgres:16-alpine"))
            .withDatabaseName("documentstorage_test")
            .withUsername("postgres")
            .withPassword("postgres")
            .withExposedPorts(5432)
            .withStartupTimeout(Duration.ofMinutes(1))
            .waitingFor(Wait.forLogMessage("database system is ready to accept connections", 2).withTimes(1));

    @Container
    static MongoDBContainer mongoDB = new MongoDBContainer(
            DockerImageName.parse("mongo:7"))
            .withExposedPorts(27017)
            .withStartupTimeout(Duration.ofMinutes(1));

    @Autowired
    private DocumentRepositoryPort documentRepository;

    @Autowired
    private SpringDataOutboxRepository outboxRepository;

    @BeforeEach
    void setUp() {
        // Clean up any previous test data
        outboxRepository.deleteAll();
    }

    @AfterEach
    void tearDown() {
        // Clean up test data
        outboxRepository.deleteAll();
    }

    @Test
    @DisplayName("Should create document and outbox event transactionally")
    void shouldCreateDocumentAndOutboxEvent() {
        // Given
        String documentId = UUID.randomUUID().toString();

        StoredDocument document = StoredDocument.builder()
                .id(documentId)
                .fileName("test.pdf")
                .contentType("application/pdf")
                .storageUrl("/test/" + documentId)
                .documentType(DocumentType.INVOICE_PDF)
                .invoiceId("INV-" + UUID.randomUUID())
                .createdAt(LocalDateTime.now())
                .build();

        // When - Save document (simulates service layer)
        StoredDocument savedDocument = documentRepository.save(document);

        // Create outbox event (simulates what the service would do transactionally)
        OutboxEventEntity outboxEvent = new OutboxEventEntity();
        outboxEvent.setAggregateId(documentId);
        outboxEvent.setAggregateType("StoredDocument");
        outboxEvent.setEventType("DocumentStoredEvent");
        outboxEvent.setPayload("{\"documentId\":\"" + documentId + "\"}");
        outboxEvent.setCreatedAt(java.time.Instant.now());
        outboxEvent.setStatus(OutboxStatus.PENDING);

        outboxRepository.save(outboxEvent);

        // Then
        assertThat(savedDocument.getId()).isEqualTo(documentId);

        List<OutboxEventEntity> events = outboxRepository.findAll();
        assertThat(events).hasSize(1);
        assertThat(events.get(0).getAggregateId()).isEqualTo(documentId);
        assertThat(events.get(0).getEventType()).isEqualTo("DocumentStoredEvent");

        log.info("✓ Document and outbox event created successfully");
    }

    @Test
    @DisplayName("Should detect orphaned documents (no outbox event)")
    void shouldDetectOrphanedDocuments() {
        // Given - Create document without outbox event
        String documentId = UUID.randomUUID().toString();

        StoredDocument document = StoredDocument.builder()
                .id(documentId)
                .fileName("orphan.pdf")
                .contentType("application/pdf")
                .storageUrl("/test/" + documentId)
                .documentType(DocumentType.INVOICE_PDF)
                .createdAt(LocalDateTime.now())
                .build();

        documentRepository.save(document);

        // When - Check for orphaned document
        boolean hasOutboxEvent = outboxRepository.existsByAggregateId(documentId);

        // Then - Document exists but has no outbox event
        List<StoredDocument> docs = documentRepository.findByCreatedAtAfter(
                LocalDateTime.now().minus(1, ChronoUnit.MINUTES));

        assertThat(docs).anyMatch(d -> d.getId().equals(documentId));
        assertThat(hasOutboxEvent).isFalse();

        log.info("✓ Orphaned document detected correctly");
    }

    @Test
    @DisplayName("Should verify outbox repository methods for reconciliation")
    void shouldVerifyOutboxRepositoryMethods() {
        // Given
        String documentId = UUID.randomUUID().toString();

        OutboxEventEntity event = new OutboxEventEntity();
        event.setAggregateId(documentId);
        event.setAggregateType("StoredDocument");
        event.setEventType("DocumentStoredEvent");
        event.setPayload("{\"test\":\"data\"}");
        event.setCreatedAt(java.time.Instant.now());
        event.setStatus(OutboxStatus.PENDING);

        outboxRepository.save(event);

        // When & Then
        assertThat(outboxRepository.existsByAggregateId(documentId)).isTrue();
        assertThat(outboxRepository.existsByAggregateIdAndType(
                documentId, "DocumentStoredEvent")).isTrue();

        List<OutboxEventEntity> found = outboxRepository
                .findByAggregateTypeAndAggregateIdOrderByCreatedAtAsc(
                        "StoredDocument", documentId);

        assertThat(found).hasSize(1);
        assertThat(found.get(0).getAggregateId()).isEqualTo(documentId);

        log.info("✓ Outbox repository methods work correctly");
    }

    @Test
    @DisplayName("Should verify document repository reconciliation queries")
    void shouldVerifyDocumentRepositoryReconciliationQueries() {
        // Given
        String recentId = UUID.randomUUID().toString();
        String oldId = UUID.randomUUID().toString();

        StoredDocument recentDoc = StoredDocument.builder()
                .id(recentId)
                .fileName("recent.pdf")
                .contentType("application/pdf")
                .storageUrl("/test/recent")
                .documentType(DocumentType.INVOICE_PDF)
                .createdAt(LocalDateTime.now())
                .build();

        StoredDocument oldDoc = StoredDocument.builder()
                .id(oldId)
                .fileName("old.pdf")
                .contentType("application/pdf")
                .storageUrl("/test/old")
                .documentType(DocumentType.INVOICE_PDF)
                .createdAt(LocalDateTime.now().minusDays(35))
                .build();

        documentRepository.save(recentDoc);
        documentRepository.save(oldDoc);

        // When & Then
        List<StoredDocument> recentDocs = documentRepository.findByCreatedAtAfter(
                LocalDateTime.now().minus(1, ChronoUnit.HOURS));
        assertThat(recentDocs).anyMatch(d -> d.getId().equals(recentId));

        List<StoredDocument> oldDocs = documentRepository.findByCreatedAtBefore(
                LocalDateTime.now().minusDays(30));
        assertThat(oldDocs).anyMatch(d -> d.getId().equals(oldId));

        long count = documentRepository.countByCreatedAtAfter(
                LocalDateTime.now().minus(1, ChronoUnit.HOURS));
        assertThat(count).isGreaterThan(0);

        log.info("✓ Document repository reconciliation queries work correctly");
    }

    @Test
    @DisplayName("Should support compensating events for orphaned documents")
    void shouldSupportCompensatingEvents() {
        // Given - Orphaned document
        String documentId = UUID.randomUUID().toString();

        StoredDocument document = StoredDocument.builder()
                .id(documentId)
                .fileName("orphan.pdf")
                .contentType("application/pdf")
                .storageUrl("/test/" + documentId)
                .documentType(DocumentType.INVOICE_PDF)
                .createdAt(LocalDateTime.now())
                .build();

        documentRepository.save(document);

        // When - Create compensating event
        OutboxEventEntity compensatingEvent = new OutboxEventEntity();
        compensatingEvent.setAggregateId(documentId);
        compensatingEvent.setAggregateType("StoredDocument");
        compensatingEvent.setEventType("DocumentOrphanedEvent");
        compensatingEvent.setPayload("{\"documentId\":\"" + documentId + "\"}");
        compensatingEvent.setCreatedAt(java.time.Instant.now());
        compensatingEvent.setStatus(OutboxStatus.PENDING);

        outboxRepository.save(compensatingEvent);

        // Then - Compensating event exists
        List<OutboxEventEntity> events = outboxRepository
                .findByAggregateTypeAndAggregateIdOrderByCreatedAtAsc(
                        "StoredDocument", documentId);

        assertThat(events).hasSize(1);
        assertThat(events.get(0).getEventType()).isEqualTo("DocumentOrphanedEvent");

        log.info("✓ Compensating event support works correctly");
    }
}
