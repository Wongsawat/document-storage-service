package com.wpanther.storage.infrastructure.adapter.outbound.persistence;

import com.wpanther.storage.domain.model.DocumentType;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface MongoDocumentAdapter extends MongoRepository<StoredDocumentEntity, String> {

    Optional<StoredDocumentEntity> findByFileName(String fileName);

    List<StoredDocumentEntity> findByDocumentType(DocumentType documentType);

    List<StoredDocumentEntity> findByInvoiceId(String invoiceId);

    List<StoredDocumentEntity> findByInvoiceIdAndDocumentType(String invoiceId, DocumentType documentType);

    List<StoredDocumentEntity> findByInvoiceNumber(String invoiceNumber);

    List<StoredDocumentEntity> findByExpiresAtBefore(LocalDateTime dateTime);

    long countByDocumentType(DocumentType documentType);

    /**
     * Find documents created after a specific timestamp.
     * Used for reconciliation to find recent documents.
     */
    List<StoredDocumentEntity> findByCreatedAtAfter(LocalDateTime timestamp);

    /**
     * Find documents created before a specific timestamp.
     * Used for cleanup to find old documents.
     */
    List<StoredDocumentEntity> findByCreatedAtBefore(LocalDateTime timestamp);

    /**
     * Count documents created after a specific timestamp.
     * Used for reconciliation statistics.
     */
    long countByCreatedAtAfter(LocalDateTime timestamp);
}
