package com.wpanther.storage.infrastructure.adapter.outbound.persistence;

import com.wpanther.storage.domain.model.DocumentType;
import com.wpanther.storage.domain.model.StoredDocument;
import com.wpanther.storage.domain.repository.DocumentRepositoryPort;
import lombok.RequiredArgsConstructor;
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
}
