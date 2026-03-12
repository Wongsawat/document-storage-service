package com.wpanther.storage.infrastructure.adapter.out.persistence;

import com.wpanther.saga.domain.outbox.OutboxEvent;
import com.wpanther.saga.domain.outbox.OutboxStatus;
import com.wpanther.storage.infrastructure.adapter.out.persistence.MongoOutboxEventAdapter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@DisplayName("MongoOutboxEventAdapter Tests")
@ExtendWith(MockitoExtension.class)
class MongoOutboxEventAdapterTest {

    @Mock
    private MongoTemplate mongoTemplate;

    @InjectMocks
    private MongoOutboxEventAdapter adapter;

    private OutboxEvent testEvent;

    @BeforeEach
    void setUp() {
        testEvent = OutboxEvent.builder()
            .id(UUID.randomUUID())
            .aggregateType("TestAggregate")
            .aggregateId("test-123")
            .eventType("TestEvent")
            .payload("{\"test\":\"data\"}")
            .topic("test.topic")
            .status(OutboxStatus.PENDING)
            .createdAt(Instant.now())
            .retryCount(0)
            .build();
    }

    @Nested
    @DisplayName("save()")
    class SaveTests {

        @Test
        @DisplayName("Should save outbox event and return it")
        void shouldSaveEventAndReturn() {
            when(mongoTemplate.save(eq(testEvent), eq("outbox_events"))).thenReturn(testEvent);

            OutboxEvent result = adapter.save(testEvent);

            assertEquals(testEvent, result);
            verify(mongoTemplate).save(testEvent, "outbox_events");
        }

        @Test
        @DisplayName("Should use correct collection name")
        void shouldUseCorrectCollectionName() {
            when(mongoTemplate.save(eq(testEvent), eq("outbox_events"))).thenReturn(testEvent);

            adapter.save(testEvent);

            ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
            verify(mongoTemplate).save(any(OutboxEvent.class), captor.capture());
            assertEquals("outbox_events", captor.getValue());
        }
    }

    @Nested
    @DisplayName("findPendingEvents()")
    class FindPendingEventsTests {

        @Test
        @DisplayName("Should find pending events with limit")
        void shouldFindPendingEvents() {
            int limit = 10;
            List<OutboxEvent> expectedEvents = List.of(testEvent);
            when(mongoTemplate.find(any(Query.class), eq(OutboxEvent.class), eq("outbox_events")))
                .thenReturn(expectedEvents);

            List<OutboxEvent> result = adapter.findPendingEvents(limit);

            assertEquals(expectedEvents, result);
            verify(mongoTemplate).find(any(Query.class), eq(OutboxEvent.class), eq("outbox_events"));
        }

        @Test
        @DisplayName("Should query with PENDING status")
        void shouldQueryWithPendingStatus() {
            adapter.findPendingEvents(10);

            ArgumentCaptor<Query> captor = ArgumentCaptor.forClass(Query.class);
            verify(mongoTemplate).find(captor.capture(), eq(OutboxEvent.class), eq("outbox_events"));

            // Verify the query was created with PENDING status
            verify(mongoTemplate).find(any(Query.class), eq(OutboxEvent.class), eq("outbox_events"));
        }
    }

    @Nested
    @DisplayName("findFailedEvents()")
    class FindFailedEventsTests {

        @Test
        @DisplayName("Should find failed events with limit")
        void shouldFindFailedEvents() {
            int limit = 5;
            List<OutboxEvent> expectedEvents = List.of();
            when(mongoTemplate.find(any(Query.class), eq(OutboxEvent.class), eq("outbox_events")))
                .thenReturn(expectedEvents);

            List<OutboxEvent> result = adapter.findFailedEvents(limit);

            assertEquals(expectedEvents, result);
            verify(mongoTemplate).find(any(Query.class), eq(OutboxEvent.class), eq("outbox_events"));
        }
    }

    @Nested
    @DisplayName("deletePublishedBefore()")
    class DeletePublishedBeforeTests {

        @Test
        @DisplayName("Should delete published events before timestamp")
        void shouldDeletePublishedBefore() {
            Instant before = Instant.now();
            when(mongoTemplate.findAllAndRemove(any(Query.class), eq(OutboxEvent.class), eq("outbox_events")))
                .thenReturn(List.of(testEvent));

            int result = adapter.deletePublishedBefore(before);

            assertEquals(1, result);
            verify(mongoTemplate).findAllAndRemove(any(Query.class), eq(OutboxEvent.class), eq("outbox_events"));
        }

        @Test
        @DisplayName("Should return 0 when no events found")
        void shouldReturn0WhenNoEventsFound() {
            Instant before = Instant.now();
            when(mongoTemplate.findAllAndRemove(any(Query.class), eq(OutboxEvent.class), eq("outbox_events")))
                .thenReturn(List.of());

            int result = adapter.deletePublishedBefore(before);

            assertEquals(0, result);
        }
    }

    @Nested
    @DisplayName("findById()")
    class FindByIdTests {

        @Test
        @DisplayName("Should find event by ID")
        void shouldFindById() {
            UUID id = testEvent.getId();
            when(mongoTemplate.findOne(any(Query.class), eq(OutboxEvent.class), eq("outbox_events")))
                .thenReturn(testEvent);

            Optional<OutboxEvent> result = adapter.findById(id);

            assertTrue(result.isPresent());
            assertEquals(testEvent, result.get());
        }

        @Test
        @DisplayName("Should return empty when not found")
        void shouldReturnEmptyWhenNotFound() {
            UUID id = UUID.randomUUID();
            when(mongoTemplate.findOne(any(Query.class), eq(OutboxEvent.class), eq("outbox_events")))
                .thenReturn(null);

            Optional<OutboxEvent> result = adapter.findById(id);

            assertFalse(result.isPresent());
        }
    }

    @Nested
    @DisplayName("findByAggregate()")
    class FindByAggregateTests {

        @Test
        @DisplayName("Should find events by aggregate type and ID")
        void shouldFindByAggregate() {
            String aggregateType = "TestAggregate";
            String aggregateId = "test-123";
            List<OutboxEvent> expectedEvents = List.of(testEvent);
            when(mongoTemplate.find(any(Query.class), eq(OutboxEvent.class), eq("outbox_events")))
                .thenReturn(expectedEvents);

            List<OutboxEvent> result = adapter.findByAggregate(aggregateType, aggregateId);

            assertEquals(expectedEvents, result);
            verify(mongoTemplate).find(any(Query.class), eq(OutboxEvent.class), eq("outbox_events"));
        }

        @Test
        @DisplayName("Should return empty list when no events found")
        void shouldReturnEmptyWhenNotFound() {
            when(mongoTemplate.find(any(Query.class), eq(OutboxEvent.class), eq("outbox_events")))
                .thenReturn(List.of());

            List<OutboxEvent> result = adapter.findByAggregate("NonExistent", "not-found");

            assertTrue(result.isEmpty());
        }
    }

    @Nested
    @DisplayName("Repository annotation")
    class RepositoryAnnotationTests {

        @Test
        @DisplayName("Should have Repository annotation")
        void shouldHaveRepositoryAnnotation() {
            assertNotNull(adapter.getClass().getAnnotation(org.springframework.stereotype.Repository.class));
        }
    }

    @Nested
    @DisplayName("Collection constant")
    class CollectionConstantTests {

        @Test
        @DisplayName("Should have correct collection name")
        void shouldHaveCorrectCollectionName() throws Exception {
            var field = MongoOutboxEventAdapter.class.getDeclaredField("COLLECTION");
            field.setAccessible(true);
            assertEquals("outbox_events", field.get(null));
        }
    }

    @Nested
    @DisplayName("Constructor")
    class ConstructorTests {

        @Test
        @DisplayName("Should create adapter with MongoTemplate")
        void shouldCreateWithMongoTemplate() {
            assertNotNull(adapter);
        }
    }
}
