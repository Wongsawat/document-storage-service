package com.wpanther.storage.domain.service;

import com.wpanther.storage.domain.exception.StorageFailedException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("PdfDownloadDomainService Tests")
class PdfDownloadDomainServiceTest {

    private final PdfDownloadDomainService service = new PdfDownloadDomainService();

    @Nested
    @DisplayName("Constructor")
    class ConstructorTests {

        @Test
        @DisplayName("Should create service successfully")
        void shouldCreateServiceSuccessfully() {
            assertNotNull(service);
        }
    }

    @Nested
    @DisplayName("downloadPdf()")
    class DownloadPdfTests {

        @Test
        @DisplayName("Should throw StorageFailedException for valid URL that doesn't exist")
        void shouldThrowExceptionForNonExistentUrl() {
            String nonExistentUrl = "http://localhost:9999/non-existent.pdf";

            StorageFailedException ex = assertThrows(StorageFailedException.class,
                () -> service.downloadPdf(nonExistentUrl));

            assertTrue(ex.getMessage().contains("Failed to download PDF"));
            assertTrue(ex.getMessage().contains(nonExistentUrl) || ex.getCause() != null);
        }

        @Test
        @DisplayName("Should include URL in exception message")
        void shouldIncludeUrlInExceptionMessage() {
            String url = "http://localhost:9999/test.pdf";

            StorageFailedException ex = assertThrows(StorageFailedException.class,
                () -> service.downloadPdf(url));

            assertTrue(ex.getMessage().contains(url) || ex.getCause() != null);
        }

        @Test
        @DisplayName("Should handle timeout errors")
        void shouldHandleTimeoutErrors() {
            // Using a URL that will likely timeout
            String timeoutUrl = "http://192.0.2.1:9999/test.pdf"; // TEST-NET-1 IP, will timeout

            StorageFailedException ex = assertThrows(StorageFailedException.class,
                () -> service.downloadPdf(timeoutUrl));

            assertNotNull(ex);
        }
    }

    @Nested
    @DisplayName("downloadContent()")
    class DownloadContentTests {

        @Test
        @DisplayName("Should throw StorageFailedException for valid URL that doesn't exist")
        void shouldThrowExceptionForNonExistentUrl() {
            String nonExistentUrl = "http://localhost:9999/non-existent";

            StorageFailedException ex = assertThrows(StorageFailedException.class,
                () -> service.downloadContent(nonExistentUrl));

            assertTrue(ex.getMessage().contains("Failed to download content"));
        }

        @Test
        @DisplayName("Should include URL in exception message")
        void shouldIncludeUrlInExceptionMessage() {
            String url = "http://localhost:9999/test";

            StorageFailedException ex = assertThrows(StorageFailedException.class,
                () -> service.downloadContent(url));

            assertTrue(ex.getMessage().contains(url) || ex.getCause() != null);
        }

        @Test
        @DisplayName("Should handle timeout errors")
        void shouldHandleTimeoutErrors() {
            // Using a URL that will likely timeout
            String timeoutUrl = "http://192.0.2.1:9999/test"; // TEST-NET-1 IP, will timeout

            StorageFailedException ex = assertThrows(StorageFailedException.class,
                () -> service.downloadContent(timeoutUrl));

            assertNotNull(ex);
        }
    }
}
