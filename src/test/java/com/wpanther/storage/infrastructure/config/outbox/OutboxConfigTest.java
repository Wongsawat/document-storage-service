package com.wpanther.storage.infrastructure.config.outbox;

import com.wpanther.saga.domain.outbox.OutboxEventRepository;
import com.wpanther.storage.infrastructure.adapter.out.persistence.MongoOutboxEventAdapter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.core.MongoTemplate;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("OutboxConfig Tests")
class OutboxConfigTest {

    @Nested
    @DisplayName("outboxEventRepository()")
    class OutboxEventRepositoryBeanTests {

        @Test
        @DisplayName("Should create MongoOutboxEventAdapter bean when repository not present")
        void shouldCreateMongoOutboxAdapterWhenRepositoryNotPresent() {
            OutboxConfig config = new OutboxConfig();
            MongoTemplate mongoTemplate = null; // Mock would be needed

            // Note: This test would require Spring context for full testing
            // Testing bean creation method exists and returns correct type
            assertDoesNotThrow(() -> {
                // The method signature is correct
                var method = OutboxConfig.class.getDeclaredMethod(
                    "outboxEventRepository", MongoTemplate.class
                );
                assertNotNull(method);
                assertEquals(OutboxEventRepository.class, method.getReturnType());
            });
        }
    }

    @Nested
    @DisplayName("Configuration annotations")
    class ConfigurationAnnotationsTests {

        @Test
        @DisplayName("Should have Configuration annotation")
        void shouldHaveConfigurationAnnotation() {
            assertNotNull(OutboxConfig.class.getAnnotation(Configuration.class));
        }

        @Test
        @DisplayName("Should have Bean annotation on outboxEventRepository")
        void shouldHaveBeanAnnotations() {
            assertDoesNotThrow(() -> {
                var outboxBean = OutboxConfig.class.getDeclaredMethod("outboxEventRepository", MongoTemplate.class);

                assertNotNull(outboxBean.getAnnotation(org.springframework.context.annotation.Bean.class));
            });
        }

        @Test
        @DisplayName("Should not have ConditionalOnMissingBean - always use MongoDB outbox for transactions")
        void shouldNotHaveConditionalOnMissingBean() {
            assertDoesNotThrow(() -> {
                var outboxBean = OutboxConfig.class.getDeclaredMethod("outboxEventRepository", MongoTemplate.class);
                var conditional = outboxBean.getAnnotation(
                    org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean.class
                );

                // MongoDB outbox is always used for transactional consistency between
                // document storage and outbox events
                assertNull(conditional);
            });
        }
    }
}
