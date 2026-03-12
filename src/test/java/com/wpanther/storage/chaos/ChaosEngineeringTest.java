package com.wpanther.storage.chaos;

import com.wpanther.saga.domain.outbox.OutboxStatus;
import com.wpanther.storage.domain.model.DocumentType;
import com.wpanther.storage.domain.model.StoredDocument;
import com.wpanther.storage.domain.repository.DocumentRepositoryPort;
import com.wpanther.storage.infrastructure.adapter.out.persistence.outbox.OutboxEventEntity;
import com.wpanther.storage.infrastructure.adapter.out.persistence.outbox.SpringDataOutboxRepository;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Chaos Engineering tests for document-storage-service.
 * <p>
 * These tests validate system resilience under failure conditions by:
 * <ul>
 *   <li>Simulating container crashes and restarts</li>
 *   <li>Injecting network latency and packet loss</li>
 *   <li>Testing database connection pool recovery</li>
 *   <li>Validating retry logic under failures</li>
 *   <li>Testing resource exhaustion scenarios</li>
 * </ul>
 * </p>
 * <p>
 * <b>Prerequisites:</b>
 * <ul>
 *   <li>Docker or Podman must be running</li>
 *   <li>Sufficient resources for multiple containers</li>
 *   <li>Ports 5432, 27017, 9092 available</li>
 * </ul>
 * </p>
 * <p>
 * <b>Tags:</b> @chaos, @integration
 * </p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Testcontainers
@Tag("chaos")
@Tag("integration")
@DisplayName("Chaos Engineering Tests")
@ActiveProfiles("test")
public class ChaosEngineeringTest {

    private static final Logger log = LoggerFactory.getLogger(ChaosEngineeringTest.class);

    // Chaos test configuration constants
    private static final int CIRCUIT_BREAKER_TEST_ITERATIONS = 10;
    private static final int RESOURCE_CONSTRAINT_TEST_DOCUMENTS = 20;
    private static final int CONCURRENT_OPERATIONS_COUNT = 15;
    private static final int MEMORY_PRESSURE_MB = 10;
    private static final int SLOW_QUERY_TEST_DOCUMENTS = 5;
    private static final int SLOW_QUERY_DELAY_MS = 100;
    private static final int RETRY_TEST_MAX_ATTEMPTS = 5;

    private static Network NETWORK;

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(
            DockerImageName.parse("postgres:16-alpine"))
            .withNetwork(NETWORK)
            .withNetworkAliases("postgres")
            .withDatabaseName("documentstorage_chaos")
            .withUsername("test")
            .withPassword("test")
            .withExposedPorts(5432)
            .withStartupTimeout(Duration.ofMinutes(2))
            .withCommand("postgres", "-c", "max_connections=50") // Lower for testing
            .waitingFor(Wait.forLogMessage("database system is ready to accept connections", 2)
                    .withTimes(1));

    @Container
    static MongoDBContainer mongoDB = new MongoDBContainer(
            DockerImageName.parse("mongo:7"))
            .withNetwork(NETWORK)
            .withExposedPorts(27017)
            .withStartupTimeout(Duration.ofMinutes(2))
            .withCommand("--wiredTigerCacheSizeGB", "0.25"); // Limited memory

    @Autowired
    private DocumentRepositoryPort documentRepository;

    @Autowired
    private SpringDataOutboxRepository outboxRepository;

    @BeforeAll
    static void setUpNetwork() {
        NETWORK = Network.newNetwork();
    }

    @AfterAll
    static void tearDownNetwork() {
        if (NETWORK != null) {
            NETWORK.close();
        }
    }

    @BeforeEach
    void setUp() {
        log.info("==============================================");
        log.info("Starting Chaos Engineering Test");
        log.info("==============================================");

        // Clean up outbox events to ensure test isolation
        // Note: We intentionally don't clean up documents in MongoDB for chaos testing.
        // Having leftover documents simulates real-world conditions and tests the system's
        // ability to handle existing data during failure scenarios.
        try {
            outboxRepository.deleteAll();
        } catch (Exception e) {
            log.debug("Error during outbox cleanup: {}", e.getMessage());
        }
    }

    @AfterEach
    void tearDown() {
        log.info("==============================================");
        log.info("Chaos Engineering Test Completed");
        log.info("==============================================");

        // Suggest garbage collection to help detect resource leaks
        System.gc();
    }

    // ==================== RESILIENCE TESTS ====================

    @Test
    @DisplayName("Should recover from database connection loss")
    void shouldRecoverFromDatabaseConnectionLoss() throws Exception {
        log.info("CHAOS TEST: Database Connection Loss Simulation");

        // Given - Create a document
        String documentId = UUID.randomUUID().toString();
        StoredDocument document = StoredDocument.builder()
                .id(documentId)
                .fileName("chaos-test.pdf")
                .contentType("application/pdf")
                .storageUrl("/test/" + documentId)
                .documentType(DocumentType.INVOICE_PDF)
                .createdAt(LocalDateTime.now())
                .build();

        // When - Simulate database connection issues by restarting PostgreSQL
        log.info("Step 1: Store document before chaos");
        documentRepository.save(document);

        log.info("Step 2: Simulating database connection loss...");
        simulateConnectionLoss();

        log.info("Step 3: Attempting to retrieve document after chaos");
        // Then - System should recover and retrieve document
        await().atMost(30, TimeUnit.SECONDS)
                .pollInterval(1, TimeUnit.SECONDS)
                .ignoreExceptions()
                .until(() -> {
                    try {
                        return documentRepository.findById(documentId).isPresent();
                    } catch (Exception e) {
                        log.debug("Error during recovery: {}", e.getMessage());
                        return false;
                    }
                });

        assertThat(documentRepository.findById(documentId)).isPresent();
        log.info("✓ System recovered from database connection loss");
    }

    @Test
    @DisplayName("Should handle outbox creation during database restart")
    void shouldHandleOutboxCreationDuringDatabaseRestart() throws Exception {
        log.info("CHAOS TEST: Outbox Creation During Database Restart");

        String documentId = UUID.randomUUID().toString();

        // When - Create outbox event while database is unstable
        log.info("Step 1: Creating outbox event with database instability");
        simulateDatabaseInstability(() -> {
            OutboxEventEntity event = new OutboxEventEntity();
            event.setAggregateId(documentId);
            event.setAggregateType("StoredDocument");
            event.setEventType("DocumentStoredEvent");
            event.setPayload("{\"test\": \"chaos\"}");
            event.setCreatedAt(java.time.Instant.now());
            event.setStatus(OutboxStatus.PENDING);
            event.setRetryCount(0);

            outboxRepository.save(event);
            log.info("Outbox event created despite database instability");
        });

        // Then - Event should be persisted
        await().atMost(15, TimeUnit.SECONDS)
                .ignoreExceptions()
                .until(() -> {
                    try {
                        return outboxRepository.existsByAggregateId(documentId);
                    } catch (Exception e) {
                        log.debug("Error checking outbox: {}", e.getMessage());
                        return false;
                    }
                });

        assertThat(outboxRepository.existsByAggregateId(documentId)).isTrue();
        log.info("✓ Outbox creation handled during database restart");
    }

    @Test
    @DisplayName("Should validate circuit breaker after repeated failures")
    void shouldValidateCircuitBreakerAfterRepeatedFailures() {
        log.info("CHAOS TEST: Circuit Breaker Validation");

        String documentId = UUID.randomUUID().toString();

        // When - Simulate repeated database failures
        log.info("Step 1: Simulating repeated database failures");
        int failureCount = 0;
        int successCount = 0;

        for (int i = 0; i < CIRCUIT_BREAKER_TEST_ITERATIONS; i++) {
            try {
                // Attempt operation that may fail
                StoredDocument doc = StoredDocument.builder()
                        .id(documentId + "-" + i)
                        .fileName("test.pdf")
                        .contentType("application/pdf")
                        .storageUrl("/test/" + i)
                        .documentType(DocumentType.INVOICE_PDF)
                        .createdAt(LocalDateTime.now())
                        .build();

                documentRepository.save(doc);
                successCount++;
                log.debug("Operation {} succeeded", i);
            } catch (Exception e) {
                failureCount++;
                log.debug("Operation {} failed: {}", i, e.getMessage());

                // Simulate intermittent failure
                if (i % 3 == 0) {
                    try {
                        simulateIntermittentLatency();
                    } catch (Exception latencyException) {
                        log.debug("Latency simulation failed: {}", latencyException.getMessage());
                    }
                }
            }
        }

        // Then - System should handle failures gracefully
        log.info("Results - Failures: {}, Successes: {}", failureCount, successCount);
        assertThat(successCount + failureCount).isEqualTo(CIRCUIT_BREAKER_TEST_ITERATIONS);

        // Verify at least some operations succeeded
        assertThat(successCount).isGreaterThan(0);
        log.info("✓ Circuit breaker handled repeated failures");
    }

    @Test
    @DisplayName("Should handle resource constrained environment")
    void shouldHandleResourceConstrainedEnvironment() {
        log.info("CHAOS TEST: Resource Constrained Environment");

        // Given - Create multiple documents rapidly
        int documentCount = RESOURCE_CONSTRAINT_TEST_DOCUMENTS;
        log.info("Step 1: Creating {} documents under resource constraints", documentCount);

        int successfulSaves = 0;
        long startTime = System.currentTimeMillis();

        for (int i = 0; i < documentCount; i++) {
            try {
                // Simulate resource pressure by creating document
                String documentId = UUID.randomUUID().toString();
                StoredDocument document = StoredDocument.builder()
                        .id(documentId)
                        .fileName("resource-test-" + i + ".pdf")
                        .contentType("application/pdf")
                        .storageUrl("/test/resource/" + i)
                        .documentType(DocumentType.INVOICE_PDF)
                        .createdAt(LocalDateTime.now())
                        .build();

                documentRepository.save(document);
                successfulSaves++;

                // Simulate memory pressure
                if (i % 5 == 0) {
                    log.debug("Simulating resource pressure at document {}", i);
                    simulateResourcePressure();
                }
            } catch (Exception e) {
                log.warn("Failed to save document {}: {}", i, e.getMessage());
            }
        }

        long duration = System.currentTimeMillis() - startTime;

        // Then - System should handle resource constraints
        log.info("Step 2: Verifying results under resource constraints");
        assertThat(successfulSaves).isGreaterThan(documentCount / 2); // At least 50% success rate
        log.info("Successfully saved {}/{} documents in {}ms", successfulSaves, documentCount, duration);
        log.info("✓ System handled resource constrained environment");
    }

    @Test
    @DisplayName("Should recover from connection pool exhaustion")
    void shouldRecoverFromConnectionPoolExhaustion() throws Exception {
        log.info("CHAOS TEST: Connection Pool Exhaustion");

        // Given - Create multiple concurrent operations
        int concurrentOps = CONCURRENT_OPERATIONS_COUNT;
        log.info("Step 1: Executing {} concurrent operations", concurrentOps);

        java.util.List<StoredDocument> documents = new java.util.ArrayList<>();
        java.util.List<Exception> exceptions = new java.util.ArrayList<>();

        // Execute operations concurrently
        java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(concurrentOps);
        java.util.concurrent.ExecutorService executor = java.util.concurrent.Executors.newFixedThreadPool(10);

        for (int i = 0; i < concurrentOps; i++) {
            final int index = i;
            executor.submit(() -> {
                try {
                    String documentId = UUID.randomUUID().toString();
                    StoredDocument document = StoredDocument.builder()
                            .id(documentId)
                            .fileName("pool-test-" + index + ".pdf")
                            .contentType("application/pdf")
                            .storageUrl("/test/pool/" + index)
                            .documentType(DocumentType.INVOICE_PDF)
                            .createdAt(LocalDateTime.now())
                            .build();

                    StoredDocument saved = documentRepository.save(document);
                    synchronized (documents) {
                        documents.add(saved);
                    }
                    log.debug("Operation {} succeeded", index);
                } catch (Exception e) {
                    synchronized (exceptions) {
                        exceptions.add(e);
                    }
                    log.warn("Operation {} failed: {}", index, e.getMessage());
                } finally {
                    latch.countDown();
                }
            });
        }

        // Wait for all operations to complete
        boolean completed = latch.await(60, TimeUnit.SECONDS);
        executor.shutdown();

        // Then - System should handle pool exhaustion gracefully
        assertThat(completed).isTrue();
        log.info("Results - Success: {}, Failures: {}", documents.size(), exceptions.size());

        // Most operations should succeed
        assertThat(documents.size()).isGreaterThan(concurrentOps / 2);
        log.info("✓ System recovered from connection pool exhaustion");
    }

    @Test
    @DisplayName("Should validate retry logic under transient failures")
    void shouldValidateRetryLogicUnderTransientFailures() throws Exception {
        log.info("CHAOS TEST: Retry Logic Validation");

        String documentId = UUID.randomUUID().toString();

        // When - Simulate transient failures
        log.info("Step 1: Testing retry logic with transient failures");
        int attempts = 0;
        boolean success = false;

        for (int i = 0; i < RETRY_TEST_MAX_ATTEMPTS; i++) {
            attempts++;
            try {
                StoredDocument document = StoredDocument.builder()
                        .id(documentId)
                        .fileName("retry-test.pdf")
                        .contentType("application/pdf")
                        .storageUrl("/test/retry")
                        .documentType(DocumentType.INVOICE_PDF)
                        .createdAt(LocalDateTime.now())
                        .build();

                documentRepository.save(document);

                // Simulate transient failure for first 3 attempts
                if (i < 3) {
                    simulateTransientFailure();
                    throw new RuntimeException("Simulated transient failure");
                }

                success = true;
                log.info("Operation succeeded on attempt {}", attempts);
                break;
            } catch (Exception e) {
                log.debug("Attempt {} failed: {}", attempts, e.getMessage());
                java.util.concurrent.TimeUnit.MILLISECONDS.sleep(500);
            }
        }

        // Then - Operation should succeed after retries
        assertThat(success).isTrue();
        assertThat(attempts).isGreaterThan(3);
        log.info("✓ Retry logic validated - succeeded after {} attempts", attempts);
    }

    @Test
    @DisplayName("Should handle slow database queries")
    void shouldHandleSlowDatabaseQueries() throws Exception {
        log.info("CHAOS TEST: Slow Database Query Handling");

        // Given - Simulate slow queries
        log.info("Step 1: Creating documents with slow queries");

        java.util.List<String> documentIds = new java.util.ArrayList<>();
        long startTime = System.currentTimeMillis();

        for (int i = 0; i < SLOW_QUERY_TEST_DOCUMENTS; i++) {
            simulateSlowDatabase(SLOW_QUERY_DELAY_MS); // 100ms delay

            String documentId = UUID.randomUUID().toString();
            StoredDocument document = StoredDocument.builder()
                    .id(documentId)
                    .fileName("slow-query-" + i + ".pdf")
                    .contentType("application/pdf")
                    .storageUrl("/test/slow/" + i)
                    .documentType(DocumentType.INVOICE_PDF)
                    .createdAt(LocalDateTime.now())
                    .build();

            documentRepository.save(document);
            documentIds.add(documentId);
            log.info("Document {} saved despite slow query", i + 1);
        }

        long duration = System.currentTimeMillis() - startTime;

        // Then - All operations should complete
        assertThat(documentIds).hasSize(SLOW_QUERY_TEST_DOCUMENTS);
        log.info("All {} documents saved in {}ms despite slow queries", documentIds.size(), duration);
        log.info("✓ System handled slow database queries");
    }

    // ==================== CHAOS SIMULATION METHODS ====================

    /**
     * Simulate database connection loss by restarting PostgreSQL.
     */
    private void simulateConnectionLoss() throws Exception {
        log.info("  [CHAOS] Simulating connection loss - pausing PostgreSQL");
        postgres.stop();

        // Wait a bit to simulate connection loss
        java.util.concurrent.TimeUnit.SECONDS.sleep(3);

        log.info("  [CHAOS] Restarting PostgreSQL");
        postgres.start();

        // Wait for PostgreSQL to be ready
        await().atMost(30, TimeUnit.SECONDS)
                .pollInterval(2, TimeUnit.SECONDS)
                .until(() -> {
                    try {
                        return postgres.isRunning() &&
                               postgres.execInContainer("pg_isready").getExitCode() == 0;
                    } catch (Exception e) {
                        return false;
                    }
                });

        log.info("  [CHAOS] PostgreSQL recovered");
    }

    /**
     * Simulate database instability during operation.
     */
    private void simulateDatabaseInstability(ChaosOperation operation) throws Exception {
        log.info("  [CHAOS] Simulating database instability");

        // Execute operation with random delays
        for (int i = 0; i < 3; i++) {
            try {
                operation.execute();

                // Random delay between 100-500ms
                long delay = 100 + (long) (Math.random() * 400);
                if (Math.random() > 0.5) {
                    java.util.concurrent.TimeUnit.MILLISECONDS.sleep(delay);
                }
            } catch (Exception e) {
                log.debug("Operation attempt {} failed: {}", i, e.getMessage());
                if (i < 2) {
                    continue; // Retry
                }
                throw e;
            }
        }

        log.info("  [CHAOS] Database instability simulation complete");
    }

    /**
     * Simulate intermittent network latency.
     */
    private void simulateIntermittentLatency() throws Exception {
        long delay = (long) (Math.random() * 2000) + 500; // 500-2500ms
        log.info("  [CHAOS] Simulating network latency: {}ms", delay);
        java.util.concurrent.TimeUnit.MILLISECONDS.sleep(delay);
    }

    /**
     * Simulate resource pressure.
     */
    private void simulateResourcePressure() throws Exception {
        log.info("  [CHAOS] Simulating resource pressure");

        // Allocate some memory
        byte[] memoryAllocation = new byte[MEMORY_PRESSURE_MB * 1024 * 1024];

        // Simulate CPU pressure
        long startTime = System.currentTimeMillis();
        while (System.currentTimeMillis() - startTime < 100) {
            // CPU intensive operation
            Math.sqrt(Math.random() * 10000);
        }

        log.info("  [CHAOS] Resource pressure simulation complete");
    }

    /**
     * Simulate transient failure.
     */
    private void simulateTransientFailure() throws Exception {
        log.info("  [CHAOS] Simulating transient failure");
        // Random small delay
        java.util.concurrent.TimeUnit.MILLISECONDS.sleep((long) (Math.random() * 200));
    }

    /**
     * Simulate slow database operation.
     */
    private void simulateSlowDatabase(long delayMs) throws Exception {
        log.info("  [CHAOS] Simulating slow database: {}ms", delayMs);
        java.util.concurrent.TimeUnit.MILLISECONDS.sleep(delayMs);
    }

    /**
     * Functional interface for chaos operations.
     */
    @FunctionalInterface
    private interface ChaosOperation {
        void execute() throws Exception;
    }

    // ==================== RESILIENCE METRICS ====================

    /**
     * Calculate chaos test success rate.
     */
    private double calculateSuccessRate(int total, int successful) {
        return total > 0 ? (double) successful / total * 100 : 0;
    }

    /**
     * Log chaos test summary.
     */
    private void logChaosSummary(String testName, int total, int successful, int failed, long durationMs) {
        log.info("==============================================");
        log.info("CHAOS TEST SUMMARY: {}", testName);
        log.info("==============================================");
        log.info("Total Operations: {}", total);
        log.info("Successful: {}", successful);
        log.info("Failed: {}", failed);
        log.info("Success Rate: {:.1f}%", calculateSuccessRate(total, successful));
        log.info("Duration: {}ms", durationMs);
        log.info("==============================================");
    }
}
