package com.invoice.storage;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;
import org.springframework.kafka.annotation.EnableKafka;

/**
 * Document Storage Service Application
 *
 * Microservice for storing and managing documents (PDFs, attachments)
 * with MongoDB metadata storage and pluggable storage backends (local filesystem, S3)
 */
@SpringBootApplication
@EnableDiscoveryClient
@EnableKafka
@EnableMongoRepositories(basePackages = "com.invoice.storage.infrastructure.persistence")
public class DocumentStorageServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(DocumentStorageServiceApplication.class, args);
    }
}
