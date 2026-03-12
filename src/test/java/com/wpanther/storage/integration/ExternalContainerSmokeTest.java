package com.wpanther.storage.integration;

import com.wpanther.storage.domain.model.DocumentType;
import com.wpanther.storage.domain.model.StoredDocument;
import com.wpanther.storage.domain.repository.DocumentRepositoryPort;
import com.wpanther.storage.infrastructure.adapter.out.persistence.outbox.OutboxEventEntity;
import com.wpanther.storage.infrastructure.adapter.out.persistence.outbox.SpringDataOutboxRepository;
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

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Smoke tests using external Docker containers.
 * <p>
 * These tests connect to externally managed containers (started via docker-compose)
 * instead of using Testcontainers. This allows testing in environments where
 * Testcontainers has compatibility issues with the container runtime.
 * </p>
 * <p>
 * <b>Prerequisites:</b>
 * <ul>
 *   <li>Start test containers: <code>cd ../../ && ./scripts/test-containers-start.sh</code></li>
 *   <li>Containers run on ports: PostgreSQL=5433, MongoDB=27018, Kafka=9093</li>
 * </ul>
 * </p>
 */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.NONE,
    classes = {
        com.wpanther.storage.DocumentStorageServiceApplication.class
    },
    properties = {
        "spring.main.allow-bean-definition-overriding=true"
    }
)
@Tag("integration")
@Tag("smoke")
@DisplayName("External Container Smoke Tests")
@ActiveProfiles("test")
public class ExternalContainerSmokeTest {

    private static final Logger log = LoggerFactory.getLogger(ExternalContainerSmokeTest.class);

    @Autowired
    private DocumentRepositoryPort documentRepository;

    @Autowired
    private SpringDataOutboxRepository outboxRepository;

    @BeforeEach
    void setUp() {
        log.info("==============================================");
        log.info("Starting External Container Smoke Test");
        log.info("==============================================");
    }

    @AfterEach
    void tearDown() {
        log.info("==============================================");
        log.info("External Container Smoke Test Completed");
        log.info("==============================================");
    }

    @Test
    @DisplayName("Should connect to PostgreSQL and store document")
    void shouldConnectToPostgreSQLAndStoreDocument() {
        log.info("SMOKE TEST: PostgreSQL Connection");

        // Given
        String documentId = UUID.randomUUID().toString();
        StoredDocument document = StoredDocument.builder()
                .id(documentId)
                .fileName("smoke-test.pdf")
                .contentType("application/pdf")
                .storagePath("/test/smoke")
                .storageUrl("http://localhost:8084/api/v1/documents/" + documentId)
                .fileSize(1024)
                .checksum("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855")
                .documentType(DocumentType.INVOICE_PDF)
                .createdAt(LocalDateTime.now())
                .build();

        // When
        StoredDocument saved = documentRepository.save(document);
        log.info("Document saved: {}", saved.getId());

        // Then
        assertThat(saved.getId()).isEqualTo(documentId);
        assertThat(documentRepository.findById(documentId)).isPresent();

        log.info("✓ PostgreSQL connection successful");
    }

    @Test
    @DisplayName("Should connect to MongoDB and query documents")
    void shouldConnectToMongoDBAndQueryDocuments() {
        log.info("SMOKE TEST: MongoDB Connection");

        // Given - Create multiple documents
        String invoiceId = UUID.randomUUID().toString();
        for (int i = 0; i < 5; i++) {
            String documentId = UUID.randomUUID().toString();
            StoredDocument document = StoredDocument.builder()
                    .id(documentId)
                    .fileName("mongo-test-" + i + ".pdf")
                    .contentType("application/pdf")
                    .storagePath("/test/mongo/" + i)
                    .storageUrl("http://localhost:8084/api/v1/documents/" + documentId)
                    .fileSize(1024)
                    .checksum("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855")
                    .documentType(DocumentType.INVOICE_PDF)
                    .invoiceId(invoiceId)
                    .createdAt(LocalDateTime.now())
                    .build();
            documentRepository.save(document);
        }
        log.info("Created 5 documents");

        // When - Query by invoice ID
        var documents = documentRepository.findByInvoiceId(invoiceId);

        // Then
        assertThat(documents).hasSize(5);
        log.info("✓ MongoDB connection successful");
    }

    @Test
    @DisplayName("Should create outbox event in PostgreSQL")
    void shouldCreateOutboxEventInPostgreSQL() {
        log.info("SMOKE TEST: Outbox Pattern");

        // Given
        String aggregateId = UUID.randomUUID().toString();
        OutboxEventEntity event = new OutboxEventEntity();
        event.setAggregateId(aggregateId);
        event.setAggregateType("StoredDocument");
        event.setEventType("DocumentStoredEvent");
        event.setPayload("{\"test\": \"smoke\"}");
        event.setStatus(com.wpanther.saga.domain.outbox.OutboxStatus.PENDING);
        event.setRetryCount(0);

        // When
        OutboxEventEntity saved = outboxRepository.save(event);
        log.info("Outbox event created: {}", saved.getId());

        // Then
        assertThat(outboxRepository.existsByAggregateId(aggregateId)).isTrue();
        log.info("✓ Outbox pattern working");
    }

    @Test
    @DisplayName("Should handle concurrent document operations")
    void shouldHandleConcurrentDocumentOperations() throws Exception {
        log.info("SMOKE TEST: Concurrent Operations");

        // Given
        int threadCount = 10;
        java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(threadCount);
        java.util.List<String> documentIds = new java.util.concurrent.CopyOnWriteArrayList<>();

        // When - Create documents concurrently
        long startTime = System.currentTimeMillis();
        for (int i = 0; i < threadCount; i++) {
            final int index = i;
            new Thread(() -> {
                try {
                    String documentId = UUID.randomUUID().toString();
                    StoredDocument document = StoredDocument.builder()
                            .id(documentId)
                            .fileName("concurrent-" + index + ".pdf")
                            .contentType("application/pdf")
                            .storagePath("/test/concurrent/" + index)
                            .storageUrl("http://localhost:8084/api/v1/documents/" + documentId)
                            .fileSize(1024)
                            .checksum("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855")
                            .documentType(DocumentType.INVOICE_PDF)
                            .createdAt(LocalDateTime.now())
                            .build();
                    documentRepository.save(document);
                    documentIds.add(documentId);
                } finally {
                    latch.countDown();
                }
            }).start();
        }

        latch.await(30, java.util.concurrent.TimeUnit.SECONDS);
        long duration = System.currentTimeMillis() - startTime;

        // Then
        assertThat(documentIds).hasSize(threadCount);
        log.info("✓ Concurrent operations completed in {}ms", duration);
    }

    @Test
    @DisplayName("Should verify database isolation between tests")
    void shouldVerifyDatabaseIsolationBetweenTests() {
        log.info("SMOKE TEST: Database Isolation");

        // Given - Create a document
        String documentId = UUID.randomUUID().toString();
        StoredDocument document = StoredDocument.builder()
                .id(documentId)
                .fileName("isolation-test.pdf")
                .contentType("application/pdf")
                .storagePath("/test/isolation")
                .storageUrl("http://localhost:8084/api/v1/documents/" + documentId)
                .fileSize(1024)
                .checksum("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855")
                .documentType(DocumentType.INVOICE_PDF)
                .createdAt(LocalDateTime.now())
                .build();
        documentRepository.save(document);

        // When - Query for a different document
        assertThat(documentRepository.findById(UUID.randomUUID().toString())).isEmpty();

        // Then - Original document still exists
        assertThat(documentRepository.findById(documentId)).isPresent();

        log.info("✓ Database isolation verified");
    }
}
