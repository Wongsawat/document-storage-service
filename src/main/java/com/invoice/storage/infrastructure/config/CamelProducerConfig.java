package com.invoice.storage.infrastructure.config;

import org.apache.camel.CamelContext;
import org.apache.camel.ProducerTemplate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration for Apache Camel producer template.
 *
 * Provides a ProducerTemplate bean for sending messages to Camel routes,
 * used by EventPublisher to publish integration events to Kafka.
 */
@Configuration
public class CamelProducerConfig {

    /**
     * Create a ProducerTemplate bean for sending messages to Camel routes.
     *
     * The ProducerTemplate is used to send messages to direct: endpoints,
     * which are then processed by Camel routes (e.g., publishing to Kafka).
     *
     * @param camelContext the Camel context
     * @return a ProducerTemplate instance
     */
    @Bean
    public ProducerTemplate producerTemplate(CamelContext camelContext) {
        return camelContext.createProducerTemplate();
    }
}
