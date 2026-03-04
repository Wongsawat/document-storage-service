package com.wpanther.storage.infrastructure.config;

import com.wpanther.saga.domain.outbox.OutboxEventRepository;
import com.wpanther.storage.infrastructure.adapter.outbound.persistence.MongoOutboxEventAdapter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.web.client.RestTemplate;

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

    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }
}
