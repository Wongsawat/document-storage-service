package com.wpanther.storage.infrastructure.adapter.outbound.persistence.outbox;

import com.wpanther.saga.domain.outbox.OutboxEvent;
import com.wpanther.saga.domain.outbox.OutboxStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("OutboxEventEntity Tests")
class OutboxEventEntityTest {

    @Nested
    @DisplayName("fromDomain()")
    class FromDomainTests {

        @Test
        @DisplayName("Should convert domain OutboxEvent to entity")
        void shouldConvertDomainToEntity() {
            OutboxEvent domainEvent = OutboxEvent.builder()
                .id(UUID.randomUUID())
                .aggregateType("DocumentStorage")
                .aggregateId("doc-123")
                .eventType("document.stored")
                .payload("{\"test\": \"data\"}")
                .createdAt(Instant.now())
                .publishedAt(null)
                .status(OutboxStatus.PENDING)
                .retryCount(0)
                .errorMessage(null)
                .topic("document.stored")
                .partitionKey("doc-123")
                .headers("{\"contentType\": \"application/json\"}")
                .build();

            OutboxEventEntity entity = OutboxEventEntity.fromDomain(domainEvent);

            assertEquals(domainEvent.getId(), entity.getId());
            assertEquals(domainEvent.getAggregateType(), entity.getAggregateType());
            assertEquals(domainEvent.getAggregateId(), entity.getAggregateId());
            assertEquals(domainEvent.getEventType(), entity.getEventType());
            assertEquals(domainEvent.getPayload(), entity.getPayload());
            assertEquals(domainEvent.getCreatedAt(), entity.getCreatedAt());
            assertEquals(domainEvent.getPublishedAt(), entity.getPublishedAt());
            assertEquals(domainEvent.getStatus(), entity.getStatus());
            assertEquals(domainEvent.getRetryCount(), entity.getRetryCount());
            assertEquals(domainEvent.getErrorMessage(), entity.getErrorMessage());
            assertEquals(domainEvent.getTopic(), entity.getTopic());
            assertEquals(domainEvent.getPartitionKey(), entity.getPartitionKey());
            assertEquals(domainEvent.getHeaders(), entity.getHeaders());
        }

        @Test
        @DisplayName("Should convert domain event with all required fields")
        void shouldConvertDomainEventWithRequiredFields() {
            // OutboxEvent requires non-null id, aggregateType, aggregateId, eventType, payload, status, createdAt
            Instant now = Instant.now();
            OutboxEvent domainEvent = OutboxEvent.builder()
                .id(UUID.randomUUID())
                .aggregateType("TestType")
                .aggregateId("test-123")
                .eventType("test.event")
                .payload("{}")
                .status(OutboxStatus.PENDING)
                .createdAt(now)
                .publishedAt(null)
                .retryCount(0)
                .errorMessage(null)
                .topic(null)
                .partitionKey(null)
                .headers(null)
                .build();

            OutboxEventEntity entity = OutboxEventEntity.fromDomain(domainEvent);

            assertEquals(domainEvent.getId(), entity.getId());
            assertEquals(domainEvent.getAggregateType(), entity.getAggregateType());
            assertEquals(domainEvent.getAggregateId(), entity.getAggregateId());
            assertEquals(domainEvent.getEventType(), entity.getEventType());
            assertEquals(domainEvent.getPayload(), entity.getPayload());
            assertEquals(domainEvent.getStatus(), entity.getStatus());
            assertEquals(now, entity.getCreatedAt());
            assertNull(entity.getPublishedAt());
            assertEquals(0, entity.getRetryCount());
            assertNull(entity.getErrorMessage());
            assertNull(entity.getTopic());
            assertNull(entity.getPartitionKey());
            assertNull(entity.getHeaders());
        }
    }

    @Nested
    @DisplayName("toDomain()")
    class ToDomainTests {

        @Test
        @DisplayName("Should convert entity to domain OutboxEvent")
        void shouldConvertEntityToDomain() {
            OutboxEventEntity entity = OutboxEventEntity.builder()
                .id(UUID.randomUUID())
                .aggregateType("DocumentStorage")
                .aggregateId("doc-123")
                .eventType("document.stored")
                .payload("{\"test\": \"data\"}")
                .createdAt(Instant.now())
                .publishedAt(Instant.now())
                .status(OutboxStatus.PUBLISHED)
                .retryCount(1)
                .errorMessage(null)
                .topic("document.stored")
                .partitionKey("doc-123")
                .headers("{\"contentType\": \"application/json\"}")
                .build();

            OutboxEvent domainEvent = entity.toDomain();

            assertEquals(entity.getId(), domainEvent.getId());
            assertEquals(entity.getAggregateType(), domainEvent.getAggregateType());
            assertEquals(entity.getAggregateId(), domainEvent.getAggregateId());
            assertEquals(entity.getEventType(), domainEvent.getEventType());
            assertEquals(entity.getPayload(), domainEvent.getPayload());
            assertEquals(entity.getCreatedAt(), domainEvent.getCreatedAt());
            assertEquals(entity.getPublishedAt(), domainEvent.getPublishedAt());
            assertEquals(entity.getStatus(), domainEvent.getStatus());
            assertEquals(entity.getRetryCount(), domainEvent.getRetryCount());
            assertEquals(entity.getErrorMessage(), domainEvent.getErrorMessage());
            assertEquals(entity.getTopic(), domainEvent.getTopic());
            assertEquals(entity.getPartitionKey(), domainEvent.getPartitionKey());
            assertEquals(entity.getHeaders(), domainEvent.getHeaders());
        }
    }

    @Nested
    @DisplayName("Builder")
    class BuilderTests {

        @Test
        @DisplayName("Should build entity with all fields")
        void shouldBuildEntityWithAllFields() {
            UUID id = UUID.randomUUID();
            Instant now = Instant.now();

            OutboxEventEntity entity = OutboxEventEntity.builder()
                .id(id)
                .aggregateType("DocumentStorage")
                .aggregateId("doc-123")
                .eventType("document.stored")
                .payload("{\"test\": \"data\"}")
                .createdAt(now)
                .publishedAt(now)
                .status(OutboxStatus.PUBLISHED)
                .retryCount(2)
                .errorMessage("Test error")
                .topic("document.stored")
                .partitionKey("doc-123")
                .headers("{\"contentType\": \"application/json\"}")
                .build();

            assertEquals(id, entity.getId());
            assertEquals("DocumentStorage", entity.getAggregateType());
            assertEquals("doc-123", entity.getAggregateId());
            assertEquals("document.stored", entity.getEventType());
            assertEquals("{\"test\": \"data\"}", entity.getPayload());
            assertEquals(now, entity.getCreatedAt());
            assertEquals(now, entity.getPublishedAt());
            assertEquals(OutboxStatus.PUBLISHED, entity.getStatus());
            assertEquals(2, entity.getRetryCount());
            assertEquals("Test error", entity.getErrorMessage());
            assertEquals("document.stored", entity.getTopic());
            assertEquals("doc-123", entity.getPartitionKey());
            assertEquals("{\"contentType\": \"application/json\"}", entity.getHeaders());
        }

        @Test
        @DisplayName("Should build entity with minimal fields")
        void shouldBuildEntityWithMinimalFields() {
            OutboxEventEntity entity = OutboxEventEntity.builder()
                .aggregateType("DocumentStorage")
                .aggregateId("doc-123")
                .eventType("document.stored")
                .payload("{\"test\": \"data\"}")
                .build();

            assertEquals("DocumentStorage", entity.getAggregateType());
            assertEquals("doc-123", entity.getAggregateId());
            assertEquals("document.stored", entity.getEventType());
            assertEquals("{\"test\": \"data\"}", entity.getPayload());
        }
    }

    @Nested
    @DisplayName("@PrePersist lifecycle callback")
    class PrePersistTests {

        @Test
        @DisplayName("Should set default values on create when null")
        void shouldSetDefaultValuesWhenNull() {
            OutboxEventEntity entity = new OutboxEventEntity();

            // Simulate @PrePersist
            entity.onCreate();

            assertNotNull(entity.getId());
            assertNotNull(entity.getStatus());
            assertEquals(OutboxStatus.PENDING, entity.getStatus());
            assertNotNull(entity.getCreatedAt());
            assertNotNull(entity.getRetryCount());
            assertEquals(0, entity.getRetryCount());
        }

        @Test
        @DisplayName("Should not overwrite existing values on create")
        void shouldNotOverwriteExistingValues() {
            UUID id = UUID.randomUUID();
            Instant createdAt = Instant.now().minusSeconds(60);
            OutboxStatus status = OutboxStatus.PUBLISHED;
            Integer retryCount = 5;

            OutboxEventEntity entity = OutboxEventEntity.builder()
                .id(id)
                .createdAt(createdAt)
                .status(status)
                .retryCount(retryCount)
                .build();

            // Simulate @PrePersist
            entity.onCreate();

            assertEquals(id, entity.getId());
            assertEquals(createdAt, entity.getCreatedAt());
            assertEquals(status, entity.getStatus());
            assertEquals(retryCount, entity.getRetryCount());
        }
    }

    @Nested
    @DisplayName("Round-trip conversion")
    class RoundTripTests {

        @Test
        @DisplayName("Should maintain data integrity through round-trip conversion")
        void shouldMaintainDataIntegrity() {
            OutboxEvent originalEvent = OutboxEvent.builder()
                .id(UUID.randomUUID())
                .aggregateType("DocumentStorage")
                .aggregateId("doc-123")
                .eventType("document.stored")
                .payload("{\"test\": \"data\"}")
                .createdAt(Instant.now())
                .publishedAt(null)
                .status(OutboxStatus.PENDING)
                .retryCount(0)
                .errorMessage(null)
                .topic("document.stored")
                .partitionKey("doc-123")
                .headers("{\"contentType\": \"application/json\"}")
                .build();

            OutboxEventEntity entity = OutboxEventEntity.fromDomain(originalEvent);
            OutboxEvent convertedEvent = entity.toDomain();

            assertEquals(originalEvent.getId(), convertedEvent.getId());
            assertEquals(originalEvent.getAggregateType(), convertedEvent.getAggregateType());
            assertEquals(originalEvent.getAggregateId(), convertedEvent.getAggregateId());
            assertEquals(originalEvent.getEventType(), convertedEvent.getEventType());
            assertEquals(originalEvent.getPayload(), convertedEvent.getPayload());
            assertEquals(originalEvent.getCreatedAt(), convertedEvent.getCreatedAt());
            assertEquals(originalEvent.getPublishedAt(), convertedEvent.getPublishedAt());
            assertEquals(originalEvent.getStatus(), convertedEvent.getStatus());
            assertEquals(originalEvent.getRetryCount(), convertedEvent.getRetryCount());
            assertEquals(originalEvent.getErrorMessage(), convertedEvent.getErrorMessage());
            assertEquals(originalEvent.getTopic(), convertedEvent.getTopic());
            assertEquals(originalEvent.getPartitionKey(), convertedEvent.getPartitionKey());
            assertEquals(originalEvent.getHeaders(), convertedEvent.getHeaders());
        }
    }
}
