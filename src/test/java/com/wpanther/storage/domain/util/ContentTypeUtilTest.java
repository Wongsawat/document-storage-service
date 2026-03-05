package com.wpanther.storage.domain.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ContentTypeUtil Tests")
class ContentTypeUtilTest {

    @Nested
    @DisplayName("determineContentType()")
    class DetermineContentTypeTests {

        @Test
        @DisplayName("Should return application/pdf for .pdf extension")
        void shouldReturnPdfContentType() {
            assertEquals("application/pdf", ContentTypeUtil.determineContentType("document.pdf"));
            assertEquals("application/pdf", ContentTypeUtil.determineContentType("file.PDF"));
            assertEquals("application/pdf", ContentTypeUtil.determineContentType("test.Pdf"));
        }

        @Test
        @DisplayName("Should return application/xml for .xml extension")
        void shouldReturnXmlContentType() {
            assertEquals("application/xml", ContentTypeUtil.determineContentType("data.xml"));
            assertEquals("application/xml", ContentTypeUtil.determineContentType("file.XML"));
        }

        @Test
        @DisplayName("Should return application/json for .json extension")
        void shouldReturnJsonContentType() {
            assertEquals("application/json", ContentTypeUtil.determineContentType("data.json"));
            assertEquals("application/json", ContentTypeUtil.determineContentType("file.JSON"));
        }

        @Test
        @DisplayName("Should return image/png for .png extension")
        void shouldReturnPngContentType() {
            assertEquals("image/png", ContentTypeUtil.determineContentType("image.png"));
            assertEquals("image/png", ContentTypeUtil.determineContentType("photo.PNG"));
        }

        @Test
        @DisplayName("Should return image/jpeg for .jpg and .jpeg extensions")
        void shouldReturnJpegContentType() {
            assertEquals("image/jpeg", ContentTypeUtil.determineContentType("photo.jpg"));
            assertEquals("image/jpeg", ContentTypeUtil.determineContentType("photo.jpeg"));
            assertEquals("image/jpeg", ContentTypeUtil.determineContentType("photo.JPG"));
            assertEquals("image/jpeg", ContentTypeUtil.determineContentType("photo.JPEG"));
        }

        @Test
        @DisplayName("Should return image/gif for .gif extension")
        void shouldReturnGifContentType() {
            assertEquals("image/gif", ContentTypeUtil.determineContentType("animation.gif"));
            assertEquals("image/gif", ContentTypeUtil.determineContentType("image.GIF"));
        }

        @Test
        @DisplayName("Should return text/plain for .txt extension")
        void shouldReturnTextPlainContentType() {
            assertEquals("text/plain", ContentTypeUtil.determineContentType("notes.txt"));
            assertEquals("text/plain", ContentTypeUtil.determineContentType("file.TXT"));
        }

        @Test
        @DisplayName("Should return text/html for .html and .htm extensions")
        void shouldReturnHtmlContentType() {
            assertEquals("text/html", ContentTypeUtil.determineContentType("page.html"));
            assertEquals("text/html", ContentTypeUtil.determineContentType("page.htm"));
            assertEquals("text/html", ContentTypeUtil.determineContentType("page.HTML"));
        }

        @Test
        @DisplayName("Should return text/csv for .csv extension")
        void shouldReturnCsvContentType() {
            assertEquals("text/csv", ContentTypeUtil.determineContentType("data.csv"));
            assertEquals("text/csv", ContentTypeUtil.determineContentType("file.CSV"));
        }

        @Test
        @DisplayName("Should return application/octet-stream for unknown extension")
        void shouldReturnOctetStreamForUnknownExtension() {
            assertEquals("application/octet-stream", ContentTypeUtil.determineContentType("file.xyz"));
            assertEquals("application/octet-stream", ContentTypeUtil.determineContentType("file.unknown"));
            assertEquals("application/octet-stream", ContentTypeUtil.determineContentType("file"));
        }

        @Test
        @DisplayName("Should return application/octet-stream for null filename")
        void shouldReturnOctetStreamForNullFilename() {
            assertEquals("application/octet-stream", ContentTypeUtil.determineContentType(null));
        }

        @Test
        @DisplayName("Should return application/octet-stream for empty filename")
        void shouldReturnOctetStreamForEmptyFilename() {
            assertEquals("application/octet-stream", ContentTypeUtil.determineContentType(""));
            assertEquals("application/octet-stream", ContentTypeUtil.determineContentType("   "));
        }

        @Test
        @DisplayName("Should be case-insensitive for extension matching")
        void shouldBeCaseInsensitive() {
            String contentType1 = ContentTypeUtil.determineContentType("file.PDF");
            String contentType2 = ContentTypeUtil.determineContentType("file.pdf");
            String contentType3 = ContentTypeUtil.determineContentType("file.Pdf");

            assertEquals(contentType1, contentType2);
            assertEquals(contentType2, contentType3);
        }

        @Test
        @DisplayName("Should handle filenames with multiple dots")
        void shouldHandleMultipleDots() {
            assertEquals("application/pdf", ContentTypeUtil.determineContentType("my.document.pdf"));
            assertEquals("application/xml", ContentTypeUtil.determineContentType("data.file.xml"));
            assertEquals("application/octet-stream", ContentTypeUtil.determineContentType("archive.tar.gz"));
        }

        @Test
        @DisplayName("Should prioritize longer extensions when appropriate")
        void shouldPrioritizeCorrectExtension() {
            // .tar.gz - should match .gz but we don't have that, so it falls through
            assertEquals("application/octet-stream", ContentTypeUtil.determineContentType("archive.tar.gz"));

            // .pdf.xml - should match .xml (last matching pattern in some implementations)
            assertEquals("application/xml", ContentTypeUtil.determineContentType("report.pdf.xml"));
        }
    }
}
