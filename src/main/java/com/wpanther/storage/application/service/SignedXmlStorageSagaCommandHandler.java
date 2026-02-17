package com.wpanther.storage.application.service;

import com.wpanther.storage.domain.event.CompensateSignedXmlStorageCommand;
import com.wpanther.storage.domain.event.ProcessSignedXmlStorageCommand;
import com.wpanther.storage.domain.model.DocumentType;
import com.wpanther.storage.domain.model.StoredDocument;
import com.wpanther.storage.infrastructure.messaging.SignedXmlStorageSagaReplyPublisher;
import com.wpanther.storage.infrastructure.persistence.StoredDocumentEntity;
import com.wpanther.storage.infrastructure.persistence.MongoDocumentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Handles saga commands from the orchestrator for signed XML storage.
 * Delegates business logic to DocumentStorageService,
 * then publishes replies via outbox pattern.
 *
 * The @Transactional annotation governs the PostgreSQL transaction (outbox writes).
 * MongoDB writes happen inside the transaction body but are not part of the
 * PostgreSQL transaction. Idempotency check prevents duplicates on retry.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SignedXmlStorageSagaCommandHandler {

    private final DocumentStorageService storageService;
    private final MongoDocumentRepository documentRepository;
    private final SignedXmlStorageSagaReplyPublisher sagaReplyPublisher;

    @Transactional
    public void handleProcessCommand(ProcessSignedXmlStorageCommand command) {
        log.info("Handling ProcessSignedXmlStorageCommand for saga {} document {}",
                command.getSagaId(), command.getDocumentId());

        try {
            // Idempotency: check if XML document already stored for this invoiceId
            List<StoredDocumentEntity> existing = documentRepository.findByInvoiceId(command.getDocumentId());
            boolean alreadyStored = existing.stream()
                    .anyMatch(doc -> doc.getDocumentType() == DocumentType.SIGNED_XML);

            if (alreadyStored) {
                log.warn("Signed XML for invoiceId {} already stored, sending SUCCESS reply", command.getDocumentId());
                sagaReplyPublisher.publishSuccess(
                        command.getSagaId(), command.getSagaStep(), command.getCorrelationId());
                return;
            }

            // Generate filename from invoice number and document type
            String fileName = generateFileName(command.getInvoiceNumber(), command.getDocumentType());

            // Store signed XML document (saves file + MongoDB metadata)
            StoredDocument document = storageService.storeDocument(
                    command.getSignedXmlContent().getBytes(),
                    fileName,
                    "application/xml",
                    DocumentType.SIGNED_XML,
                    command.getDocumentId(),
                    command.getInvoiceNumber()
            );

            log.info("Stored signed XML document: documentId={}, invoiceId={}", document.getId(), command.getDocumentId());

            // Publish saga SUCCESS reply via outbox
            sagaReplyPublisher.publishSuccess(
                    command.getSagaId(), command.getSagaStep(), command.getCorrelationId());

            log.info("Successfully processed signed XML storage for saga {} document {}",
                    command.getSagaId(), command.getDocumentId());

        } catch (Exception e) {
            log.error("Failed to process signed XML storage for saga {} document {}: {}",
                    command.getSagaId(), command.getDocumentId(), e.getMessage(), e);
            sagaReplyPublisher.publishFailure(
                    command.getSagaId(), command.getSagaStep(), command.getCorrelationId(), e.getMessage());
        }
    }

    @Transactional
    public void handleCompensation(CompensateSignedXmlStorageCommand command) {
        log.info("Handling compensation for signed XML storage for saga {} document {}",
                command.getSagaId(), command.getDocumentId());

        try {
            // Find and delete stored signed XML documents for this invoiceId
            List<StoredDocumentEntity> documents = documentRepository.findByInvoiceId(command.getDocumentId());

            int deletedCount = 0;
            for (StoredDocumentEntity doc : documents) {
                if (doc.getDocumentType() == DocumentType.SIGNED_XML) {
                    storageService.deleteDocument(doc.getId());
                    deletedCount++;
                }
            }

            if (deletedCount > 0) {
                log.info("Deleted {} signed XML document(s) for compensation of documentId {}",
                        deletedCount, command.getDocumentId());
            } else {
                log.info("No signed XML documents found for documentId {} - already compensated or never stored",
                        command.getDocumentId());
            }

            sagaReplyPublisher.publishCompensated(
                    command.getSagaId(), command.getSagaStep(), command.getCorrelationId());

        } catch (Exception e) {
            log.error("Failed to compensate signed XML storage for saga {} document {}: {}",
                    command.getSagaId(), command.getDocumentId(), e.getMessage(), e);
            sagaReplyPublisher.publishFailure(
                    command.getSagaId(), command.getSagaStep(), command.getCorrelationId(),
                    "Compensation failed: " + e.getMessage());
        }
    }

    /**
     * Generate filename for signed XML document.
     */
    private String generateFileName(String invoiceNumber, String documentType) {
        String typePrefix = getTypePrefix(documentType);
        return String.format("%s_%s_signed.xml", typePrefix, invoiceNumber);
    }

    /**
     * Get filename prefix based on document type.
     */
    private String getTypePrefix(String documentType) {
        return switch (documentType.toUpperCase()) {
            case "INVOICE" -> "INV";
            case "TAX_INVOICE" -> "TAX";
            case "RECEIPT" -> "RCT";
            case "DEBIT_CREDIT_NOTE" -> "DCN";
            case "CANCELLATION_NOTE" -> "CN";
            case "ABBREVIATED_TAX_INVOICE" -> "ABT";
            default -> "DOC";
        };
    }
}
