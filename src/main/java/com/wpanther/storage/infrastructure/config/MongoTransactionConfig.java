package com.wpanther.storage.infrastructure.config;

import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.MongoDatabaseFactory;
import org.springframework.data.mongodb.MongoTransactionManager;
import org.springframework.data.mongodb.config.AbstractMongoClientConfiguration;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/**
 * Configuration for MongoDB transactions (requires MongoDB 4.0+ with replica set).
 * <p>
 * This configuration enables transactional support for operations spanning both
 * document storage (MongoDB) and outbox events (MongoDB-based outbox pattern).
 * </p>
 * <p>
 * IMPORTANT: MongoDB transactions require a replica set. For development with a
 * standalone MongoDB instance, transactions will not work. In production,
 * ensure MongoDB is configured as a replica set.
 * </p>
 *
 * @see <a href="https://docs.mongodb.com/manual/reference/replica-configuration/">MongoDB Replica Set Configuration</a>
 */
@Configuration
@EnableTransactionManagement
public class MongoTransactionConfig extends AbstractMongoClientConfiguration {

    @Value("${spring.data.mongodb.host:localhost}")
    private String host;

    @Value("${spring.data.mongodb.port:27017}")
    private int port;

    @Value("${spring.data.mongodb.database:document_storage}")
    private String database;

    @Value("${spring.data.mongodb.authentication-database:admin}")
    private String authDatabase;

    @Value("${spring.data.mongodb.username:}")
    private String username;

    @Value("${spring.data.mongodb.password:}")
    private String password;

    @Value("${spring.data.mongodb.replica-set:}")
    private String replicaSet;

    @Override
    protected String getDatabaseName() {
        return database;
    }

    @Override
    @SuppressWarnings("null")
    public MongoClient mongoClient() {
        StringBuilder connectionStringBuilder = new StringBuilder("mongodb://");

        // Add credentials if provided
        if (username != null && !username.isBlank()) {
            connectionStringBuilder.append(username)
                    .append(":")
                    .append(password)
                    .append("@");
        }

        connectionStringBuilder.append(host)
                .append(":")
                .append(port)
                .append("/")
                .append(database);

        // Add authentication database if credentials provided
        if (username != null && !username.isBlank()) {
            connectionStringBuilder.append("?authSource=").append(authDatabase);
        }

        // Add replica set configuration if provided
        if (replicaSet != null && !replicaSet.isBlank()) {
            connectionStringBuilder.append("&replicaSet=").append(replicaSet);
        }

        String connectionString = connectionStringBuilder.toString();

        MongoClientSettings settings = MongoClientSettings.builder()
                .applyConnectionString(new ConnectionString(connectionString))
                .build();

        return MongoClients.create(settings);
    }

    /**
     * Configure MongoTransactionManager as the primary transaction manager.
     * <p>
     * This enables Spring's transaction management to use MongoDB transactions,
     * ensuring atomic operations across MongoDB collections (documents and outbox events).
     * </p>
     *
     * @param dbFactory the MongoDB database factory
     * @return the MongoDB transaction manager
     */
    @Bean
    public MongoTransactionManager transactionManager(MongoDatabaseFactory dbFactory) {
        return new MongoTransactionManager(dbFactory);
    }
}
