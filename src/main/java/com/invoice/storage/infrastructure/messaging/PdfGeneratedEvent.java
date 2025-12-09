package com.invoice.storage.infrastructure.messaging;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * Event published when PDF generation is completed
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PdfGeneratedEvent implements Serializable {

    private String eventId;
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
