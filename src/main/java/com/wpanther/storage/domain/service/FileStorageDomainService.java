package com.wpanther.storage.domain.service;

import com.wpanther.storage.domain.model.*;
import com.wpanther.storage.domain.port.outbound.*;
import com.wpanther.storage.domain.exception.*;
import com.wpanther.storage.domain.port.inbound.DocumentStorageUseCase;
import com.wpanther.storage.domain.util.ContentTypeUtil;
import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class FileStorageDomainService implements DocumentStorageUseCase {

    private final StorageProviderPort storageProvider;
    private final DocumentRepositoryPort documentRepository;

    public FileStorageDomainService(StorageProviderPort storageProvider,
                                     DocumentRepositoryPort documentRepository) {
        this.storageProvider = storageProvider;
        this.documentRepository = documentRepository;
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
            .fileSize(content.length)
            .checksum(checksum)
            .createdAt(LocalDateTime.now())
            .build();

        return documentRepository.save(document);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<StoredDocument> getDocument(String documentId) {
        return documentRepository.findById(documentId);
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
    public byte[] getDocumentContent(String documentId) {
        StoredDocument doc = documentRepository.findById(documentId)
            .orElseThrow(() -> new DocumentNotFoundException("Document not found: " + documentId));

        try {
            InputStream inputStream = storageProvider.retrieve(doc.getStoragePath());
            return inputStream.readAllBytes();
        } catch (Exception e) {
            throw new StorageFailedException("Failed to retrieve document content: " + documentId, e);
        }
    }
}
