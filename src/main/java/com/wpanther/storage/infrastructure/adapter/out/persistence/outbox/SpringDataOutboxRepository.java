package com.wpanther.storage.infrastructure.adapter.out.persistence.outbox;

import com.wpanther.saga.domain.outbox.OutboxStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Repository
public interface SpringDataOutboxRepository extends JpaRepository<OutboxEventEntity, UUID> {

    List<OutboxEventEntity> findByStatusOrderByCreatedAtAsc(OutboxStatus status, Pageable pageable);

    @Query("SELECT e FROM OutboxEventEntity e WHERE e.status = 'FAILED' ORDER BY e.createdAt ASC")
    List<OutboxEventEntity> findFailedEventsOrderByCreatedAtAsc(Pageable pageable);

    List<OutboxEventEntity> findByAggregateTypeAndAggregateIdOrderByCreatedAtAsc(
            String aggregateType,
            String aggregateId
    );

    @Modifying
    @Query("DELETE FROM OutboxEventEntity e WHERE e.status = 'PUBLISHED' AND e.publishedAt < :before")
    int deletePublishedBefore(@Param("before") Instant before);

    /**
     * Check if an outbox event exists for the given aggregate ID and event type.
     */
    boolean existsByAggregateIdAndEventType(String aggregateId, String eventType);

    /**
     * Check if any outbox event exists for the given aggregate ID.
     */
    @Query("SELECT CASE WHEN COUNT(e) > 0 THEN true ELSE false END FROM OutboxEventEntity e WHERE e.aggregateId = :aggregateId")
    boolean existsByAggregateId(@Param("aggregateId") String aggregateId);

    /**
     * Find all distinct aggregate IDs that have at least one outbox event.
     * Used for batch orphan detection to avoid N+1 queries.
     *
     * @param aggregateIds the aggregate IDs to check
     * @return subset of aggregateIds that have at least one outbox event
     */
    @Query("SELECT DISTINCT e.aggregateId FROM OutboxEventEntity e WHERE e.aggregateId IN :aggregateIds")
    List<String> findExistingAggregateIds(@Param("aggregateIds") Collection<String> aggregateIds);

    @Query("SELECT COUNT(e) FROM OutboxEventEntity e WHERE e.status = :status")
    long countByStatus(@Param("status") OutboxStatus status);
}
