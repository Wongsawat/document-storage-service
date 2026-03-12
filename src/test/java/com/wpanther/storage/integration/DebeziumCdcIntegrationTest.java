package com.wpanther.storage.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wpanther.saga.domain.outbox.OutboxStatus;
import com.wpanther.storage.domain.model.DocumentType;
import com.wpanther.storage.domain.model.StoredDocument;
import com.wpanther.storage.domain.repository.DocumentRepositoryPort;
import com.wpanther.storage.infrastructure.adapter.out.persistence.outbox.OutboxEventEntity;
import com.wpanther.storage.infrastructure.adapter.out.persistence.outbox.SpringDataOutboxRepository;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
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
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Full end-to-end integration tests for Debezium CDC pipeline.
 * <p>
 * This test class validates the complete Change Data Capture flow:
 * <ol>
 *   <li>PostgreSQL outbox events are created</li>
 *   <li>Debezium captures PostgreSQL WAL changes</li>
 *   <li>Debezium publishes events to Kafka</li>
 *   <li>Kafka consumer receives and validates events</li>
 * </ol>
 * </p>
 * <p>
 * <b>Prerequisites:</b>
 * <ul>
 *   <li>Docker daemon running (or Podman with DOCKER_HOST set)</li>
 *   <li>Ports 5433, 9093, 27018, 8083 must be available</li>
 * </ul>
 * </p>
 * <p>
 * <b>Note:</b> These tests require Docker/Podman to run. They will be skipped
 * if Docker is not available (the SagaFlowIntegrationTest runs without Debezium).
 * </p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Testcontainers
@Tag("integration")
@Tag("debezium")
@DisplayName("Debezium CDC Integration Tests")
@ActiveProfiles("test")
public class DebeziumCdcIntegrationTest {

    private static final Logger log = LoggerFactory.getLogger(DebeziumCdcIntegrationTest.class);

    private static final String DEBEZIUM_VERSION = "2.5.4.Final";
    private static final String KAFKA_CONNECT_HOST = "kafka-connect";
    private static final String OUTBOX_TOPIC = "debezium-postgres-cdc.documentstorage_test.public.outbox_events";
    private static final Network NETWORK = Network.newNetwork();

    @Container
    static KafkaContainer kafka = new KafkaContainer(
            DockerImageName.parse("confluentinc/cp-kafka:7.5.0"))
            .withNetwork(NETWORK)
            .withNetworkAliases("kafka")
            .withEmbeddedZookeeper()
            .withExposedPorts(9093)
            .withStartupTimeout(Duration.ofMinutes(3));

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(
            DockerImageName.parse("postgres:16-alpine"))
            .withNetwork(NETWORK)
            .withNetworkAliases("postgres")
            .withDatabaseName("documentstorage_test")
            .withUsername("test")
            .withPassword("test")
            .withExposedPorts(5432)
            .withStartupTimeout(Duration.ofMinutes(2))
            .withCommand("postgres", "-c", "wal_level=logical", "-c", "max_replication_slots=4");

    @Container
    static MongoDBContainer mongoDB = new MongoDBContainer(
            DockerImageName.parse("mongo:7"))
            .withNetwork(NETWORK)
            .withExposedPorts(27017)
            .withStartupTimeout(Duration.ofMinutes(2));

    @Container
    static GenericContainer<?> debezium = new GenericContainer<>(
            DockerImageName.parse("debezium/connect:" + DEBEZIUM_VERSION))
            .withNetwork(NETWORK)
            .withNetworkAliases(KAFKA_CONNECT_HOST)
            .withExposedPorts(8083)
            .withEnv("BOOTSTRAP_SERVERS", "kafka:9092")
            .withEnv("GROUP_ID", "debezium-connect-group")
            .withEnv("CONFIG_STORAGE_TOPIC", "debezium-connect-config")
            .withEnv("OFFSET_STORAGE_TOPIC", "debezium-connect-offsets")
            .withEnv("STATUS_STORAGE_TOPIC", "debezium-connect-status")
            .withEnv("KEY_CONVERTER", "org.apache.kafka.connect.json.JsonConverter")
            .withEnv("VALUE_CONVERTER", "org.apache.kafka.connect.json.JsonConverter")
            .withEnv("TYPEConverter", "true") // Include schema in messages
            .withStartupTimeout(Duration.ofMinutes(3))
            .waitingFor(Wait.forHttp("/").forPort(8083).forStatusCode(200));

    @Autowired
    private DocumentRepositoryPort documentRepository;

    @Autowired
    private SpringDataOutboxRepository outboxRepository;

    private KafkaConsumer<String, String> kafkaConsumer;
    private ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() throws Exception {
        // Wait for all containers to be ready
        await().atMost(2, TimeUnit.MINUTES)
                .ignoreExceptions()
                .until(() -> kafka.isRunning() && postgres.isRunning() &&
                             mongoDB.isRunning() && debezium.isRunning());

        // Clean up any previous test data
        outboxRepository.deleteAll();

        // Configure and register Debezium connector
        registerDebeziumConnector();

        // Create Kafka consumer
        createKafkaConsumer();

        // Wait for Debezium to be ready
        await().atMost(1, TimeUnit.MINUTES)
                .ignoreExceptions()
                .until(() -> isDebeziumConnectorReady());
    }

    @AfterEach
    void tearDown() {
        // Clean up test data
        try {
            outboxRepository.deleteAll();
        } catch (Exception e) {
            log.warn("Error cleaning up test data", e);
        }

        // Close Kafka consumer
        if (kafkaConsumer != null) {
            try {
                kafkaConsumer.close();
            } catch (Exception e) {
                log.warn("Error closing Kafka consumer", e);
            }
        }
    }

    @Test
    @DisplayName("Should capture outbox event with Debezium CDC pipeline")
    void shouldCaptureOutboxEventWithDebezium() {
        // Given
        String documentId = UUID.randomUUID().toString();
        String invoiceId = "INV-" + UUID.randomUUID();

        // Create outbox event using builder
        OutboxEventEntity outboxEvent = OutboxEventEntity.builder()
                .aggregateId(documentId)
                .aggregateType("StoredDocument")
                .eventType("DocumentStoredEvent")
                .payload(String.format("""
                        {
                            "documentId": "%s",
                            "invoiceId": "%s",
                            "documentType": "INVOICE_PDF",
                            "storageUrl": "/test/%s.pdf",
                            "createdAt": "2026-03-09T10:00:00Z"
                        }
                        """, documentId, invoiceId, documentId))
                .createdAt(java.time.Instant.now())
                .status(OutboxStatus.PENDING)
                .topic("document.stored")
                .retryCount(0)
                .build();

        // When - Save outbox event to PostgreSQL
        outboxRepository.save(outboxEvent);
        log.info("Created outbox event for documentId: {}", documentId);

        // Then - Verify Debezium captured the change and published to Kafka
        await().atMost(30, TimeUnit.SECONDS)
                .pollInterval(1, TimeUnit.SECONDS)
                .until(() -> {
                    ConsumerRecords<String, String> records = kafkaConsumer.poll(
                            java.time.Duration.ofSeconds(1));
                    return !records.isEmpty();
                });

        ConsumerRecords<String, String> records = kafkaConsumer.poll(
                java.time.Duration.ofSeconds(1));

        assertThat(records).isNotNull();
        assertThat(records.count()).isGreaterThan(0);

        JsonNode event = parseDebeziumEvent(records.iterator().next().value());

        // Verify Debezium event structure
        assertThat(event.has("schema")).isTrue();
        assertThat(event.has("payload")).isTrue();

        JsonNode payload = event.get("payload");
        assertThat(payload.has("before")).isTrue();
        assertThat(payload.has("after")).isTrue();
        assertThat(payload.has("op")).isTrue();

        JsonNode after = payload.get("after");
        assertThat(after.get("aggregate_id").asText()).isEqualTo(documentId);
        assertThat(after.get("aggregate_type").asText()).isEqualTo("StoredDocument");
        assertThat(after.get("event_type").asText()).isEqualTo("DocumentStoredEvent");
        assertThat(after.get("status").asText()).isEqualTo("PENDING");

        log.info("✓ Debezium CDC pipeline verified: outbox event captured and published to Kafka");
    }

    @Test
    @DisplayName("Should capture multiple outbox events in correct order")
    void shouldCaptureMultipleEventsInCorrectOrder() {
        // Given - Create multiple outbox events
        String documentId1 = UUID.randomUUID().toString();
        String documentId2 = UUID.randomUUID().toString();
        String documentId3 = UUID.randomUUID().toString();

        OutboxEventEntity event1 = createOutboxEvent(documentId1, "DocumentStoredEvent", 1);
        OutboxEventEntity event2 = createOutboxEvent(documentId2, "DocumentStoredEvent", 2);
        OutboxEventEntity event3 = createOutboxEvent(documentId3, "DocumentDeletedEvent", 3);

        // When - Save events in sequence
        outboxRepository.save(event1);
        outboxRepository.save(event2);
        outboxRepository.save(event3);

        log.info("Created 3 outbox events: {}, {}, {}", documentId1, documentId2, documentId3);

        // Then - Verify all events are captured in order
        await().atMost(30, TimeUnit.SECONDS)
                .pollInterval(1, TimeUnit.SECONDS)
                .until(() -> {
                    ConsumerRecords<String, String> records = kafkaConsumer.poll(
                            java.time.Duration.ofSeconds(1));
                    return records.count() >= 3;
                });

        java.util.List<String> capturedIds = new java.util.ArrayList<>();
        ConsumerRecords<String, String> records = kafkaConsumer.poll(
                java.time.Duration.ofSeconds(5));

        records.forEach(record -> {
            JsonNode event = parseDebeziumEvent(record.value());
            JsonNode after = event.get("payload").get("after");
            capturedIds.add(after.get("aggregate_id").asText());
        });

        assertThat(capturedIds).hasSize(3);
        assertThat(capturedIds.get(0)).isEqualTo(documentId1);
        assertThat(capturedIds.get(1)).isEqualTo(documentId2);
        assertThat(capturedIds.get(2)).isEqualTo(documentId3);

        log.info("✓ Multiple events captured in correct order");
    }

    @Test
    @DisplayName("Should handle event status updates through CDC")
    void shouldHandleEventStatusUpdates() {
        // Given
        String documentId = UUID.randomUUID().toString();

        OutboxEventEntity event = OutboxEventEntity.builder()
                .aggregateId(documentId)
                .aggregateType("StoredDocument")
                .eventType("DocumentStoredEvent")
                .payload("{\"test\": \"data\"}")
                .createdAt(java.time.Instant.now())
                .status(OutboxStatus.PENDING)
                .retryCount(0)
                .build();

        // When - Save event with PENDING status
        OutboxEventEntity saved = outboxRepository.save(event);

        // Wait for initial CDC event
        await().atMost(20, TimeUnit.SECONDS)
                .pollInterval(1, TimeUnit.SECONDS)
                .until(() -> {
                    ConsumerRecords<String, String> records = kafkaConsumer.poll(
                            java.time.Duration.ofSeconds(1));
                    return !records.isEmpty();
                });

        // Clear existing messages
        kafkaConsumer.commitSync();

        // Update status to PUBLISHED using builder
        OutboxEventEntity updatedEvent = OutboxEventEntity.builder()
                .id(saved.getId())
                .aggregateId(saved.getAggregateId())
                .aggregateType(saved.getAggregateType())
                .eventType(saved.getEventType())
                .payload(saved.getPayload())
                .createdAt(saved.getCreatedAt())
                .publishedAt(java.time.Instant.now())
                .status(OutboxStatus.PUBLISHED)
                .retryCount(saved.getRetryCount())
                .topic(saved.getTopic())
                .partitionKey(saved.getPartitionKey())
                .headers(saved.getHeaders())
                .errorMessage(saved.getErrorMessage())
                .build();

        outboxRepository.save(updatedEvent);

        log.info("Updated event status to PUBLISHED");

        // Then - Verify status update is captured
        await().atMost(20, TimeUnit.SECONDS)
                .pollInterval(1, TimeUnit.SECONDS)
                .until(() -> {
                    ConsumerRecords<String, String> records = kafkaConsumer.poll(
                            java.time.Duration.ofSeconds(1));
                    if (records.isEmpty()) {
                        return false;
                    }
                    JsonNode eventNode = parseDebeziumEvent(records.iterator().next().value());
                    JsonNode after = eventNode.get("payload").get("after");
                    return "PUBLISHED".equals(after.get("status").asText());
                });

        ConsumerRecords<String, String> updateRecords = kafkaConsumer.poll(
                java.time.Duration.ofSeconds(1));

        JsonNode updateEvent = parseDebeziumEvent(updateRecords.iterator().next().value());
        JsonNode after = updateEvent.get("payload").get("after");

        assertThat(after.get("status").asText()).isEqualTo("PUBLISHED");
        assertThat(after.get("published_at")).isNotNull();

        log.info("✓ Event status updates captured correctly through CDC");
    }

    @Test
    @DisplayName("Should include complete event metadata in CDC payload")
    void shouldIncludeCompleteEventMetadata() {
        // Given
        String documentId = UUID.randomUUID().toString();

        OutboxEventEntity event = new OutboxEventEntity();
        event.setAggregateId(documentId);
        event.setAggregateType("StoredDocument");
        event.setEventType("DocumentStoredEvent");
        event.setPayload("{\"documentId\": \"" + documentId + "\"}");
        event.setCreatedAt(java.time.Instant.now());
        event.setStatus(OutboxStatus.PENDING);
        event.setTopic("document.stored");
        event.setPartitionKey(documentId);
        event.setRetryCount(0);

        // When
        outboxRepository.save(event);

        // Then
        await().atMost(30, TimeUnit.SECONDS)
                .pollInterval(1, TimeUnit.SECONDS)
                .until(() -> {
                    ConsumerRecords<String, String> records = kafkaConsumer.poll(
                            java.time.Duration.ofSeconds(1));
                    return !records.isEmpty();
                });

        ConsumerRecords<String, String> records = kafkaConsumer.poll(
                java.time.Duration.ofSeconds(1));
        JsonNode eventNode = parseDebeziumEvent(records.iterator().next().value());
        JsonNode after = eventNode.get("payload").get("after");

        assertThat(after.get("aggregate_id").asText()).isEqualTo(documentId);
        assertThat(after.get("aggregate_type").asText()).isEqualTo("StoredDocument");
        assertThat(after.get("event_type").asText()).isEqualTo("DocumentStoredEvent");
        assertThat(after.get("status").asText()).isEqualTo("PENDING");
        assertThat(after.get("topic").asText()).isEqualTo("document.stored");
        assertThat(after.get("partition_key").asText()).isEqualTo(documentId);
        assertThat(after.get("retry_count").asInt()).isEqualTo(0);
        assertThat(after.get("created_at")).isNotNull();
        assertThat(after.get("payload")).isNotNull();

        log.info("✓ Complete event metadata included in CDC payload");
    }

    // ========== Helper Methods ==========

    private OutboxEventEntity createOutboxEvent(String documentId, String eventType, int sequence) {
        OutboxEventEntity event = new OutboxEventEntity();
        event.setAggregateId(documentId);
        event.setAggregateType("StoredDocument");
        event.setEventType(eventType);
        event.setPayload("{\"test\": \"data-" + sequence + "\"}");
        event.setCreatedAt(java.time.Instant.now());
        event.setStatus(OutboxStatus.PENDING);
        return event;
    }

    private void registerDebeziumConnector() throws Exception {
        String connectorConfig = String.format("""
                {
                    "name": "postgres-connector",
                    "config": {
                        "connector.class": "io.debezium.connector.postgresql.PostgresConnector",
                        "database.hostname": "postgres",
                        "database.port": 5432,
                        "database.user": "test",
                        "database.password": "test",
                        "database.dbname": "documentstorage_test",
                        "topic.prefix": "debezium-postgres-cdc",
                        "plugin.name": "pgoutput",
                        "table.include.list": "public.outbox_events",
                        "slot.name": "debezium_slot",
                        "publication.name": "debezium_publication",
                        "publication.autocreate.mode": "filtered",
                        "slot.drop.on.stop": "false",
                        "tombstones.on.delete": "false",
                        "include.schema.changes": "false",
                        "transforms": "unwrap",
                        "transforms.unwrap.type": "io.debezium.transforms.ExtractNewRecordState",
                        "transforms.unwrap.drop.tombstones": "true",
                        "transforms.unwrap.add.fields": "op,ts_ms",
                        "key.converter": "org.apache.kafka.connect.json.JsonConverter",
                        "value.converter": "org.apache.kafka.connect.json.JsonConverter",
                        "key.converter.schemas.enable": "false",
                        "value.converter.schemas.enable": "true"
                    }
                }
                """);

        // Create Debezium connector via REST API
        String registerUrl = String.format("http://%s:8083/connectors", KAFKA_CONNECT_HOST);
        java.net.http.HttpClient client = java.net.http.HttpClient.newHttpClient();
        java.net.http.HttpRequest request = java.net.http.HttpRequest.newBuilder()
                .uri(java.net.URI.create(registerUrl))
                .header("Content-Type", "application/json")
                .POST(java.net.http.HttpRequest.BodyPublishers.ofString(connectorConfig))
                .timeout(java.time.Duration.ofSeconds(30))
                .build();

        java.net.http.HttpResponse<String> response = client.send(request,
                java.net.http.HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() == 201 || response.statusCode() == 409) {
            log.info("Debezium connector registered successfully");
        } else {
            throw new RuntimeException("Failed to register Debezium connector: " +
                    response.statusCode() + " - " + response.body());
        }
    }

    private boolean isDebeziumConnectorReady() {
        try {
            String statusUrl = String.format("http://%s:8083/connectors/postgres-connector/status",
                    KAFKA_CONNECT_HOST);
            java.net.http.HttpClient client = java.net.http.HttpClient.newHttpClient();
            java.net.http.HttpRequest request = java.net.http.HttpRequest.newBuilder()
                    .uri(java.net.URI.create(statusUrl))
                    .GET()
                    .timeout(java.time.Duration.ofSeconds(10))
                    .build();

            java.net.http.HttpResponse<String> response = client.send(request,
                    java.net.http.HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                JsonNode status = objectMapper.readTree(response.body());
                JsonNode connector = status.get("connector");
                return connector != null && "RUNNING".equals(connector.get("state").asText());
            }
            return false;
        } catch (Exception e) {
            log.debug("Error checking Debezium connector status: {}", e.getMessage());
            return false;
        }
    }

    private void createKafkaConsumer() {
        Map<String, Object> props = Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG,
                kafka.getBootstrapServers(),
                ConsumerConfig.GROUP_ID_CONFIG,
                "debezium-cdc-test-consumer",
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG,
                StringDeserializer.class.getName(),
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG,
                StringDeserializer.class.getName(),
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG,
                "earliest",
                ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG,
                "false"
        );

        kafkaConsumer = new KafkaConsumer<>(props);
        kafkaConsumer.subscribe(Collections.singletonList(OUTBOX_TOPIC));

        log.info("Kafka consumer created and subscribed to: {}", OUTBOX_TOPIC);
    }

    private JsonNode parseDebeziumEvent(String json) {
        try {
            return objectMapper.readTree(json);
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse Debezium event", e);
        }
    }
}
