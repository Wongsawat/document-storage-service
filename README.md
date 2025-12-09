# Document Storage Service

Microservice for storing and managing documents (PDFs, XML attachments) with MongoDB metadata storage and pluggable storage backends.

## Overview

The Document Storage Service:

- ✅ **Stores** documents with checksum verification (SHA-256)
- ✅ **Supports** multiple storage backends (local filesystem, AWS S3)
- ✅ **Manages** document metadata in MongoDB
- ✅ **Provides** REST API for upload, download, delete operations
- ✅ **Integrates** with Kafka for event-driven document ingestion
- ✅ **Tracks** document relationships to invoices

## Architecture

### Technology Stack

| Component | Technology |
|-----------|------------|
| Language | Java 21 |
| Framework | Spring Boot 3.2.5 |
| Database | MongoDB 7 |
| Messaging | Apache Kafka |
| Storage | Local FS / AWS S3 |
| Service Discovery | Netflix Eureka |

### Domain Model

**Aggregate Root:**
- `StoredDocument` - Document metadata with checksum verification

**Value Objects:**
- `DocumentType` - GENERATED_INVOICE, ORIGINAL_XML, ATTACHMENT, OTHER

**Domain Services:**
- `FileStorageProvider` - Abstract storage backend interface

### Storage Backends

#### Local Filesystem Storage
- **Structure**: `/var/documents/YYYY/MM/DD/UUID_filename.pdf`
- **URL Format**: `http://localhost:8084/api/v1/documents/{id}`
- **Use Case**: Development, small deployments

#### AWS S3 Storage
- **Structure**: `s3://bucket/YYYY/MM/DD/UUID_filename.pdf`
- **URL Format**: `https://bucket.s3.amazonaws.com/YYYY/MM/DD/UUID_filename.pdf`
- **Use Case**: Production, scalable deployments

## Document Storage Flow

```
1. PDF Generation Service publishes PdfGeneratedEvent
   ↓
2. Document Storage Service receives event via Kafka
   ↓
3. Download PDF from provided URL
   ↓
4. Calculate SHA-256 checksum
   ↓
5. Store file via storage provider (local or S3)
   ↓
6. Save metadata to MongoDB
   ↓
7. (Future) Publish DocumentStoredEvent
```

## REST API

### Upload Document
```bash
POST /api/v1/documents
Content-Type: multipart/form-data

Parameters:
- file: MultipartFile (required)
- documentType: DocumentType (optional, default: OTHER)
- invoiceId: String (optional)
- invoiceNumber: String (optional)

Response: 201 Created
{
  "documentId": "uuid",
  "fileName": "INV-2025-001_invoice.pdf",
  "url": "http://localhost:8084/api/v1/documents/uuid",
  "fileSize": 125000,
  "checksum": "sha256hex..."
}
```

### Download Document
```bash
GET /api/v1/documents/{id}

Response: 200 OK
Content-Disposition: attachment; filename="INV-2025-001_invoice.pdf"
Content-Type: application/pdf

[Binary PDF content]
```

### Get Document Metadata
```bash
GET /api/v1/documents/{id}/metadata

Response: 200 OK
{
  "id": "uuid",
  "fileName": "INV-2025-001_invoice.pdf",
  "contentType": "application/pdf",
  "fileSize": 125000,
  "checksum": "sha256hex...",
  "documentType": "GENERATED_INVOICE",
  "createdAt": "2025-12-03T10:30:00",
  "invoiceId": "uuid",
  "invoiceNumber": "INV-2025-001"
}
```

### Delete Document
```bash
DELETE /api/v1/documents/{id}

Response: 204 No Content
```

### Get Documents by Invoice
```bash
GET /api/v1/documents/invoice/{invoiceId}

Response: 200 OK
[
  {
    "id": "uuid",
    "fileName": "INV-2025-001_invoice.pdf",
    "url": "http://localhost:8084/api/v1/documents/uuid",
    "fileSize": 125000,
    "documentType": "GENERATED_INVOICE",
    "createdAt": "2025-12-03T10:30:00"
  }
]
```

## Kafka Integration

### Consumed Topics
- `pdf.generated` - Automatically stores generated PDFs

### Published Topics
- `document.stored` - (Future) Notifies when document is stored

### Event Schema

**Input: PdfGeneratedEvent**
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

## MongoDB Collections

### documents Collection
```javascript
{
  _id: "uuid",
  fileName: "INV-2025-001_invoice.pdf",
  contentType: "application/pdf",
  storagePath: "/var/documents/2025/12/03/uuid_invoice.pdf",
  storageUrl: "http://localhost:8084/api/v1/documents/uuid",
  fileSize: 125000,
  checksum: "sha256hex...",
  documentType: "GENERATED_INVOICE",
  createdAt: ISODate("2025-12-03T10:30:00Z"),
  expiresAt: null,
  invoiceId: "uuid",
  invoiceNumber: "INV-2025-001"
}
```

### Indexes
- `fileName` - For filename lookups
- `documentType` - For type-based queries
- `invoiceId` - For invoice relationships
- `invoiceNumber` - For invoice number searches
- `expiresAt` - For TTL-based cleanup
- `createdAt` - For time-range queries

## Configuration

### Environment Variables

| Variable | Description | Default |
|----------|-------------|---------|
| `MONGODB_HOST` | MongoDB host | `localhost` |
| `MONGODB_PORT` | MongoDB port | `27017` |
| `MONGODB_DATABASE` | Database name | `document_storage` |
| `KAFKA_BROKERS` | Kafka servers | `localhost:9092` |
| `STORAGE_PROVIDER` | Storage backend | `local` or `s3` |
| `LOCAL_STORAGE_PATH` | Local filesystem path | `/var/documents` |
| `STORAGE_BASE_URL` | Base URL for documents | `http://localhost:8084/api/v1/documents` |
| `S3_BUCKET_NAME` | S3 bucket name | - |
| `AWS_REGION` | AWS region | `us-east-1` |
| `AWS_ACCESS_KEY` | AWS access key | - |
| `AWS_SECRET_KEY` | AWS secret key | - |

### Local Filesystem Configuration

```yaml
app:
  storage:
    provider: local
    local:
      base-path: /var/documents
      base-url: http://localhost:8084/api/v1/documents
```

### AWS S3 Configuration

```yaml
app:
  storage:
    provider: s3
    s3:
      bucket-name: invoice-documents
      region: us-east-1
      access-key: ${AWS_ACCESS_KEY}
      secret-key: ${AWS_SECRET_KEY}
      base-url: https://invoice-documents.s3.amazonaws.com
```

## Running the Service

### Prerequisites
1. MongoDB 7+ running
2. Kafka broker running
3. (For local storage) Directory `/var/documents` with write permissions
4. (For S3 storage) AWS credentials with S3 access

### Build
```bash
mvn clean package
```

### Run Locally
```bash
export MONGODB_HOST=localhost
export KAFKA_BROKERS=localhost:9092
export STORAGE_PROVIDER=local
export LOCAL_STORAGE_PATH=/tmp/documents

mvn spring-boot:run
```

### Run with Docker

#### Local Filesystem Storage
```bash
docker build -t document-storage-service:latest .

docker run -p 8084:8084 \
  -e MONGODB_HOST=mongo \
  -e KAFKA_BROKERS=kafka:29092 \
  -e STORAGE_PROVIDER=local \
  -v /host/documents:/var/documents \
  document-storage-service:latest
```

#### AWS S3 Storage
```bash
docker run -p 8084:8084 \
  -e MONGODB_HOST=mongo \
  -e KAFKA_BROKERS=kafka:29092 \
  -e STORAGE_PROVIDER=s3 \
  -e S3_BUCKET_NAME=invoice-documents \
  -e AWS_REGION=us-east-1 \
  -e AWS_ACCESS_KEY=your-access-key \
  -e AWS_SECRET_KEY=your-secret-key \
  document-storage-service:latest
```

## Security Features

### Checksum Verification
- **SHA-256 checksum** calculated on upload
- **Verified on download** to ensure integrity
- **Prevents** tampered documents from being served

### File Size Limits
- **Maximum upload size**: 50MB (configurable)
- **Prevents** resource exhaustion attacks

### Storage Path Isolation
- **UUID-based filenames** prevent path traversal
- **Date-based directories** organize storage
- **Non-root container user** for security

## Project Structure

```
src/main/java/com/invoice/storage/
├── DocumentStorageServiceApplication.java
├── domain/
│   ├── model/              # StoredDocument aggregate
│   └── service/            # FileStorageProvider interface
├── application/
│   ├── controller/         # REST API endpoints
│   └── service/            # DocumentStorageService
└── infrastructure/
    ├── persistence/        # MongoDB entities
    ├── storage/            # Storage provider implementations
    └── messaging/          # Kafka consumers/config
```

## Testing

### Unit Tests
```bash
mvn test
```

### Integration Tests with Testcontainers
```bash
mvn verify
```

## Monitoring

### Actuator Endpoints
- `/actuator/health` - Service health status
- `/actuator/info` - Service information
- `/actuator/metrics` - Application metrics
- `/actuator/prometheus` - Prometheus metrics

### Health Check
```bash
curl http://localhost:8084/actuator/health
```

## Future Enhancements

### Planned Features
1. **Document Expiration** - Automatic cleanup of expired documents
2. **Versioning** - Support multiple versions of same document
3. **Thumbnail Generation** - Generate thumbnails for PDFs
4. **Full-Text Search** - Index document content for search
5. **Virus Scanning** - Integrate with ClamAV or similar
6. **Encryption** - Encrypt documents at rest
7. **Audit Log** - Track all document access and modifications
8. **Batch Upload** - Upload multiple documents at once
9. **Pre-signed URLs** - Temporary access URLs for S3
10. **Event Publishing** - Publish DocumentStoredEvent to Kafka

### Recommended Improvements
1. **Retry Logic** - Implement retry for failed storage operations
2. **Circuit Breaker** - Protect against storage backend failures
3. **Rate Limiting** - Limit upload requests per client
4. **Compression** - Compress documents before storage
5. **CDN Integration** - Serve documents via CDN

## License

MIT License

## Contact

Maintained by wpanther (rabbit_roger@yahoo.com)
