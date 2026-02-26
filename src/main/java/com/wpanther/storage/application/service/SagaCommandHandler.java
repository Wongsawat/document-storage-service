package com.wpanther.storage.application.service;

import com.wpanther.storage.domain.event.CompensateDocumentStorageCommand;
import com.wpanther.storage.domain.event.DocumentStoredEvent;
import com.wpanther.storage.domain.event.ProcessDocumentStorageCommand;
import com.wpanther.storage.domain.model.DocumentType;
import com.wpanther.storage.domain.model.StoredDocument;
import com.wpanther.storage.infrastructure.messaging.EventPublisher;
import com.wpanther.storage.infrastructure.messaging.SagaReplyPublisher;
import com.wpanther.storage.infrastructure.persistence.StoredDocumentEntity;
import com.wpanther.storage.infrastructure.persistence.MongoDocumentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Handles saga commands from the orchestrator.
 * Delegates business logic to DocumentStorageService and PdfDownloadService,
 * then publishes replies via outbox pattern.
 * <p>
 * <b>Transaction Boundary:</b>
 * <pre>
 * ┌─────────────────────────────────────────────────────────────────┐
 * │ @Transactional (PostgreSQL transaction only)                    │
 * │ ┌─────────────────────────────────────────────────────────────┐│
 * │ │ 1. Idempotency check (MongoDB - NOT in PostgreSQL tx)     ││
 * │ │ 2. Download PDF from URL (HTTP call)                      ││
 * │ │ 3. Store document (MongoDB + filesystem - NOT in tx)      ││
 * │ │ 4. Publish DocumentStoredEvent (PostgreSQL outbox - IN tx) ││
 * │ │ 5. Publish saga reply (PostgreSQL outbox - IN tx)        ││
 * │ └─────────────────────────────────────────────────────────────┘│
 * │                          │                                     │
 * │                    COMMIT (PostgreSQL only)                   │
 * │                          │                                     │
 * │              ┌─────────────────────────────────┐              │
 * │              │ Debezium CDC publishes events   │              │
 * │              │ to Kafka from outbox table      │              │
 * │              └─────────────────────────────────┘              │
 * └─────────────────────────────────────────────────────────────────┘
 * </pre>
 * <p>
 * <b>Important Notes:</b>
 * <ul>
 *   <li>MongoDB writes are NOT part of the PostgreSQL transaction</li>
 *   <li>If PostgreSQL commit fails, MongoDB documents will persist (orphaned data)</li>
 *   <li>If MongoDB write fails, PostgreSQL outbox events will rollback</li>
 *   <li>Idempotency check prevents duplicate processing on retry</li>
 *   <li>Compensation handler cleans up orphaned MongoDB documents</li>
 *   <li>For stronger consistency, consider MongoDB multi-document transactions</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SagaCommandHandler {

    private final DocumentStorageService storageService;
    private final PdfDownloadService downloadService;
    private final MongoDocumentRepository documentRepository;
    private final SagaReplyPublisher sagaReplyPublisher;
    private final EventPublisher eventPublisher;

    @Transactional
    public void handleProcessCommand(ProcessDocumentStorageCommand command) {
        log.info("Handling ProcessDocumentStorageCommand for saga {} document {}",
                command.getSagaId(), command.getDocumentId());

        try {
            // Idempotency: check if document already stored for this invoiceId
            List<StoredDocumentEntity> existing = documentRepository.findByInvoiceId(command.getDocumentId());
            if (!existing.isEmpty()) {
                log.warn("Document for invoiceId {} already stored, sending SUCCESS reply", command.getDocumentId());
                sagaReplyPublisher.publishSuccess(
                        command.getSagaId(), command.getSagaStep(), command.getCorrelationId());
                return;
            }

            // Download PDF from URL
            byte[] pdfContent = downloadService.downloadPdf(command.getSignedPdfUrl());
            String fileName = downloadService.extractFileName(
                    command.getSignedPdfUrl(), command.getInvoiceNumber());
            DocumentType documentType = downloadService.mapDocumentType(command.getDocumentType());

            // Store document (saves file + MongoDB metadata)
            StoredDocument document = storageService.storeDocument(
                    pdfContent,
                    fileName,
                    "application/pdf",
                    documentType,
                    command.getDocumentId(),
                    command.getInvoiceNumber()
            );

            log.info("Stored document: documentId={}, invoiceId={}", document.getId(), command.getDocumentId());

            // Publish downstream notification event via outbox
            DocumentStoredEvent storedEvent = new DocumentStoredEvent(
                    document.getId(),
                    command.getDocumentId(),
                    command.getInvoiceNumber(),
                    document.getFileName(),
                    document.getStorageUrl(),
                    document.getFileSize(),
                    document.getChecksum(),
                    document.getDocumentType().name(),
                    command.getCorrelationId()
            );
            eventPublisher.publishDocumentStored(storedEvent);

            // Publish saga SUCCESS reply via outbox
            sagaReplyPublisher.publishSuccess(
                    command.getSagaId(), command.getSagaStep(), command.getCorrelationId());

            log.info("Successfully processed document storage for saga {} document {}",
                    command.getSagaId(), command.getDocumentId());

        } catch (Exception e) {
            log.error("Failed to process document storage for saga {} document {}: {}",
                    command.getSagaId(), command.getDocumentId(), e.getMessage(), e);
            sagaReplyPublisher.publishFailure(
                    command.getSagaId(), command.getSagaStep(), command.getCorrelationId(), e.getMessage());
        }
    }

    @Transactional
    public void handleCompensation(CompensateDocumentStorageCommand command) {
        log.info("Handling compensation for saga {} document {}",
                command.getSagaId(), command.getDocumentId());

        try {
            // Find and delete stored documents for this invoiceId
            List<StoredDocumentEntity> documents = documentRepository.findByInvoiceId(command.getDocumentId());
            if (!documents.isEmpty()) {
                for (StoredDocumentEntity doc : documents) {
                    storageService.deleteDocument(doc.getId());
                }
                log.info("Deleted {} document(s) for compensation of documentId {}",
                        documents.size(), command.getDocumentId());
            } else {
                log.info("No stored documents found for documentId {} - already compensated or never stored",
                        command.getDocumentId());
            }

            sagaReplyPublisher.publishCompensated(
                    command.getSagaId(), command.getSagaStep(), command.getCorrelationId());

        } catch (Exception e) {
            log.error("Failed to compensate document storage for saga {} document {}: {}",
                    command.getSagaId(), command.getDocumentId(), e.getMessage(), e);
            sagaReplyPublisher.publishFailure(
                    command.getSagaId(), command.getSagaStep(), command.getCorrelationId(),
                    "Compensation failed: " + e.getMessage());
        }
    }
}
