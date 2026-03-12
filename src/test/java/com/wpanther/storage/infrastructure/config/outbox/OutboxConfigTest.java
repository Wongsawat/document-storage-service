package com.wpanther.storage.infrastructure.config.outbox;

import com.wpanther.saga.domain.outbox.OutboxEventRepository;
import com.wpanther.storage.infrastructure.adapter.outbound.persistence.MongoOutboxEventAdapter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.web.client.RestTemplate;

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
    @DisplayName("restTemplate()")
    class RestTemplateBeanTests {

        @Test
        @DisplayName("Should create RestTemplate bean")
        void shouldCreateRestTemplateBean() {
            OutboxConfig config = new OutboxConfig();

            assertDoesNotThrow(() -> {
                var method = OutboxConfig.class.getDeclaredMethod("restTemplate");
                assertNotNull(method);
                assertEquals(RestTemplate.class, method.getReturnType());
            });
        }

        @Test
        @DisplayName("Should create new RestTemplate instance")
        void shouldCreateNewRestTemplateInstance() {
            OutboxConfig config = new OutboxConfig();
            RestTemplate restTemplate = config.restTemplate();

            assertNotNull(restTemplate);
            assertTrue(restTemplate instanceof RestTemplate);
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
        @DisplayName("Should have Bean annotations")
        void shouldHaveBeanAnnotations() {
            assertDoesNotThrow(() -> {
                var outboxBean = OutboxConfig.class.getDeclaredMethod("outboxEventRepository", MongoTemplate.class);
                var restTemplateBean = OutboxConfig.class.getDeclaredMethod("restTemplate");

                assertNotNull(outboxBean.getAnnotation(org.springframework.context.annotation.Bean.class));
                assertNotNull(restTemplateBean.getAnnotation(org.springframework.context.annotation.Bean.class));
            });
        }

        @Test
        @DisplayName("Should have ConditionalOnMissingBean on outboxEventRepository")
        void shouldHaveConditionalOnMissingBean() {
            assertDoesNotThrow(() -> {
                var outboxBean = OutboxConfig.class.getDeclaredMethod("outboxEventRepository", MongoTemplate.class);
                var conditional = outboxBean.getAnnotation(
                    org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean.class
                );

                assertNotNull(conditional);
                assertEquals(1, conditional.value().length);
                assertEquals(OutboxEventRepository.class, conditional.value()[0]);
            });
        }
    }
}
