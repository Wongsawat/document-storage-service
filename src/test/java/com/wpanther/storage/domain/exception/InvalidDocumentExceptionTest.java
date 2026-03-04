package com.wpanther.storage.domain.exception;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class InvalidDocumentExceptionTest {

    @Test
    void exception_containsMessage() {
        InvalidDocumentException ex = new InvalidDocumentException("invalid file type");
        assertThat(ex.getMessage()).contains("invalid file type");
    }

    @Test
    void exceptionWithCause_containsCause() {
        Throwable cause = new RuntimeException("parsing error");
        InvalidDocumentException ex = new InvalidDocumentException("invalid file type", cause);
        assertThat(ex.getCause()).isSameAs(cause);
    }
}
