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
    StoredDocument storeDocument(byte[] content, String filename,
                                 DocumentType type, String invoiceId);
    Optional<StoredDocument> getDocument(String documentId);
    List<StoredDocument> getDocumentsByInvoice(String invoiceId);
    void deleteDocument(String documentId);
    boolean existsByInvoiceAndType(String invoiceId, DocumentType type);
}
