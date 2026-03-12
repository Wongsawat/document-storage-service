package com.wpanther.storage;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Document Storage Service Application
 *
 * Microservice for storing and managing documents (PDFs, attachments)
 * with MongoDB metadata storage and pluggable storage backends (local filesystem, S3).
 * Participates in Saga Orchestrator as STORE_DOCUMENT step.
 */
@SpringBootApplication
@EnableDiscoveryClient
@EnableMongoRepositories(basePackages = "com.wpanther.storage.infrastructure.adapter.out.persistence")
@EnableScheduling
public class DocumentStorageServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(DocumentStorageServiceApplication.class, args);
    }
}
