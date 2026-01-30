package com.invoice.storage.infrastructure.messaging;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * Event published when PDF generation is completed.
 *
 * Legacy event class - currently unused but kept for potential future use.
 * Extends IntegrationEvent for consistent event metadata across services.
 */
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PdfGeneratedEvent extends IntegrationEvent implements Serializable {

    private static final long serialVersionUID = 1L;

    private String invoiceId;
    private String invoiceNumber;
    private String documentId;
    private String documentUrl;
    private long fileSize;
    private boolean xmlEmbedded;
    private boolean digitallySigned;
    private LocalDateTime generatedAt;
    private String correlationId;
}
