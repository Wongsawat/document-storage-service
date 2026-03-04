package com.wpanther.storage.domain.service;

import com.wpanther.storage.domain.event.*;
import com.wpanther.storage.domain.exception.StorageFailedException;
import com.wpanther.storage.domain.exception.InvalidDocumentException;
import com.wpanther.storage.domain.model.DocumentType;
import com.wpanther.storage.domain.model.StoredDocument;
import com.wpanther.storage.domain.port.outbound.MessagePublisherPort;
import com.wpanther.storage.domain.port.inbound.SagaCommandUseCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Domain service for saga command orchestration.
 * Implements SagaCommandUseCase port.
 * Handles document storage commands from the orchestrator.
 */
@Service
public class SagaOrchestrationService implements SagaCommandUseCase {

    private static final Logger log = LoggerFactory.getLogger(SagaOrchestrationService.class);

    private final FileStorageDomainService storageService;
    private final PdfDownloadDomainService pdfDownloadService;
    private final MessagePublisherPort messagePublisher;

    public SagaOrchestrationService(FileStorageDomainService storageService,
                                     PdfDownloadDomainService pdfDownloadService,
                                     MessagePublisherPort messagePublisher) {
        this.storageService = storageService;
        this.pdfDownloadService = pdfDownloadService;
        this.messagePublisher = messagePublisher;
    }

    @Override
    @Transactional
    public void handleProcessCommand(ProcessDocumentStorageCommand command) {
        log.info("Handling ProcessDocumentStorageCommand for saga: {}, document: {}",
                 command.getSagaId(), command.getDocumentId());

        try {
            // 1. Idempotency check
            if (storageService.existsByInvoiceAndType(command.getDocumentId(), DocumentType.INVOICE_PDF)) {
                log.info("Document already exists for invoice: {}, type: INVOICE_PDF",
                         command.getDocumentId());
                publishAlreadyExistsReply(command);
                return;
            }

            // 2. Download PDF from orchestrator-provided URL
            byte[] content = pdfDownloadService.downloadPdf(command.getSignedPdfUrl());

            // 3. Store document
            String filename = command.getDocumentId() + ".pdf";
            StoredDocument stored = storageService.storeDocument(
                content,
                filename,
                DocumentType.INVOICE_PDF,
                command.getDocumentId()
            );

            // 4. Publish event and reply via outbox
            DocumentStoredEvent event = new DocumentStoredEvent(
                stored.getId(),
                stored.getInvoiceId(),
                command.getInvoiceNumber(),
                stored.getFileName(),
                stored.getStorageUrl(),
                stored.getFileSize(),
                stored.getChecksum(),
                stored.getDocumentType().name(),
                command.getCorrelationId()
            );
            messagePublisher.publishEvent(event);

            DocumentStorageReplyEvent reply = DocumentStorageReplyEvent.success(
                command.getSagaId(),
                command.getSagaStep(),
                command.getCorrelationId()
            );
            messagePublisher.publishReply(reply);

            log.info("Successfully processed ProcessDocumentStorageCommand for saga: {}",
                     command.getSagaId());

        } catch (StorageFailedException | InvalidDocumentException e) {
            log.error("Failed to process document storage command for saga: {}",
                      command.getSagaId(), e);
            publishFailureReply(command, e.getMessage());
        } catch (Exception e) {
            log.error("Unexpected error processing document storage command for saga: {}",
                      command.getSagaId(), e);
            publishFailureReply(command, "Unexpected error: " + e.getMessage());
        }
    }

    private void publishAlreadyExistsReply(ProcessDocumentStorageCommand command) {
        DocumentStorageReplyEvent reply = DocumentStorageReplyEvent.success(
            command.getSagaId(),
            command.getSagaStep(),
            command.getCorrelationId()
        );
        messagePublisher.publishReply(reply);
    }

    private void publishFailureReply(ProcessDocumentStorageCommand command, String error) {
        DocumentStorageReplyEvent reply = DocumentStorageReplyEvent.failure(
            command.getSagaId(),
            command.getSagaStep(),
            command.getCorrelationId(),
            error
        );
        messagePublisher.publishReply(reply);
    }

    @Override
    @Transactional
    public void handleProcessCommand(ProcessSignedXmlStorageCommand command) {
        // TODO: Implement in Task 19
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    @Transactional
    public void handleProcessCommand(ProcessPdfStorageCommand command) {
        // TODO: Implement in Task 20
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    @Transactional
    public void handleCompensation(CompensateDocumentStorageCommand command) {
        // TODO: Implement in Task 21
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    @Transactional
    public void handleCompensation(CompensateSignedXmlStorageCommand command) {
        // TODO: Implement in Task 22
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    @Transactional
    public void handleCompensation(CompensatePdfStorageCommand command) {
        // TODO: Implement in Task 23
        throw new UnsupportedOperationException("Not yet implemented");
    }
}
