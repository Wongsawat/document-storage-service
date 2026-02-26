package com.wpanther.storage.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("DocumentType Enum Tests")
class DocumentTypeTest {

    @Test
    @DisplayName("Should have all expected document types")
    void shouldHaveAllExpectedDocumentTypes() {
        // Given & Then
        assertThat(DocumentType.values())
                .hasSize(6)
                .containsExactly(
                        DocumentType.INVOICE_PDF,
                        DocumentType.INVOICE_XML,
                        DocumentType.SIGNED_XML,
                        DocumentType.UNSIGNED_PDF,
                        DocumentType.ATTACHMENT,
                        DocumentType.OTHER
                );
    }

    @Test
    @DisplayName("Should return correct enum values")
    void shouldReturnCorrectEnumValues() {
        assertThat(DocumentType.INVOICE_PDF.name()).isEqualTo("INVOICE_PDF");
        assertThat(DocumentType.INVOICE_XML.name()).isEqualTo("INVOICE_XML");
        assertThat(DocumentType.SIGNED_XML.name()).isEqualTo("SIGNED_XML");
        assertThat(DocumentType.UNSIGNED_PDF.name()).isEqualTo("UNSIGNED_PDF");
        assertThat(DocumentType.ATTACHMENT.name()).isEqualTo("ATTACHMENT");
        assertThat(DocumentType.OTHER.name()).isEqualTo("OTHER");
    }

    @Test
    @DisplayName("Should get enum by name")
    void shouldGetEnumByName() {
        assertThat(DocumentType.valueOf("INVOICE_PDF")).isEqualTo(DocumentType.INVOICE_PDF);
        assertThat(DocumentType.valueOf("ATTACHMENT")).isEqualTo(DocumentType.ATTACHMENT);
        assertThat(DocumentType.valueOf("OTHER")).isEqualTo(DocumentType.OTHER);
    }

    @Test
    @DisplayName("Should maintain enum ordinal consistency")
    void shouldMaintainEnumOrdinalConsistency() {
        assertThat(DocumentType.INVOICE_PDF.ordinal()).isEqualTo(0);
        assertThat(DocumentType.INVOICE_XML.ordinal()).isEqualTo(1);
        assertThat(DocumentType.SIGNED_XML.ordinal()).isEqualTo(2);
        assertThat(DocumentType.UNSIGNED_PDF.ordinal()).isEqualTo(3);
        assertThat(DocumentType.ATTACHMENT.ordinal()).isEqualTo(4);
        assertThat(DocumentType.OTHER.ordinal()).isEqualTo(5);
    }
}
