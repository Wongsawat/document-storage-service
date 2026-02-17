package com.wpanther.storage.application.service;

import com.wpanther.storage.domain.event.XmlStorageRequestedEvent;
import com.wpanther.storage.domain.event.XmlStoredEvent;
import com.wpanther.storage.domain.model.DocumentType;
import com.wpanther.storage.domain.model.StoredDocument;
import com.wpanther.storage.infrastructure.messaging.XmlStoredEventPublisher;
import com.wpanther.storage.infrastructure.persistence.MongoDocumentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Handles XML storage requests from xml-signing-service.
 * Stores signed XML documents in MinIO/S3 and publishes XmlStoredEvent.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class StorageRequestHandler {

    private final MongoDocumentRepository documentRepository;
    private final DocumentStorageService documentStorageService;
    private final XmlStoredEventPublisher eventPublisher;

    /**
     * Handle an XML storage request event.
     * Stores the signed XML and publishes a confirmation event.
     */
    @Transactional
    public void handleStorageRequest(XmlStorageRequestedEvent event) {
        log.info("Handling XML storage request for invoice: {}", event.getInvoiceId());

        // Check idempotency - skip if already stored
        List<com.wpanther.storage.infrastructure.persistence.StoredDocumentEntity> existing =
                documentRepository.findByInvoiceId(event.getInvoiceId());
        if (!existing.isEmpty()) {
            // Check if SIGNED_XML type exists
            boolean alreadyStored = existing.stream()
                    .anyMatch(doc -> doc.getDocumentType() == DocumentType.SIGNED_XML);
            if (alreadyStored) {
                log.info("Signed XML already stored for invoice: {}, skipping", event.getInvoiceId());
                return;
            }
        }

        try {
            // Generate file name
            String fileName = generateFileName(event);

            // Store the document using existing DocumentStorageService
            StoredDocument storedDocument = documentStorageService.storeDocument(
                    event.getXmlContent().getBytes(),
                    fileName,
                    "application/xml",
                    DocumentType.SIGNED_XML,
                    event.getInvoiceId(),
                    event.getInvoiceNumber()
            );

            // Publish XmlStoredEvent
            eventPublisher.publishXmlStored(new XmlStoredEvent(
                    event.getInvoiceId(),
                    storedDocument.getStorageUrl(),
                    storedDocument.getStoragePath(),
                    event.getDocumentType(),
                    event.getInvoiceNumber(),
                    event.getCorrelationId()
            ));

            log.info("Successfully stored signed XML for invoice: {} at: {}",
                    event.getInvoiceId(), storedDocument.getStoragePath());

        } catch (Exception e) {
            log.error("Failed to handle XML storage request for invoice: {}",
                    event.getInvoiceId(), e);
            throw e; // Re-throw to trigger Camel retry/DLQ
        }
    }

    /**
     * Generate file name for stored XML document.
     */
    private String generateFileName(XmlStorageRequestedEvent event) {
        String baseName = event.getInvoiceNumber() != null && !event.getInvoiceNumber().isBlank()
                ? event.getInvoiceNumber()
                : event.getInvoiceId();
        return baseName + "_signed.xml";
    }
}
