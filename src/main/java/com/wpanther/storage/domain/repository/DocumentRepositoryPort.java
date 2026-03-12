package com.wpanther.storage.domain.repository;

import com.wpanther.storage.domain.model.StoredDocument;
import com.wpanther.storage.domain.model.DocumentType;
import java.util.List;
import java.util.Optional;

/**
 * Outbound port for document metadata persistence.
 * <p>
 * This port abstracts the metadata repository, implemented by
 * {@link com.wpanther.storage.infrastructure.adapter.out.persistence.MongoDocumentAdapter}.
 * </p>
 * <p>
 * The repository stores document metadata (filename, content type, storage location,
 * checksum, etc.) in MongoDB. The actual file content is stored separately via
 * {@link com.wpanther.storage.application.port.out.StorageProviderPort}.
 * </p>
 * <p>
 * All operations are non-transactional with respect to MongoDB. For transactional
 * behavior across MongoDB and the outbox table, rely on the compensating actions
 * in the saga pattern.
 * </p>
 */
public interface DocumentRepositoryPort {

    /**
     * Save or update document metadata.
     * <p>
     * If the document has an ID, it will be updated. Otherwise, a new document
     * will be inserted.
     * </p>
     *
     * @param document the document metadata to save
     * @return the saved document with any generated fields populated
     */
    StoredDocument save(StoredDocument document);

    /**
     * Find document metadata by ID.
     *
     * @param id the unique document identifier
     * @return the document metadata, or empty if not found
     */
    Optional<StoredDocument> findById(String id);

    /**
     * Find all documents associated with an invoice ID.
     * <p>
     * Returns documents of all types (PDF, XML, etc.) for the given invoice.
     * </p>
     *
     * @param invoiceId the invoice ID to query
     * @return list of documents associated with the invoice (empty if none found)
     */
    List<StoredDocument> findByInvoiceId(String invoiceId);

    /**
     * Find a document by invoice ID and document type.
     * <p>
     * Used for idempotency checks in saga operations.
     * </p>
     *
     * @param invoiceId the invoice ID to query
     * @param type the document type to filter by
     * @return the document metadata, or empty if not found
     */
    Optional<StoredDocument> findByInvoiceIdAndDocumentType(String invoiceId, DocumentType type);

    /**
     * Delete document metadata by ID.
     * <p>
     * Only removes metadata from MongoDB. Does not delete the physical file.
     * File deletion should be handled via {@link StorageProviderPort#delete}.
     * </p>
     *
     * @param id the unique document identifier
     */
    void deleteById(String id);

    /**
     * Check if a document exists for the given invoice ID and document type.
     * <p>
     * Used for idempotency checks to prevent duplicate storage.
     * </p>
     *
     * @param invoiceId the invoice ID to check
     * @param type the document type to check
     * @return true if a matching document exists, false otherwise
     */
    boolean existsByInvoiceIdAndDocumentType(String invoiceId, DocumentType type);

    /**
     * Find documents created after a specific timestamp.
     * <p>
     * Used for reconciliation to find recent documents.
     * </p>
     *
     * @param timestamp the cutoff timestamp
     * @return list of documents created after the timestamp
     */
    List<StoredDocument> findByCreatedAtAfter(java.time.LocalDateTime timestamp);

    /**
     * Find documents created before a specific timestamp.
     * <p>
     * Used for cleanup to find old documents.
     * </p>
     *
     * @param timestamp the cutoff timestamp
     * @return list of documents created before the timestamp
     */
    List<StoredDocument> findByCreatedAtBefore(java.time.LocalDateTime timestamp);

    /**
     * Count documents created after a specific timestamp.
     * <p>
     * Used for reconciliation statistics.
     * </p>
     *
     * @param timestamp the cutoff timestamp
     * @return count of documents created after the timestamp
     */
    long countByCreatedAtAfter(java.time.LocalDateTime timestamp);
}
