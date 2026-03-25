package com.wpanther.storage.infrastructure.adapter.out.persistence;

import com.wpanther.saga.domain.outbox.OutboxEvent;
import com.wpanther.saga.domain.outbox.OutboxEventRepository;
import com.wpanther.saga.domain.outbox.OutboxStatus;
import com.wpanther.storage.application.port.out.OutboxRepositoryPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * MongoDB implementation of the OutboxEventRepository.
 * Provides outbox pattern support for MongoDB-based services.
 * <p>
 * Also implements {@link OutboxRepositoryPort} to provide outbox monitoring
 * capabilities for health indicators.
 * </p>
 */
@Repository
@RequiredArgsConstructor
@Slf4j
public class MongoOutboxEventAdapter implements OutboxEventRepository, OutboxRepositoryPort {

    private static final String COLLECTION = "outbox_events";

    private final MongoTemplate mongoTemplate;

    @Override
    public OutboxEvent save(OutboxEvent event) {
        log.debug("Saving outbox event: {} for aggregate: {}/{}",
                event.getId(), event.getAggregateType(), event.getAggregateId());
        mongoTemplate.save(event, COLLECTION);
        return event;
    }

    @Override
    public List<OutboxEvent> findPendingEvents(int limit) {
        Query query = new Query(Criteria.where("status").is(OutboxStatus.PENDING))
                .with(Sort.by(Sort.Direction.ASC, "createdAt"))
                .limit(limit);
        return mongoTemplate.find(query, OutboxEvent.class, COLLECTION);
    }

    @Override
    public List<OutboxEvent> findFailedEvents(int limit) {
        Query query = new Query(Criteria.where("status").is(OutboxStatus.FAILED))
                .with(Sort.by(Sort.Direction.ASC, "createdAt"))
                .limit(limit);
        return mongoTemplate.find(query, OutboxEvent.class, COLLECTION);
    }

    @Override
    public int deletePublishedBefore(Instant before) {
        Query query = new Query(Criteria.where("status").is(OutboxStatus.PUBLISHED)
                .and("publishedAt").lt(before));
        var deleted = mongoTemplate.findAllAndRemove(query, OutboxEvent.class, COLLECTION);
        int deletedCount = deleted.size();
        log.info("Deleted {} published events before: {}", deletedCount, before);
        return deletedCount;
    }

    @Override
    public Optional<OutboxEvent> findById(UUID id) {
        Query query = new Query(Criteria.where("id").is(id));
        return Optional.ofNullable(mongoTemplate.findOne(query, OutboxEvent.class, COLLECTION));
    }

    @Override
    public List<OutboxEvent> findByAggregate(String aggregateType, String aggregateId) {
        Query query = new Query(Criteria.where("aggregateType").is(aggregateType)
                .and("aggregateId").is(aggregateId))
                .with(Sort.by(Sort.Direction.ASC, "createdAt"));
        return mongoTemplate.find(query, OutboxEvent.class, COLLECTION);
    }

    @Override
    public void deleteById(String id) {
        Query query = new Query(Criteria.where("id").is(UUID.fromString(id)));
        mongoTemplate.remove(query, OutboxEvent.class, COLLECTION);
    }
}
