package com.wpanther.storage.domain.service;

import com.wpanther.storage.domain.port.inbound.SagaCommandUseCase;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class SagaOrchestrationServiceTest {

    @Mock private FileStorageDomainService storageService;
    @Mock private PdfDownloadDomainService pdfDownloadService;
    @Mock private com.wpanther.storage.domain.port.outbound.MessagePublisherPort messagePublisher;

    @InjectMocks
    private SagaOrchestrationService service;

    @Test
    void service_createsSuccessfully() {
        assertThat(service).isNotNull();
    }

    @Test
    void implementsSagaCommandUseCase() {
        assertThat(service).isInstanceOf(SagaCommandUseCase.class);
    }
}
