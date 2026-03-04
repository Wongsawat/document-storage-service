package com.wpanther.storage.domain.port.outbound;

import com.wpanther.storage.domain.model.StoredDocument;
import com.wpanther.storage.domain.model.DocumentType;
import java.util.List;
import java.util.Optional;

/**
 * Outbound port for document metadata persistence.
 * Implemented by MongoDocumentAdapter.
 */
public interface DocumentRepositoryPort {
    StoredDocument save(StoredDocument document);
    Optional<StoredDocument> findById(String id);
    List<StoredDocument> findByInvoiceId(String invoiceId);
    Optional<StoredDocument> findByInvoiceIdAndDocumentType(String invoiceId, DocumentType type);
    void deleteById(String id);
    boolean existsByInvoiceIdAndDocumentType(String invoiceId, DocumentType type);
}
