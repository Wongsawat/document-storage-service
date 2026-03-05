package com.wpanther.storage.infrastructure.adapter.outbound.persistence.outbox;

import com.wpanther.saga.domain.outbox.OutboxEvent;
import com.wpanther.saga.domain.outbox.OutboxStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("JpaOutboxEventRepository Tests")
class JpaOutboxEventRepositoryTest {

    @Mock
    private SpringDataOutboxRepository springRepository;

    @InjectMocks
    private JpaOutboxEventRepository repository;

    private OutboxEvent testEvent;
    private OutboxEventEntity testEntity;
    private UUID testId;

    @BeforeEach
    void setUp() {
        testId = UUID.randomUUID();
        Instant now = Instant.now();

        testEvent = OutboxEvent.builder()
            .id(testId)
            .aggregateType("DocumentStorage")
            .aggregateId("doc-123")
            .eventType("document.stored")
            .payload("{\"test\": \"data\"}")
            .createdAt(now)
            .publishedAt(null)
            .status(OutboxStatus.PENDING)
            .retryCount(0)
            .errorMessage(null)
            .topic("document.stored")
            .partitionKey("doc-123")
            .headers("{\"contentType\": \"application/json\"}")
            .build();

        testEntity = OutboxEventEntity.builder()
            .id(testId)
            .aggregateType("DocumentStorage")
            .aggregateId("doc-123")
            .eventType("document.stored")
            .payload("{\"test\": \"data\"}")
            .createdAt(now)
            .publishedAt(null)
            .status(OutboxStatus.PENDING)
            .retryCount(0)
            .errorMessage(null)
            .topic("document.stored")
            .partitionKey("doc-123")
            .headers("{\"contentType\": \"application/json\"}")
            .build();
    }

    @Nested
    @DisplayName("save()")
    class SaveTests {

        @Test
        @DisplayName("Should save outbox event successfully")
        void shouldSaveOutboxEventSuccessfully() {
            when(springRepository.save(any(OutboxEventEntity.class))).thenReturn(testEntity);

            OutboxEvent result = repository.save(testEvent);

            assertNotNull(result);
            assertEquals(testEvent.getId(), result.getId());
            assertEquals(testEvent.getAggregateType(), result.getAggregateType());
            assertEquals(testEvent.getAggregateId(), result.getAggregateId());

            verify(springRepository).save(any(OutboxEventEntity.class));
        }

        @Test
        @DisplayName("Should convert domain event to entity before saving")
        void shouldConvertDomainToEntityBeforeSaving() {
            when(springRepository.save(any(OutboxEventEntity.class))).thenReturn(testEntity);

            repository.save(testEvent);

            verify(springRepository).save(argThat(entity ->
                entity.getId().equals(testEvent.getId()) &&
                entity.getAggregateType().equals(testEvent.getAggregateType()) &&
                entity.getAggregateId().equals(testEvent.getAggregateId())
            ));
        }
    }

    @Nested
    @DisplayName("findById()")
    class FindByIdTests {

        @Test
        @DisplayName("Should return event when found")
        void shouldReturnEventWhenFound() {
            when(springRepository.findById(testId)).thenReturn(Optional.of(testEntity));

            Optional<OutboxEvent> result = repository.findById(testId);

            assertTrue(result.isPresent());
            assertEquals(testId, result.get().getId());
            assertEquals(testEvent.getAggregateType(), result.get().getAggregateType());

            verify(springRepository).findById(testId);
        }

        @Test
        @DisplayName("Should return empty optional when not found")
        void shouldReturnEmptyOptionalWhenNotFound() {
            when(springRepository.findById(testId)).thenReturn(Optional.empty());

            Optional<OutboxEvent> result = repository.findById(testId);

            assertFalse(result.isPresent());

            verify(springRepository).findById(testId);
        }

        @Test
        @DisplayName("Should convert entity to domain event")
        void shouldConvertEntityToDomainEvent() {
            when(springRepository.findById(testId)).thenReturn(Optional.of(testEntity));

            Optional<OutboxEvent> result = repository.findById(testId);

            assertTrue(result.isPresent());
            assertEquals(testEntity.getId(), result.get().getId());
            assertEquals(testEntity.getAggregateType(), result.get().getAggregateType());
        }
    }

    @Nested
    @DisplayName("findPendingEvents()")
    class FindPendingEventsTests {

        @Test
        @DisplayName("Should return list of pending events")
        void shouldReturnListOfPendingEvents() {
            List<OutboxEventEntity> entities = List.of(testEntity);
            when(springRepository.findByStatusOrderByCreatedAtAsc(
                eq(OutboxStatus.PENDING), any()))
                .thenReturn(entities);

            List<OutboxEvent> result = repository.findPendingEvents(10);

            assertNotNull(result);
            assertEquals(1, result.size());
            assertEquals(testEvent.getId(), result.get(0).getId());

            verify(springRepository).findByStatusOrderByCreatedAtAsc(
                eq(OutboxStatus.PENDING), any());
        }

        @Test
        @DisplayName("Should return empty list when no pending events")
        void shouldReturnEmptyListWhenNoPendingEvents() {
            when(springRepository.findByStatusOrderByCreatedAtAsc(
                eq(OutboxStatus.PENDING), any()))
                .thenReturn(List.of());

            List<OutboxEvent> result = repository.findPendingEvents(10);

            assertNotNull(result);
            assertTrue(result.isEmpty());

            verify(springRepository).findByStatusOrderByCreatedAtAsc(
                eq(OutboxStatus.PENDING), any());
        }

        @Test
        @DisplayName("Should use provided limit")
        void shouldUseProvidedLimit() {
            when(springRepository.findByStatusOrderByCreatedAtAsc(
                eq(OutboxStatus.PENDING), any()))
                .thenReturn(List.of());

            repository.findPendingEvents(50);

            verify(springRepository).findByStatusOrderByCreatedAtAsc(
                eq(OutboxStatus.PENDING), any());
        }
    }

    @Nested
    @DisplayName("findFailedEvents()")
    class FindFailedEventsTests {

        @Test
        @DisplayName("Should return list of failed events")
        void shouldReturnListOfFailedEvents() {
            OutboxEventEntity failedEntity = OutboxEventEntity.builder()
                .id(UUID.randomUUID())
                .aggregateType("DocumentStorage")
                .aggregateId("doc-failed")
                .eventType("document.stored")
                .payload("{\"test\": \"data\"}")
                .createdAt(Instant.now())
                .status(OutboxStatus.FAILED)
                .retryCount(3)
                .errorMessage("Connection timeout")
                .build();

            List<OutboxEventEntity> entities = List.of(failedEntity);
            when(springRepository.findFailedEventsOrderByCreatedAtAsc(any()))
                .thenReturn(entities);

            List<OutboxEvent> result = repository.findFailedEvents(10);

            assertNotNull(result);
            assertEquals(1, result.size());
            assertEquals(OutboxStatus.FAILED, result.get(0).getStatus());
            assertEquals("Connection timeout", result.get(0).getErrorMessage());

            verify(springRepository).findFailedEventsOrderByCreatedAtAsc(any());
        }

        @Test
        @DisplayName("Should return empty list when no failed events")
        void shouldReturnEmptyListWhenNoFailedEvents() {
            when(springRepository.findFailedEventsOrderByCreatedAtAsc(any()))
                .thenReturn(List.of());

            List<OutboxEvent> result = repository.findFailedEvents(10);

            assertNotNull(result);
            assertTrue(result.isEmpty());
        }
    }

    @Nested
    @DisplayName("deletePublishedBefore()")
    class DeletePublishedBeforeTests {

        @Test
        @DisplayName("Should delete published events before given date")
        void shouldDeletePublishedEventsBeforeGivenDate() {
            Instant beforeDate = Instant.now().minusSeconds(3600);
            when(springRepository.deletePublishedBefore(beforeDate)).thenReturn(5);

            int result = repository.deletePublishedBefore(beforeDate);

            assertEquals(5, result);
            verify(springRepository).deletePublishedBefore(beforeDate);
        }

        @Test
        @DisplayName("Should return zero when no events deleted")
        void shouldReturnZeroWhenNoEventsDeleted() {
            Instant beforeDate = Instant.now();
            when(springRepository.deletePublishedBefore(beforeDate)).thenReturn(0);

            int result = repository.deletePublishedBefore(beforeDate);

            assertEquals(0, result);
        }
    }

    @Nested
    @DisplayName("findByAggregate()")
    class FindByAggregateTests {

        @Test
        @DisplayName("Should return events for given aggregate")
        void shouldReturnEventsForGivenAggregate() {
            List<OutboxEventEntity> entities = List.of(testEntity);
            when(springRepository.findByAggregateTypeAndAggregateIdOrderByCreatedAtAsc(
                "DocumentStorage", "doc-123"))
                .thenReturn(entities);

            List<OutboxEvent> result = repository.findByAggregate("DocumentStorage", "doc-123");

            assertNotNull(result);
            assertEquals(1, result.size());
            assertEquals("DocumentStorage", result.get(0).getAggregateType());
            assertEquals("doc-123", result.get(0).getAggregateId());

            verify(springRepository).findByAggregateTypeAndAggregateIdOrderByCreatedAtAsc(
                "DocumentStorage", "doc-123");
        }

        @Test
        @DisplayName("Should return empty list when no events for aggregate")
        void shouldReturnEmptyListWhenNoEventsForAggregate() {
            when(springRepository.findByAggregateTypeAndAggregateIdOrderByCreatedAtAsc(
                "DocumentStorage", "nonexistent"))
                .thenReturn(List.of());

            List<OutboxEvent> result = repository.findByAggregate("DocumentStorage", "nonexistent");

            assertNotNull(result);
            assertTrue(result.isEmpty());
        }
    }
}
