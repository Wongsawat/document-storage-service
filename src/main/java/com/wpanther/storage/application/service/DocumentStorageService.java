package com.wpanther.storage.application.service;

import com.wpanther.storage.domain.model.DocumentType;
import com.wpanther.storage.domain.model.StoredDocument;
import com.wpanther.storage.domain.service.FileStorageProvider;
import com.wpanther.storage.infrastructure.persistence.MongoDocumentRepository;
import com.wpanther.storage.infrastructure.persistence.StoredDocumentEntity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Application service for document storage operations
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DocumentStorageService {

    private final MongoDocumentRepository repository;
    private final FileStorageProvider storageProvider;

    /**
     * Store document
     */
    public StoredDocument storeDocument(byte[] content, String fileName, String contentType,
                                       DocumentType documentType, String invoiceId, String invoiceNumber) {
        try {
            log.info("Storing document: {} (type: {}, size: {} bytes)", fileName, documentType, content.length);

            // Calculate checksum
            String checksum = calculateChecksum(content);

            // Store file using provider
            FileStorageProvider.StorageResult result = storageProvider.store(content, fileName);

            // Create domain model
            StoredDocument document = StoredDocument.builder()
                .id(java.util.UUID.randomUUID().toString())
                .fileName(fileName)
                .contentType(contentType)
                .storagePath(result.path())
                .storageUrl(result.url())
                .fileSize(content.length)
                .checksum(checksum)
                .documentType(documentType)
                .invoiceId(invoiceId)
                .invoiceNumber(invoiceNumber)
                .build();

            // Save metadata to MongoDB
            saveDomain(document);

            log.info("Successfully stored document: {} with ID: {}", fileName, document.getId());

            return document;

        } catch (Exception e) {
            log.error("Failed to store document: {}", fileName, e);
            throw new RuntimeException("Failed to store document", e);
        }
    }

    /**
     * Retrieve document by ID
     */
    public StoredDocument getDocument(String id) {
        StoredDocumentEntity entity = repository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Document not found: " + id));

        return toDomain(entity);
    }

    /**
     * Retrieve document content
     */
    public byte[] getDocumentContent(String id) {
        try {
            StoredDocument document = getDocument(id);
            byte[] content = storageProvider.retrieve(document.getStoragePath());

            // Verify checksum
            String actualChecksum = calculateChecksum(content);
            if (!document.verifyChecksum(actualChecksum)) {
                log.warn("Checksum mismatch for document: {}", id);
                throw new RuntimeException("Document integrity check failed");
            }

            return content;

        } catch (FileStorageProvider.StorageException e) {
            log.error("Failed to retrieve document content: {}", id, e);
            throw new RuntimeException("Failed to retrieve document", e);
        }
    }

    /**
     * Delete document
     */
    public void deleteDocument(String id) {
        try {
            StoredDocument document = getDocument(id);

            // Delete from storage
            storageProvider.delete(document.getStoragePath());

            // Delete from MongoDB
            repository.deleteById(id);

            log.info("Deleted document: {}", id);

        } catch (FileStorageProvider.StorageException e) {
            log.error("Failed to delete document: {}", id, e);
            throw new RuntimeException("Failed to delete document", e);
        }
    }

    /**
     * Find documents by invoice ID
     */
    public List<StoredDocument> findByInvoiceId(String invoiceId) {
        return repository.findByInvoiceId(invoiceId).stream()
            .map(this::toDomain)
            .collect(Collectors.toList());
    }

    /**
     * Calculate SHA-256 checksum
     */
    private String calculateChecksum(byte[] content) {
        return DigestUtils.sha256Hex(content);
    }

    /**
     * Save domain model to MongoDB
     */
    private void saveDomain(StoredDocument document) {
        StoredDocumentEntity entity = StoredDocumentEntity.builder()
            .id(document.getId())
            .fileName(document.getFileName())
            .contentType(document.getContentType())
            .storagePath(document.getStoragePath())
            .storageUrl(document.getStorageUrl())
            .fileSize(document.getFileSize())
            .checksum(document.getChecksum())
            .documentType(document.getDocumentType())
            .createdAt(document.getCreatedAt())
            .expiresAt(document.getExpiresAt())
            .invoiceId(document.getInvoiceId())
            .invoiceNumber(document.getInvoiceNumber())
            .build();

        repository.save(entity);
    }

    /**
     * Convert entity to domain model
     */
    private StoredDocument toDomain(StoredDocumentEntity entity) {
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
