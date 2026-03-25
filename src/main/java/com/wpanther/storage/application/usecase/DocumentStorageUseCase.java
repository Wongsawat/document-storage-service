package com.wpanther.storage.application.usecase;

import com.wpanther.storage.domain.model.StoredDocument;
import com.wpanther.storage.domain.model.DocumentType;

import java.io.InputStream;
import java.util.List;
import java.util.Optional;

/**
 * Inbound port for document storage operations.
 * <p>
 * This port defines the primary use cases for document storage, implemented by
 * {@link com.wpanther.storage.application.usecase.FileStorageDomainService} and called by
 * {@link com.wpanther.storage.infrastructure.adapter.in.rest.DocumentStorageController}.
 * </p>
 * <p>
 * Operations follow a simple CRUD pattern for document metadata and content:
 * <ul>
 *   <li>Store documents with automatic checksum calculation</li>
 *   <li>Retrieve document metadata and content</li>
 *   <li>Delete documents with physical file cleanup</li>
 *   <li>Query documents by invoice ID</li>
 * </ul>
 * </p>
 * <p>
 * All operations participate in the current transaction context when annotated with
 * {@link org.springframework.transaction.annotation.Transactional}.
 * </p>
 */
public interface DocumentStorageUseCase {

    /**
     * Store a document with content type auto-detected from filename.
     * <p>
     * The document is stored in the configured storage backend (local filesystem or S3),
     * metadata is persisted in MongoDB, and a SHA-256 checksum is calculated.
     * </p>
     *
     * @param content the document content bytes (must not be null or empty)
     * @param filename the original filename (used for content type detection)
     * @param type the document type categorization (e.g., INVOICE_PDF, SIGNED_XML)
     * @param invoiceId the associated invoice ID (may be null)
     * @return the stored document with generated ID, checksum, and storage location
     * @throws com.wpanther.storage.domain.exception.InvalidDocumentException if content is null or empty
     * @throws com.wpanther.storage.domain.exception.StorageFailedException if storage operation fails
     */
    StoredDocument storeDocument(byte[] content, String filename,
                                 DocumentType type, String invoiceId);

    /**
     * Store a document with explicit content type and invoice number.
     * <p>
     * This overload allows specifying the content type explicitly (overriding auto-detection)
     * and includes the invoice number for additional metadata.
     * </p>
     *
     * @param content the document content bytes (must not be null or empty)
     * @param filename the original filename
     * @param contentType the MIME content type (if null, auto-detected from filename)
     * @param type the document type categorization
     * @param invoiceId the associated invoice ID (may be null)
     * @param invoiceNumber the human-readable invoice number (may be null)
     * @return the stored document with generated ID, checksum, and storage location
     * @throws com.wpanther.storage.domain.exception.InvalidDocumentException if content is null or empty
     * @throws com.wpanther.storage.domain.exception.StorageFailedException if storage operation fails
     */
    StoredDocument storeDocument(byte[] content, String filename, String contentType,
                                 DocumentType type, String invoiceId, String invoiceNumber);

    /**
     * Retrieve document metadata by ID.
     * <p>
     * Returns the document metadata including filename, content type, storage location,
     * checksum, and creation timestamp. Does not include the document content.
     * </p>
     *
     * @param documentId the unique document identifier
     * @return the document metadata, or empty if not found
     */
    Optional<StoredDocument> getDocument(String documentId);

    /**
     * Retrieve all documents associated with an invoice ID.
     * <p>
     * Useful for fetching all related documents (PDF, XML, etc.) for a specific invoice.
     * </p>
     *
     * @param invoiceId the invoice ID to query
     * @return list of documents associated with the invoice (empty if none found)
     */
    List<StoredDocument> getDocumentsByInvoice(String invoiceId);

    /**
     * Delete a document by ID.
     * <p>
     * This removes both the metadata from MongoDB and the physical file from storage.
     * If the file cannot be found in storage, the operation still succeeds (idempotent).
     * </p>
     *
     * @param documentId the unique document identifier
     * @throws com.wpanther.storage.domain.exception.DocumentNotFoundException if document metadata not found
     * @throws com.wpanther.storage.domain.exception.StorageFailedException if file deletion fails
     */
    void deleteDocument(String documentId);

    /**
     * Check if a document exists for the given invoice ID and document type.
     * <p>
     * Used for idempotency checks in saga operations to prevent duplicate storage.
     * </p>
     *
     * @param invoiceId the invoice ID to check
     * @param type the document type to check
     * @return true if a document exists, false otherwise
     */
    boolean existsByInvoiceAndType(String invoiceId, DocumentType type);

    /**
     * Retrieve document content by ID as a stream.
     * <p>
     * Returns a streaming response for efficient large file downloads without
     * loading the entire content into memory. Callers are responsible for
     * closing the stream.
     * </p>
     *
     * @param documentId the unique document identifier
     * @return an input stream to read the document content
     * @throws com.wpanther.storage.domain.exception.DocumentNotFoundException if document not found
     * @throws com.wpanther.storage.domain.exception.StorageFailedException if content retrieval fails
     */
    InputStream getDocumentContentStream(String documentId);

    /**
     * Retrieve document content by ID.
     * <p>
     * Downloads the full document content from storage. For large files, prefer
     * {@link #getDocumentContentStream(String)} to avoid memory issues.
     * </p>
     *
     * @param documentId the unique document identifier
     * @return the document content bytes
     * @throws com.wpanther.storage.domain.exception.DocumentNotFoundException if document not found
     * @throws com.wpanther.storage.domain.exception.StorageFailedException if content retrieval fails
     * @deprecated Use {@link #getDocumentContentStream(String)} for large files
     */
    @Deprecated
    byte[] getDocumentContent(String documentId);
}
