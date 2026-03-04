package com.wpanther.storage.domain.exception;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class DocumentNotFoundExceptionTest {

    @Test
    void exception_containsDocumentId() {
        DocumentNotFoundException ex = new DocumentNotFoundException("doc-123");

        assertThat(ex.getMessage()).contains("doc-123");
        assertThat(ex.documentId()).isEqualTo("doc-123");
    }
}
