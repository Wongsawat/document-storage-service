package com.wpanther.storage.domain.service;

import com.wpanther.storage.domain.event.*;
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
        // TODO: Implement in Task 18
        throw new UnsupportedOperationException("Not yet implemented");
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
