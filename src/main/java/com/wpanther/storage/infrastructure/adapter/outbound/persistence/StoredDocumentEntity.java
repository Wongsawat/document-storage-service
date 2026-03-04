package com.wpanther.storage.infrastructure.adapter.outbound.persistence;

import com.wpanther.storage.domain.model.DocumentType;
import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Document(collection = "documents")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StoredDocumentEntity {

    @Id
    private String id;

    @Indexed
    private String fileName;

    private String contentType;

    private String storagePath;

    private String storageUrl;

    private Long fileSize;

    private String checksum;

    @Indexed
    private DocumentType documentType;

    @Indexed
    private LocalDateTime createdAt;

    @Indexed
    private LocalDateTime expiresAt;

    @Indexed
    private String invoiceId;

    @Indexed
    private String invoiceNumber;
}
