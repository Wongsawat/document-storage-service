package com.wpanther.storage.infrastructure.config.outbox;

import com.wpanther.saga.domain.outbox.OutboxEventRepository;
import com.wpanther.storage.infrastructure.adapter.out.persistence.MongoOutboxEventAdapter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.core.MongoTemplate;

/**
 * Configuration for outbox pattern support.
 * Uses MongoDB-based outbox repository for document-storage-service.
 * <p>
 * This enables atomic transactions between document storage and outbox events,
 * both stored in MongoDB within the same transaction.
 * </p>
 */
@Configuration
public class OutboxConfig {

    /**
     * Creates the MongoDB-based outbox event repository.
     * <p>
     * This repository stores outbox events in MongoDB, enabling transactional
     * atomicity with document storage operations via MongoDB transactions.
     * </p>
     *
     * @param mongoTemplate the MongoDB template
     * @return the MongoDB-based outbox event repository
     */
    @Bean
    public OutboxEventRepository outboxEventRepository(MongoTemplate mongoTemplate) {
        return new MongoOutboxEventAdapter(mongoTemplate);
    }
}
