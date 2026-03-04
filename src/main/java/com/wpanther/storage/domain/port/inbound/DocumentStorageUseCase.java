package com.wpanther.storage.domain.port.inbound;

import com.wpanther.storage.domain.model.StoredDocument;
import com.wpanther.storage.domain.model.DocumentType;
import java.util.List;
import java.util.Optional;

/**
 * Inbound port for document storage operations.
 * Implemented by FileStorageDomainService.
 * Called by DocumentStorageController (REST adapter).
 */
public interface DocumentStorageUseCase {
    /**
     * Store a document with the given content and metadata.
     */
    StoredDocument storeDocument(byte[] content, String filename,
                                 DocumentType type, String invoiceId);

    /**
     * Store a document with additional metadata including invoice number.
     */
    StoredDocument storeDocument(byte[] content, String filename, String contentType,
                                 DocumentType type, String invoiceId, String invoiceNumber);

    /**
     * Get document metadata by ID.
     */
    Optional<StoredDocument> getDocument(String documentId);

    /**
     * Get documents by invoice ID.
     */
    List<StoredDocument> getDocumentsByInvoice(String invoiceId);

    /**
     * Delete a document by ID.
     */
    void deleteDocument(String documentId);

    /**
     * Check if document exists by invoice ID and type.
     */
    boolean existsByInvoiceAndType(String invoiceId, DocumentType type);

    /**
     * Download document content by ID.
     */
    byte[] getDocumentContent(String documentId);
}
