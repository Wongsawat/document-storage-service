package com.wpanther.storage.infrastructure.adapter.out.persistence;

import com.wpanther.storage.domain.model.DocumentType;
import com.wpanther.storage.domain.model.StoredDocument;
import com.wpanther.storage.domain.repository.DocumentRepositoryPort;
import lombok.RequiredArgsConstructor;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationResults;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * MongoDB adapter for document metadata persistence.
 * <p>
 * Implements the {@link DocumentRepositoryPort} outbound port by delegating to
 * {@link MongoDocumentAdapter} (Spring Data MongoDB). Maps between domain
 * {@link StoredDocument} entities and persistence {@link StoredDocumentEntity}.
 * </p>
 */
@Component
@RequiredArgsConstructor
public class DocumentRepositoryAdapter implements DocumentRepositoryPort {

    private final MongoDocumentAdapter mongoDocumentAdapter;
    private final StoredDocumentMapper documentMapper;
    private final MongoTemplate mongoTemplate;

    @Override
    public StoredDocument save(StoredDocument document) {
        StoredDocumentEntity entity = documentMapper.toEntity(document);
        StoredDocumentEntity saved = mongoDocumentAdapter.save(entity);
        return documentMapper.toDomain(saved);
    }

    @Override
    public Optional<StoredDocument> findById(String id) {
        return mongoDocumentAdapter.findById(id)
                .map(documentMapper::toDomain);
    }

    @Override
    public List<StoredDocument> findByInvoiceId(String invoiceId) {
        return mongoDocumentAdapter.findByInvoiceId(invoiceId).stream()
                .map(documentMapper::toDomain)
                .toList();
    }

    @Override
    public Optional<StoredDocument> findByInvoiceIdAndDocumentType(String invoiceId, DocumentType type) {
        return mongoDocumentAdapter.findByInvoiceIdAndDocumentType(invoiceId, type).stream()
                .findFirst()
                .map(documentMapper::toDomain);
    }

    @Override
    public void deleteById(String id) {
        mongoDocumentAdapter.deleteById(id);
    }

    @Override
    public boolean existsByInvoiceIdAndDocumentType(String invoiceId, DocumentType type) {
        return !mongoDocumentAdapter.findByInvoiceIdAndDocumentType(invoiceId, type).isEmpty();
    }

    @Override
    public List<StoredDocument> findByCreatedAtAfter(LocalDateTime timestamp) {
        return mongoDocumentAdapter.findByCreatedAtAfter(timestamp).stream()
                .map(documentMapper::toDomain)
                .toList();
    }

    @Override
    public List<StoredDocument> findByCreatedAtBefore(LocalDateTime timestamp) {
        return mongoDocumentAdapter.findByCreatedAtBefore(timestamp).stream()
                .map(documentMapper::toDomain)
                .toList();
    }

    @Override
    public long countByCreatedAtAfter(LocalDateTime timestamp) {
        return mongoDocumentAdapter.countByCreatedAtAfter(timestamp);
    }

    @Override
    public long countOrphanedDocumentsAfter(LocalDateTime timestamp) {
        // Use MongoDB aggregation with $lookup to find documents without outbox events
        // This avoids N+1 queries by joining documents with outbox_events in a single query
        Aggregation aggregation = Aggregation.newAggregation(
            Aggregation.match(Criteria.where("createdAt").gt(timestamp)),
            Aggregation.lookup("outbox_events", "_id", "aggregateId", "outboxMatches"),
            Aggregation.match(Criteria.where("outboxMatches").size(0)),
            Aggregation.count().as("count")
        );

        AggregationResults<Document> results = mongoTemplate.aggregate(
            aggregation, "stored_documents", Document.class
        );

        Document result = results.getUniqueMappedResult();
        return result != null ? result.getInteger("count", 0) : 0;
    }
}
