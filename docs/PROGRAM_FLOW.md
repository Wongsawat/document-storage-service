# Program Flow

This document describes the main program flows in the Document Storage Service.

## 1. Document Upload Flow (REST API)

```
Client                    Controller                  Service                    Storage Provider         MongoDB
  │                          │                          │                              │                    │
  │  POST /api/v1/documents  │                          │                              │                    │
  │  (multipart/form-data)   │                          │                              │                    │
  │─────────────────────────>│                          │                              │                    │
  │                          │                          │                              │                    │
  │                          │  storeDocument()         │                              │                    │
  │                          │─────────────────────────>│                              │                    │
  │                          │                          │                              │                    │
  │                          │                          │  calculateChecksum()         │                    │
  │                          │                          │  (SHA-256)                   │                    │
  │                          │                          │──────────┐                   │                    │
  │                          │                          │<─────────┘                   │                    │
  │                          │                          │                              │                    │
  │                          │                          │  store(content, fileName)    │                    │
  │                          │                          │─────────────────────────────>│                    │
  │                          │                          │                              │                    │
  │                          │                          │                              │  Create directory  │
  │                          │                          │                              │  YYYY/MM/DD/       │
  │                          │                          │                              │──────────┐         │
  │                          │                          │                              │<─────────┘         │
  │                          │                          │                              │                    │
  │                          │                          │                              │  Write file with   │
  │                          │                          │                              │  UUID filename     │
  │                          │                          │                              │──────────┐         │
  │                          │                          │                              │<─────────┘         │
  │                          │                          │                              │                    │
  │                          │                          │  StorageResult(path, url)    │                    │
  │                          │                          │<─────────────────────────────│                    │
  │                          │                          │                              │                    │
  │                          │                          │  Create StoredDocument       │                    │
  │                          │                          │──────────┐                   │                    │
  │                          │                          │<─────────┘                   │                    │
  │                          │                          │                              │                    │
  │                          │                          │  save(entity)                │                    │
  │                          │                          │─────────────────────────────────────────────────>│
  │                          │                          │                              │                    │
  │                          │                          │  Saved                       │                    │
  │                          │                          │<─────────────────────────────────────────────────│
  │                          │                          │                              │                    │
  │                          │  StoredDocument          │                              │                    │
  │                          │<─────────────────────────│                              │                    │
  │                          │                          │                              │                    │
  │  201 Created             │                          │                              │                    │
  │  {documentId, url, ...}  │                          │                              │                    │
  │<─────────────────────────│                          │                              │                    │
```

### Key Files
- [DocumentStorageController.java](../src/main/java/com/invoice/storage/application/controller/DocumentStorageController.java) - `uploadDocument()`
- [DocumentStorageService.java](../src/main/java/com/invoice/storage/application/service/DocumentStorageService.java) - `storeDocument()`
- [LocalFileStorageProvider.java](../src/main/java/com/invoice/storage/infrastructure/storage/LocalFileStorageProvider.java) - `store()`
- [S3FileStorageProvider.java](../src/main/java/com/invoice/storage/infrastructure/storage/S3FileStorageProvider.java) - `store()`

---

## 2. Document Download Flow (REST API)

```
Client                    Controller                  Service                    Storage Provider         MongoDB
  │                          │                          │                              │                    │
  │  GET /api/v1/documents   │                          │                              │                    │
  │      /{id}               │                          │                              │                    │
  │─────────────────────────>│                          │                              │                    │
  │                          │                          │                              │                    │
  │                          │  getDocument(id)         │                              │                    │
  │                          │─────────────────────────>│                              │                    │
  │                          │                          │                              │                    │
  │                          │                          │  findById(id)                │                    │
  │                          │                          │─────────────────────────────────────────────────>│
  │                          │                          │                              │                    │
  │                          │                          │  StoredDocumentEntity        │                    │
  │                          │                          │<─────────────────────────────────────────────────│
  │                          │                          │                              │                    │
  │                          │  StoredDocument          │                              │                    │
  │                          │<─────────────────────────│                              │                    │
  │                          │                          │                              │                    │
  │                          │  getDocumentContent(id)  │                              │                    │
  │                          │─────────────────────────>│                              │                    │
  │                          │                          │                              │                    │
  │                          │                          │  retrieve(storagePath)       │                    │
  │                          │                          │─────────────────────────────>│                    │
  │                          │                          │                              │                    │
  │                          │                          │                              │  Read file         │
  │                          │                          │                              │──────────┐         │
  │                          │                          │                              │<─────────┘         │
  │                          │                          │                              │                    │
  │                          │                          │  byte[] content              │                    │
  │                          │                          │<─────────────────────────────│                    │
  │                          │                          │                              │                    │
  │                          │                          │  verifyChecksum()            │                    │
  │                          │                          │  (SHA-256 match?)            │                    │
  │                          │                          │──────────┐                   │                    │
  │                          │                          │<─────────┘                   │                    │
  │                          │                          │                              │                    │
  │                          │  byte[] content          │                              │                    │
  │                          │<─────────────────────────│                              │                    │
  │                          │                          │                              │                    │
  │  200 OK                  │                          │                              │                    │
  │  Content-Disposition:    │                          │                              │                    │
  │    attachment            │                          │                              │                    │
  │  [Binary PDF content]    │                          │                              │                    │
  │<─────────────────────────│                          │                              │                    │
```

### Checksum Verification
On download, the service:
1. Retrieves file content from storage
2. Calculates SHA-256 of retrieved content
3. Compares with stored checksum
4. Returns 500 error if mismatch (document integrity compromised)

### Key Files
- [DocumentStorageController.java](../src/main/java/com/invoice/storage/application/controller/DocumentStorageController.java) - `downloadDocument()`
- [DocumentStorageService.java](../src/main/java/com/invoice/storage/application/service/DocumentStorageService.java) - `getDocument()`, `getDocumentContent()`

---

## 3. Kafka Event-Driven Flow (PDF Ingestion)

```
PDF Generation           Kafka                    PdfEventListener            Service                 Storage
Service                  (pdf.generated)              │                          │                       │
  │                          │                        │                          │                       │
  │  PdfGeneratedEvent       │                        │                          │                       │
  │  {invoiceId, docUrl,     │                        │                          │                       │
  │   invoiceNumber, ...}    │                        │                          │                       │
  │─────────────────────────>│                        │                          │                       │
  │                          │                        │                          │                       │
  │                          │  handlePdfGenerated()  │                          │                       │
  │                          │───────────────────────>│                          │                       │
  │                          │                        │                          │                       │
  │                          │                        │  downloadPdf(docUrl)     │                       │
  │                          │                        │  (HTTP GET)              │                       │
  │                          │                        │─────────────────────────>│ PDF Gen Service       │
  │                          │                        │                          │                       │
  │                          │                        │  byte[] pdfContent       │                       │
  │                          │                        │<─────────────────────────│                       │
  │                          │                        │                          │                       │
  │                          │                        │  extractFileName()       │                       │
  │                          │                        │──────────┐               │                       │
  │                          │                        │<─────────┘               │                       │
  │                          │                        │                          │                       │
  │                          │                        │  storeDocument(          │                       │
  │                          │                        │    content,              │                       │
  │                          │                        │    fileName,             │                       │
  │                          │                        │    "application/pdf",    │                       │
  │                          │                        │    GENERATED_INVOICE,    │                       │
  │                          │                        │    invoiceId,            │                       │
  │                          │                        │    invoiceNumber)        │                       │
  │                          │                        │───────────────────────────────────────────────────>
  │                          │                        │                          │                       │
  │                          │                        │                          │  [Upload Flow]        │
  │                          │                        │                          │  (see Flow 1)         │
  │                          │                        │                          │───────────────────────>
  │                          │                        │                          │                       │
  │                          │                        │  StoredDocument          │                       │
  │                          │                        │<───────────────────────────────────────────────────
  │                          │                        │                          │                       │
  │                          │                        │  Log success             │                       │
  │                          │                        │                          │                       │
  │                          │  ACK                   │                          │                       │
  │                          │<───────────────────────│                          │                       │
```

### Event Schema (PdfGeneratedEvent)
```json
{
  "eventId": "uuid",
  "invoiceId": "uuid",
  "invoiceNumber": "INV-2025-001",
  "documentId": "uuid",
  "documentUrl": "http://pdf-service:8083/documents/path/file.pdf",
  "fileSize": 125000,
  "xmlEmbedded": true,
  "digitallySigned": false,
  "generatedAt": "2025-12-03T10:30:00",
  "correlationId": "uuid"
}
```

### Error Handling
- On failure: Exception is thrown, Kafka message not acknowledged
- Consumer pauses and requires manual intervention
- TODO: Dead Letter Queue for failed events

### Key Files
- [PdfEventListener.java](../src/main/java/com/invoice/storage/infrastructure/messaging/PdfEventListener.java) - `handlePdfGenerated()`
- [PdfGeneratedEvent.java](../src/main/java/com/invoice/storage/infrastructure/messaging/PdfGeneratedEvent.java) - Event DTO
- [KafkaConfig.java](../src/main/java/com/invoice/storage/infrastructure/messaging/KafkaConfig.java) - Kafka configuration

---

## 4. Document Delete Flow

```
Client                    Controller                  Service                    Storage Provider         MongoDB
  │                          │                          │                              │                    │
  │  DELETE /api/v1/         │                          │                              │                    │
  │    documents/{id}        │                          │                              │                    │
  │─────────────────────────>│                          │                              │                    │
  │                          │                          │                              │                    │
  │                          │  deleteDocument(id)      │                              │                    │
  │                          │─────────────────────────>│                              │                    │
  │                          │                          │                              │                    │
  │                          │                          │  getDocument(id)             │                    │
  │                          │                          │─────────────────────────────────────────────────>│
  │                          │                          │                              │                    │
  │                          │                          │  StoredDocument              │                    │
  │                          │                          │<─────────────────────────────────────────────────│
  │                          │                          │                              │                    │
  │                          │                          │  delete(storagePath)         │                    │
  │                          │                          │─────────────────────────────>│                    │
  │                          │                          │                              │                    │
  │                          │                          │                              │  Delete file       │
  │                          │                          │                              │──────────┐         │
  │                          │                          │                              │<─────────┘         │
  │                          │                          │                              │                    │
  │                          │                          │  Success                     │                    │
  │                          │                          │<─────────────────────────────│                    │
  │                          │                          │                              │                    │
  │                          │                          │  deleteById(id)              │                    │
  │                          │                          │─────────────────────────────────────────────────>│
  │                          │                          │                              │                    │
  │                          │                          │  Deleted                     │                    │
  │                          │                          │<─────────────────────────────────────────────────│
  │                          │                          │                              │                    │
  │                          │  void                    │                              │                    │
  │                          │<─────────────────────────│                              │                    │
  │                          │                          │                              │                    │
  │  204 No Content          │                          │                              │                    │
  │<─────────────────────────│                          │                              │                    │
```

### Key Files
- [DocumentStorageController.java](../src/main/java/com/invoice/storage/application/controller/DocumentStorageController.java) - `deleteDocument()`
- [DocumentStorageService.java](../src/main/java/com/invoice/storage/application/service/DocumentStorageService.java) - `deleteDocument()`

---

## 5. Storage Provider Selection

```
Application Startup
       │
       ▼
┌──────────────────────────────────────────────────────┐
│  Read app.storage.provider property                  │
│  (default: "local")                                  │
└──────────────────────────────────────────────────────┘
       │
       ▼
   ┌───────────────────┐
   │ provider == "s3"? │
   └───────────────────┘
       │           │
      Yes          No
       │           │
       ▼           ▼
┌─────────────┐  ┌─────────────────────┐
│ S3File      │  │ LocalFile           │
│ Storage     │  │ Storage             │
│ Provider    │  │ Provider            │
│ (Bean)      │  │ (Bean, default)     │
└─────────────┘  └─────────────────────┘
       │           │
       └─────┬─────┘
             │
             ▼
┌──────────────────────────────────────────────────────┐
│  FileStorageProvider interface injected into        │
│  DocumentStorageService                             │
└──────────────────────────────────────────────────────┘
```

### Conditional Bean Activation

```java
// LocalFileStorageProvider.java
@ConditionalOnProperty(name = "app.storage.provider", havingValue = "local", matchIfMissing = true)

// S3FileStorageProvider.java
@ConditionalOnProperty(name = "app.storage.provider", havingValue = "s3")
```

---

## 6. File Storage Structure

Both storage providers use the same path structure:

```
{base-path}/
└── {YYYY}/
    └── {MM}/
        └── {DD}/
            └── {UUID}.{extension}

Example:
/var/documents/2025/12/07/550e8400-e29b-41d4-a716-446655440000.pdf
```

### Local Storage
- Base path: Configured via `app.storage.local.base-path` (default: `/var/documents`)
- URL format: `{base-url}/documents/{relative-path}`

### S3 Storage
- Bucket: Configured via `app.storage.s3.bucket-name`
- Key format: `{YYYY}/{MM}/{DD}/{UUID}.{extension}`
- URL format: `https://{bucket}.s3.{region}.amazonaws.com/{key}`
