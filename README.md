# Document Storage Service

Microservice for storing and managing documents (PDFs, XML attachments) with MongoDB metadata storage and pluggable storage backends. Participates in the **Saga Orchestrator** as the `STORE_DOCUMENT` step.

## Overview

The Document Storage Service:

- **Stores** documents with checksum verification (SHA-256)
- **Supports** multiple storage backends (local filesystem, AWS S3)
- **Manages** document metadata in MongoDB
- **Provides** REST API for upload, download, delete operations
- **Participates** in Saga Orchestrator as `STORE_DOCUMENT` step
- **Implements** Transactional Outbox pattern for reliable event publishing
- **Tracks** document relationships to invoices

## Architecture

### Technology Stack

| Component | Technology |
|-----------|------------|
| Language | Java 21 |
| Framework | Spring Boot 3.2.5 |
| Metadata DB | MongoDB 7 |
| Outbox DB | PostgreSQL 16 |
| Messaging | Apache Kafka (via Apache Camel 4.14.4) |
| Storage | Local FS / AWS S3 |
| Saga | saga-commons (Transactional Outbox + Debezium CDC) |
| Service Discovery | Netflix Eureka |

### Domain Model

**Aggregate Root:**
- `StoredDocument` - Document metadata with checksum verification

**Value Objects:**
- `DocumentType` - `INVOICE_PDF`, `INVOICE_XML`, `ATTACHMENT`, `OTHER`

**Domain Services:**
- `FileStorageProvider` - Abstract storage backend interface

**Domain Events:**
- `DocumentStoredEvent` - Published after successful document storage
- `ProcessDocumentStorageCommand` - Saga command from orchestrator
- `CompensateDocumentStorageCommand` - Saga compensation command
- `DocumentStorageReplyEvent` - Saga reply (SUCCESS/FAILURE/COMPENSATED)

### Storage Backends

#### Local Filesystem Storage
- **Structure**: `/var/documents/YYYY/MM/DD/UUID.ext`
- **URL Format**: `http://localhost:8084/api/v1/documents/{id}`
- **Use Case**: Development, small deployments

#### AWS S3 Storage
- **Structure**: `s3://bucket/YYYY/MM/DD/UUID_filename.ext`
- **URL Format**: `https://bucket.s3.amazonaws.com/YYYY/MM/DD/UUID_filename.ext`
- **Supports**: MinIO/Ceph via `S3_ENDPOINT` and `S3_PATH_STYLE_ACCESS`
- **Use Case**: Production, scalable deployments

## Saga Integration (STORE_DOCUMENT Step)

This service is step 8 of 9 in the saga pipeline:

```
PROCESS_INVOICE/TAX_INVOICE → SIGN_XML → GENERATE_PDF → SIGN_PDF → STORE_DOCUMENT → SEND_EBMS
```

### Saga Flow

```
Orchestrator                          Document Storage Service
────────────                          ────────────────────────
saga.command.document-storage    →    SagaRouteConfig (Camel)
                                        → SagaCommandHandler.handleProcessCommand()
                                          1. Idempotency check (findByInvoiceId)
                                          2. Download signed PDF from signedPdfUrl
                                          3. Store file + MongoDB metadata
                                          4. Publish DocumentStoredEvent (outbox)
                                          5. Publish SUCCESS reply (outbox)
saga.reply.document-storage      ←    SagaReplyPublisher (via Debezium CDC)

saga.compensation.document-storage →  SagaCommandHandler.handleCompensation()
                                          1. Find documents by invoiceId
                                          2. Delete file + MongoDB metadata
                                          3. Publish COMPENSATED reply (outbox)
saga.reply.document-storage      ←    SagaReplyPublisher (via Debezium CDC)
```

### Transactional Outbox Pattern

Both `EventPublisher` and `SagaReplyPublisher` write events to the `outbox_events` table in PostgreSQL within the same `@Transactional` boundary as the saga command processing. Debezium CDC captures changes from the outbox table and publishes them to Kafka, ensuring exactly-once delivery semantics.

### Kafka Topics

| Topic | Direction | Description |
|-------|-----------|-------------|
| `saga.command.document-storage` | Consumed (Camel) | Process commands from orchestrator |
| `saga.compensation.document-storage` | Consumed (Camel) | Compensation commands from orchestrator |
| `saga.reply.document-storage` | Published (Outbox) | Replies to orchestrator |
| `document.stored` | Published (Outbox) | Downstream notification of stored documents |
| `document-storage.dlq` | Dead Letter | Failed messages after 3 retries |

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
  "documentType": "INVOICE_PDF",
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
    "documentType": "INVOICE_PDF",
    "createdAt": "2025-12-03T10:30:00"
  }
]
```

## MongoDB Collections

### documents Collection
```javascript
{
  _id: "uuid",
  fileName: "INV-2025-001_invoice.pdf",
  contentType: "application/pdf",
  storagePath: "/var/documents/2025/12/03/uuid.pdf",
  storageUrl: "http://localhost:8084/api/v1/documents/uuid",
  fileSize: 125000,
  checksum: "sha256hex...",
  documentType: "INVOICE_PDF",
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

## PostgreSQL Tables

### outbox_events Table
```sql
CREATE TABLE outbox_events (
    id UUID PRIMARY KEY,
    aggregate_type VARCHAR(255) NOT NULL,
    aggregate_id VARCHAR(255) NOT NULL,
    event_type VARCHAR(255) NOT NULL,
    topic VARCHAR(255) NOT NULL,
    payload TEXT NOT NULL,
    status VARCHAR(50) NOT NULL DEFAULT 'PENDING',
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    processed_at TIMESTAMP
);
```

Flyway migration: `V1__create_outbox_events_table.sql`

## Configuration

### Environment Variables

| Variable | Description | Default |
|----------|-------------|---------|
| `DB_HOST` | PostgreSQL host (outbox) | `localhost` |
| `DB_PORT` | PostgreSQL port | `5432` |
| `DB_NAME` | PostgreSQL database | `documentstorage_db` |
| `DB_USERNAME` | PostgreSQL username | `postgres` |
| `DB_PASSWORD` | PostgreSQL password | `postgres` |
| `MONGODB_HOST` | MongoDB host | `localhost` |
| `MONGODB_PORT` | MongoDB port | `27017` |
| `MONGODB_DATABASE` | MongoDB database name | `document_storage` |
| `KAFKA_BROKERS` | Kafka servers | `localhost:9092` |
| `STORAGE_PROVIDER` | Storage backend | `local` or `s3` |
| `LOCAL_STORAGE_PATH` | Local filesystem path | `/var/documents` |
| `STORAGE_BASE_URL` | Base URL for documents | `http://localhost:8084/api/v1/documents` |
| `S3_BUCKET_NAME` | S3 bucket name | - |
| `AWS_REGION` | AWS region | `us-east-1` |
| `S3_ENDPOINT` | Custom S3 endpoint (MinIO, Ceph) | (empty) |
| `S3_PATH_STYLE_ACCESS` | Use path-style S3 access | `false` |
| `EUREKA_SERVER` | Eureka URL | `http://localhost:8761/eureka/` |

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
```

## Running the Service

### Prerequisites
1. MongoDB 7+ running on `localhost:27017`
2. PostgreSQL 16+ running with database `documentstorage_db`
3. Kafka broker running on `localhost:9092`
4. saga-commons library installed: `cd ../../saga-commons && mvn clean install`
5. (For local storage) Directory with write permissions
6. (For S3 storage) AWS credentials with S3 access

### Build
```bash
mvn clean package
```

### Run Locally
```bash
DB_HOST=localhost DB_PORT=5432 DB_NAME=documentstorage_db \
  MONGODB_HOST=localhost KAFKA_BROKERS=localhost:9092 \
  STORAGE_PROVIDER=local LOCAL_STORAGE_PATH=/tmp/documents \
  mvn spring-boot:run
```

### Run with Docker

#### Local Filesystem Storage
```bash
docker build -t document-storage-service:latest .

docker run -p 8084:8084 \
  -e MONGODB_HOST=mongo \
  -e DB_HOST=postgres \
  -e DB_NAME=documentstorage_db \
  -e KAFKA_BROKERS=kafka:29092 \
  -e STORAGE_PROVIDER=local \
  -v /host/documents:/var/documents \
  document-storage-service:latest
```

#### AWS S3 Storage
```bash
docker run -p 8084:8084 \
  -e MONGODB_HOST=mongo \
  -e DB_HOST=postgres \
  -e DB_NAME=documentstorage_db \
  -e KAFKA_BROKERS=kafka:29092 \
  -e STORAGE_PROVIDER=s3 \
  -e S3_BUCKET_NAME=invoice-documents \
  -e AWS_REGION=us-east-1 \
  document-storage-service:latest
```

## Security Features

### Checksum Verification
- **SHA-256 checksum** calculated on upload
- **Prevents** tampered documents from being served

### Storage Path Isolation
- **UUID-based filenames** prevent path traversal
- **Date-based directories** organize storage

## Project Structure

```
src/main/java/com/wpanther/storage/
├── DocumentStorageServiceApplication.java
├── domain/
│   ├── model/
│   │   ├── StoredDocument.java          # Aggregate root (manual Builder)
│   │   └── DocumentType.java            # INVOICE_PDF, INVOICE_XML, ATTACHMENT, OTHER
│   ├── event/
│   │   ├── DocumentStoredEvent.java     # Extends saga-commons IntegrationEvent
│   │   ├── ProcessDocumentStorageCommand.java
│   │   ├── CompensateDocumentStorageCommand.java
│   │   └── DocumentStorageReplyEvent.java  # Extends saga-commons SagaReply
│   └── service/
│       └── FileStorageProvider.java     # Storage abstraction
├── application/
│   ├── controller/
│   │   └── DocumentStorageController.java  # REST API
│   └── service/
│       ├── DocumentStorageService.java  # Storage orchestration
│       ├── PdfDownloadService.java      # HTTP PDF download
│       └── SagaCommandHandler.java      # Saga process + compensation
└── infrastructure/
    ├── persistence/
    │   ├── StoredDocumentEntity.java    # MongoDB entity
    │   ├── MongoDocumentRepository.java
    │   └── outbox/
    │       ├── OutboxEventEntity.java   # PostgreSQL JPA entity
    │       ├── SpringDataOutboxRepository.java
    │       └── JpaOutboxEventRepository.java
    ├── storage/
    │   ├── LocalFileStorageProvider.java
    │   └── S3FileStorageProvider.java
    ├── messaging/
    │   ├── EventPublisher.java          # Outbox-based event publishing
    │   └── SagaReplyPublisher.java      # Outbox-based saga replies
    └── config/
        ├── SagaRouteConfig.java         # Camel routes for saga
        └── OutboxConfig.java            # Outbox bean config

src/main/resources/
├── application.yml
└── db/migration/
    └── V1__create_outbox_events_table.sql
```

## Testing

### Running Unit Tests

```bash
# Run unit tests (no external dependencies required)
mvn test

# Run specific test class
mvn test -Dtest=SagaFlowIntegrationTest

# Run specific test method
mvn test -Dtest=SagaFlowIntegrationTest#shouldCreateDocumentAndOutboxEvent
```

### Running Integration Tests with External Containers

Integration tests require external services (PostgreSQL, MongoDB, Kafka). Follow these steps:

#### 1. Start External Containers

```bash
cd ../../docker
docker compose -f docker-compose.test.yml up -d
```

This starts:
- PostgreSQL on port 5433 (for outbox events)
- MongoDB on port 27018 (for document metadata)
- Kafka on port 9093
- Debezium on port 8083
- eIDAS Remote Signing on port 9000

Wait for all containers to be healthy:
```bash
docker ps --filter "name=test-"
```

#### 2. Run Integration Tests

```bash
# Run all integration tests
mvn test -Dtest="SagaFlowIntegrationTest,ExternalContainerSmokeTest"

# Run only Saga Flow tests
mvn test -Dtest=SagaFlowIntegrationTest

# Run only Smoke tests
mvn test -Dtest=ExternalContainerSmokeTest
```

#### 3. Stop Containers

```bash
cd ../../docker
docker compose -f docker-compose.test.yml down
```

### Test Configuration

Test configuration is in `src/test/resources/application-test.yml`:

| Property | Default | Description |
|----------|---------|-------------|
| `spring.datasource.url` | `jdbc:postgresql://localhost:5433/documentstorage_test` | PostgreSQL JDBC URL |
| `spring.data.mongodb.uri` | `mongodb://localhost:27018/document_storage_test` | MongoDB connection string |
| `kafka.bootstrap-servers` | `localhost:9093` | Kafka broker address |
| `app.storage.local.base-path` | `${java.io.tmpdir}/test-documents` | Local storage path for tests |
| `app.security.jwt.secret` | (test secret) | JWT secret for tests |

### Troubleshooting

**Tests fail with "Storage path is required":**
- Ensure `storagePath`, `fileSize`, and `checksum` are set in StoredDocument builders

**Tests fail with "JWT_SECRET not configured":**
- Test configuration includes JWT secret via `application-test.yml`

**Tests fail with "No property 'type' found":**
- Ensure Spring Data method names match entity fields (e.g., `existsByAggregateIdAndEventType`)

**Container startup fails:**
- Ensure Docker is running
- Check ports are not in use: `docker ps --filter "name=test-"`

## Monitoring

### Actuator Endpoints
- `/actuator/health` - Service health (MongoDB + PostgreSQL)
- `/actuator/info` - Service information
- `/actuator/metrics` - Application metrics
- `/actuator/camelroutes` - Camel route status

### Health Check
```bash
curl http://localhost:8084/actuator/health
```

## Related Services

- **Upstream**: Orchestrator Service (port 8093) - sends saga commands
- **Downstream**: Notification Service (port 8085) - consumes `document.stored` events
- **Dependency**: saga-commons library

## License

MIT License

## Contact

Maintained by wpanther (rabbit_roger@yahoo.com)
