package com.wpanther.storage.application.usecase;

import com.wpanther.storage.domain.model.*;
import com.wpanther.storage.domain.repository.DocumentRepositoryPort;
import com.wpanther.storage.application.port.out.StorageProviderPort;
import com.wpanther.storage.domain.exception.*;
import com.wpanther.storage.domain.util.ContentTypeUtil;
import com.wpanther.storage.infrastructure.config.metrics.DocumentStorageMetricsService;
import io.micrometer.core.instrument.Timer;
import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Domain service for document storage operations with metrics.
 */
@Service
public class FileStorageDomainService implements DocumentStorageUseCase {

    private final StorageProviderPort storageProvider;
    private final DocumentRepositoryPort documentRepository;
    private final DocumentStorageMetricsService metrics;

    public FileStorageDomainService(StorageProviderPort storageProvider,
                                     DocumentRepositoryPort documentRepository,
                                     DocumentStorageMetricsService metrics) {
        this.storageProvider = storageProvider;
        this.documentRepository = documentRepository;
        this.metrics = metrics;
    }

    @Override
    @Transactional
    public StoredDocument storeDocument(byte[] content, String filename,
                                        DocumentType type, String invoiceId) {
        return storeDocument(content, filename, ContentTypeUtil.determineContentType(filename), type, invoiceId, null);
    }

    @Override
    @Transactional
    public StoredDocument storeDocument(byte[] content, String filename, String contentType,
                                        DocumentType type, String invoiceId, String invoiceNumber) {
        if (content == null || content.length == 0) {
            throw new InvalidDocumentException("Document content cannot be empty");
        }

        Timer.Sample timer = metrics.timeStorageOperation();

        try {
            String documentId = UUID.randomUUID().toString();
            InputStream inputStream = new ByteArrayInputStream(content);
            StorageResult result = storageProvider.store(documentId, inputStream, filename, content.length);
            String checksum = DigestUtils.sha256Hex(content);

            StoredDocument document = StoredDocument.builder()
                .id(documentId)
                .invoiceId(invoiceId)
                .invoiceNumber(invoiceNumber)
                .documentType(type)
                .fileName(filename)
                .contentType(contentType != null ? contentType : ContentTypeUtil.determineContentType(filename))
                .storagePath(result.location())
                .storageUrl(result.location())
                .fileSize((long) content.length)
                .checksum(checksum)
                .createdAt(LocalDateTime.now())
                .build();

            StoredDocument savedDocument = documentRepository.save(document);

            // Record metrics
            metrics.recordDocumentStored(type);
            metrics.stopStorageOperation(timer);

            return savedDocument;
        } catch (Exception e) {
            metrics.stopStorageOperation(timer);
            throw e;
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<StoredDocument> getDocument(String documentId) {
        Optional<StoredDocument> doc = documentRepository.findById(documentId);
        if (doc.isPresent()) {
            metrics.recordDocumentRetrieved();
        }
        return doc;
    }

    @Override
    @Transactional(readOnly = true)
    public List<StoredDocument> getDocumentsByInvoice(String invoiceId) {
        return documentRepository.findByInvoiceId(invoiceId);
    }

    @Override
    @Transactional
    public void deleteDocument(String documentId) {
        StoredDocument doc = documentRepository.findById(documentId)
            .orElseThrow(() -> new DocumentNotFoundException("Document not found: " + documentId));
        storageProvider.delete(doc.getStoragePath());
        documentRepository.deleteById(documentId);
        metrics.recordDocumentDeleted();
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existsByInvoiceAndType(String invoiceId, DocumentType type) {
        return documentRepository.existsByInvoiceIdAndDocumentType(invoiceId, type);
    }

    /**
     * Download document content from storage
     */
    public InputStream downloadContent(String storagePath) {
        return storageProvider.retrieve(storagePath);
    }

    @Override
    @Transactional(readOnly = true)
    public InputStream getDocumentContentStream(String documentId) {
        StoredDocument doc = documentRepository.findById(documentId)
            .orElseThrow(() -> new DocumentNotFoundException("Document not found: " + documentId));

        metrics.recordDocumentRetrieved();
        return storageProvider.retrieve(doc.getStoragePath());
    }

    @Override
    @Transactional(readOnly = true)
    @Deprecated
    public byte[] getDocumentContent(String documentId) {
        try (InputStream inputStream = getDocumentContentStream(documentId)) {
            return inputStream.readAllBytes();
        } catch (DocumentNotFoundException e) {
            throw e; // Let domain exceptions propagate
        } catch (Exception e) {
            throw new StorageFailedException("Failed to retrieve document content: " + documentId, e);
        }
    }
}
