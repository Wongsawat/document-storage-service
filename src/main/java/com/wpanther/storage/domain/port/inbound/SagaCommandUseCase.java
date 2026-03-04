package com.wpanther.storage.domain.port.inbound;

import com.wpanther.storage.domain.event.*;
import com.wpanther.storage.domain.event.*;

/**
 * Inbound port for saga command handling.
 * Implemented by SagaOrchestrationService.
 * Called by SagaCommandAdapter (Kafka/Camel adapter).
 */
public interface SagaCommandUseCase {
    void handleProcessCommand(ProcessDocumentStorageCommand command);
    void handleProcessCommand(ProcessSignedXmlStorageCommand command);
    void handleProcessCommand(ProcessPdfStorageCommand command);
    void handleCompensation(CompensateDocumentStorageCommand command);
    void handleCompensation(CompensateSignedXmlStorageCommand command);
    void handleCompensation(CompensatePdfStorageCommand command);
}
