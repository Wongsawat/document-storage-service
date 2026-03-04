package com.wpanther.storage.domain.service;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class PdfDownloadDomainServiceTest {

    @Test
    void service_createsSuccessfully() {
        PdfDownloadDomainService service = new PdfDownloadDomainService();
        assertThat(service).isNotNull();
    }
}
