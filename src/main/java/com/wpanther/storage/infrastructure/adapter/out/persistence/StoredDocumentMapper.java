package com.wpanther.storage.infrastructure.adapter.out.persistence;

import com.wpanther.storage.domain.model.StoredDocument;
import org.springframework.stereotype.Component;

/**
 * Mapper between domain {@link StoredDocument} and persistence {@link StoredDocumentEntity}.
 */
@Component
public class StoredDocumentMapper {

    /**
     * Convert domain model to persistence entity.
     */
    public StoredDocumentEntity toEntity(StoredDocument domain) {
        return StoredDocumentEntity.builder()
                .id(domain.getId())
                .fileName(domain.getFileName())
                .contentType(domain.getContentType())
                .storagePath(domain.getStoragePath())
                .storageUrl(domain.getStorageUrl())
                .fileSize(domain.getFileSize())
                .checksum(domain.getChecksum())
                .documentType(domain.getDocumentType())
                .createdAt(domain.getCreatedAt())
                .expiresAt(domain.getExpiresAt())
                .invoiceId(domain.getInvoiceId())
                .invoiceNumber(domain.getInvoiceNumber())
                .build();
    }

    /**
     * Convert persistence entity to domain model.
     */
    public StoredDocument toDomain(StoredDocumentEntity entity) {
        return StoredDocument.builder()
                .id(entity.getId())
                .fileName(entity.getFileName())
                .contentType(entity.getContentType())
                .storagePath(entity.getStoragePath())
                .storageUrl(entity.getStorageUrl())
                .fileSize(entity.getFileSize())
                .checksum(entity.getChecksum())
                .documentType(entity.getDocumentType())
                .createdAt(entity.getCreatedAt())
                .expiresAt(entity.getExpiresAt())
                .invoiceId(entity.getInvoiceId())
                .invoiceNumber(entity.getInvoiceNumber())
                .build();
    }
}
