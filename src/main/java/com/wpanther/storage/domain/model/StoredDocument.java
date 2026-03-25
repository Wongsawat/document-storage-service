package com.wpanther.storage.domain.model;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Aggregate Root representing a stored document
 *
 * This aggregate encapsulates document storage including metadata,
 * checksum verification, and lifecycle management.
 */
public class StoredDocument {

    // Identity
    private final String id;

    // File Information
    private final String fileName;
    private final String contentType;

    // Storage Information
    private final String storagePath;
    private final String storageUrl;
    private final Long fileSize;
    private final String checksum;

    // Document Classification
    private final DocumentType documentType;

    // Timestamps
    private final LocalDateTime createdAt;
    private LocalDateTime expiresAt;

    // Metadata
    private String invoiceId;
    private String invoiceNumber;

    private StoredDocument(Builder builder) {
        this.id = Objects.requireNonNull(builder.id, "Document ID is required");
        this.fileName = Objects.requireNonNull(builder.fileName, "File name is required");
        this.contentType = Objects.requireNonNull(builder.contentType, "Content type is required");
        this.storagePath = Objects.requireNonNull(builder.storagePath, "Storage path is required");
        this.storageUrl = Objects.requireNonNull(builder.storageUrl, "Storage URL is required");
        this.fileSize = builder.fileSize;
        this.checksum = Objects.requireNonNull(builder.checksum, "Checksum is required");
        this.documentType = builder.documentType != null ? builder.documentType : DocumentType.OTHER;
        this.createdAt = builder.createdAt != null ? builder.createdAt : LocalDateTime.now();
        this.expiresAt = builder.expiresAt;
        this.invoiceId = builder.invoiceId;
        this.invoiceNumber = builder.invoiceNumber;

        validateInvariant();
    }

    /**
     * Validate business invariants
     */
    private void validateInvariant() {
        if (fileName.isBlank()) {
            throw new IllegalStateException("File name cannot be blank");
        }

        if (fileSize == null || fileSize <= 0) {
            throw new IllegalArgumentException("File size must be positive");
        }

        if (checksum.isBlank()) {
            throw new IllegalStateException("Checksum cannot be blank");
        }
    }

    /**
     * Set expiration date
     */
    public void setExpiresAt(LocalDateTime expiresAt) {
        this.expiresAt = expiresAt;
    }

    /**
     * Check if document is expired
     */
    public boolean isExpired() {
        return expiresAt != null && LocalDateTime.now().isAfter(expiresAt);
    }

    /**
     * Verify checksum matches expected value
     */
    public boolean verifyChecksum(String expectedChecksum) {
        return this.checksum.equals(expectedChecksum);
    }

    // Getters
    public String getId() {
        return id;
    }

    public String getFileName() {
        return fileName;
    }

    public String getContentType() {
        return contentType;
    }

    public String getStoragePath() {
        return storagePath;
    }

    public String getStorageUrl() {
        return storageUrl;
    }

    public Long getFileSize() {
        return fileSize;
    }

    public String getChecksum() {
        return checksum;
    }

    public DocumentType getDocumentType() {
        return documentType;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getExpiresAt() {
        return expiresAt;
    }

    public String getInvoiceId() {
        return invoiceId;
    }

    public String getInvoiceNumber() {
        return invoiceNumber;
    }

    /**
     * Builder for StoredDocument
     */
    public static class Builder {
        private String id;
        private String fileName;
        private String contentType;
        private String storagePath;
        private String storageUrl;
        private Long fileSize;
        private String checksum;
        private DocumentType documentType;
        private LocalDateTime createdAt;
        private LocalDateTime expiresAt;
        private String invoiceId;
        private String invoiceNumber;

        public Builder id(String id) {
            this.id = id;
            return this;
        }

        public Builder fileName(String fileName) {
            this.fileName = fileName;
            return this;
        }

        public Builder contentType(String contentType) {
            this.contentType = contentType;
            return this;
        }

        public Builder storagePath(String storagePath) {
            this.storagePath = storagePath;
            return this;
        }

        public Builder storageUrl(String storageUrl) {
            this.storageUrl = storageUrl;
            return this;
        }

        public Builder fileSize(Long fileSize) {
            this.fileSize = fileSize;
            return this;
        }

        public Builder checksum(String checksum) {
            this.checksum = checksum;
            return this;
        }

        public Builder documentType(DocumentType documentType) {
            this.documentType = documentType;
            return this;
        }

        public Builder createdAt(LocalDateTime createdAt) {
            this.createdAt = createdAt;
            return this;
        }

        public Builder expiresAt(LocalDateTime expiresAt) {
            this.expiresAt = expiresAt;
            return this;
        }

        public Builder invoiceId(String invoiceId) {
            this.invoiceId = invoiceId;
            return this;
        }

        public Builder invoiceNumber(String invoiceNumber) {
            this.invoiceNumber = invoiceNumber;
            return this;
        }

        public StoredDocument build() {
            return new StoredDocument(this);
        }
    }

    public static Builder builder() {
        return new Builder();
    }
}
