package com.invoice.storage.infrastructure.persistence;

import com.invoice.storage.domain.model.DocumentType;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface MongoDocumentRepository extends MongoRepository<StoredDocumentEntity, String> {

    Optional<StoredDocumentEntity> findByFileName(String fileName);

    List<StoredDocumentEntity> findByDocumentType(DocumentType documentType);

    List<StoredDocumentEntity> findByInvoiceId(String invoiceId);

    List<StoredDocumentEntity> findByInvoiceNumber(String invoiceNumber);

    List<StoredDocumentEntity> findByExpiresAtBefore(LocalDateTime dateTime);

    long countByDocumentType(DocumentType documentType);
}
