package com.wpanther.storage.infrastructure.config.outbox;

import com.wpanther.saga.domain.outbox.OutboxEventRepository;
import com.wpanther.storage.infrastructure.adapter.out.persistence.MongoOutboxEventAdapter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.core.MongoTemplate;

/**
 * Configuration for outbox pattern support.
 * Uses MongoDB-based outbox repository for document-storage-service.
 */
@Configuration
public class OutboxConfig {

    @Bean
    @ConditionalOnMissingBean(OutboxEventRepository.class)
    public OutboxEventRepository outboxEventRepository(MongoTemplate mongoTemplate) {
        return new MongoOutboxEventAdapter(mongoTemplate);
    }
}
