package com.wpanther.storage.domain.exception;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class StorageFailedExceptionTest {

    @Test
    void exception_containsReason() {
        StorageFailedException ex = new StorageFailedException("disk full");
        assertThat(ex.getMessage()).contains("disk full");
    }

    @Test
    void exceptionWithCause_containsCause() {
        Throwable cause = new RuntimeException("IO error");
        StorageFailedException ex = new StorageFailedException("disk full", cause);
        assertThat(ex.getCause()).isSameAs(cause);
    }
}
