# Docker Compose Setup

This directory contains Docker Compose configuration and initialization scripts for the Document Storage Service.

## Quick Start

### 1. Build the Application

```bash
# From the service root directory
mvn clean package
```

### 2. Start All Services

```bash
# Start core services (MongoDB, Kafka, Document Storage)
docker-compose up -d

# View logs
docker-compose logs -f document-storage-service
```

### 3. Verify Services

```bash
# Check service health
curl http://localhost:8084/actuator/health

# Check MongoDB
docker exec -it document-storage-mongodb mongosh -u admin -p admin123

# Check Kafka topics
docker exec -it document-storage-kafka kafka-topics --bootstrap-server localhost:9092 --list
```

## Environment Configuration

### Using .env File

Copy the example environment file and customize:

```bash
cp .env.example .env
```

Edit `.env` with your configuration:
- MongoDB credentials
- Storage provider (local or S3)
- AWS credentials (if using S3)
- Logging level

### Storage Providers

#### Local Filesystem (Default)

```bash
STORAGE_PROVIDER=local
```

Documents are stored in `/var/documents` inside the container, mounted to `./documents` on the host.

#### AWS S3

```bash
STORAGE_PROVIDER=s3
S3_BUCKET_NAME=your-bucket-name
AWS_REGION=us-east-1
AWS_ACCESS_KEY=your-access-key
AWS_SECRET_KEY=your-secret-key
```

## Service Profiles

### Production Mode (Default)

Runs core services only:
- Zookeeper
- Kafka
- MongoDB
- Document Storage Service

```bash
docker-compose up -d
```

### Debug Mode

Includes additional management tools:
- Kafka UI (http://localhost:8090)
- MongoDB Express (http://localhost:8081)

```bash
docker-compose --profile debug up -d
```

Login to MongoDB Express:
- Username: `admin` (or value from MONGO_EXPRESS_USERNAME)
- Password: `pass` (or value from MONGO_EXPRESS_PASSWORD)

## Common Commands

### Start Services

```bash
# Start all services
docker-compose up -d

# Start with debug tools
docker-compose --profile debug up -d

# Start specific service
docker-compose up -d document-storage-service
```

### Stop Services

```bash
# Stop all services
docker-compose down

# Stop and remove volumes (WARNING: deletes all data)
docker-compose down -v
```

### View Logs

```bash
# All services
docker-compose logs -f

# Specific service
docker-compose logs -f document-storage-service

# Last 100 lines
docker-compose logs --tail=100 -f document-storage-service
```

### Rebuild Services

```bash
# Rebuild after code changes
docker-compose build document-storage-service

# Rebuild and restart
docker-compose up -d --build document-storage-service
```

### Scale Services

```bash
# Scale to 3 instances
docker-compose up -d --scale document-storage-service=3
```

## Data Management

### Volumes

Data is persisted in Docker volumes:

| Volume | Purpose |
|--------|---------|
| `zookeeper_data` | Zookeeper data |
| `kafka_data` | Kafka topics and logs |
| `mongodb_data` | MongoDB database files |
| `document_storage` | Local document storage |

### Backup

```bash
# Backup MongoDB
docker exec document-storage-mongodb mongodump \
  --username=docuser \
  --password=docuser123 \
  --db=document_storage \
  --out=/tmp/backup

docker cp document-storage-mongodb:/tmp/backup ./backup-$(date +%Y%m%d)

# Backup documents (local storage)
docker run --rm -v document-storage-service_document_storage:/data \
  -v $(pwd)/documents-backup:/backup \
  alpine tar czf /backup/documents-$(date +%Y%m%d).tar.gz -C /data .
```

### Restore

```bash
# Restore MongoDB
docker cp ./backup-20251210 document-storage-mongodb:/tmp/backup
docker exec document-storage-mongodb mongorestore \
  --username=docuser \
  --password=docuser123 \
  --db=document_storage \
  /tmp/backup/document_storage

# Restore documents
docker run --rm -v document-storage-service_document_storage:/data \
  -v $(pwd)/documents-backup:/backup \
  alpine tar xzf /backup/documents-20251210.tar.gz -C /data
```

## Database Initialization

MongoDB is automatically initialized on first startup with:
- Database: `document_storage`
- User: `docuser` / `docuser123`
- Collection: `documents` with validation schema
- Indexes for optimized queries

Initialization script: `mongodb-init/01-init-user.js`

## Kafka Topics

The following topics are auto-created:

| Topic | Purpose |
|-------|---------|
| `pdf.generated` | Consumed by Document Storage (from PDF Generation Service) |
| `document.stored` | Published by Document Storage (future use) |

### Create Topics Manually

```bash
docker exec -it document-storage-kafka kafka-topics \
  --bootstrap-server localhost:9092 \
  --create \
  --topic pdf.generated \
  --partitions 3 \
  --replication-factor 1
```

### View Topic Messages

```bash
docker exec -it document-storage-kafka kafka-console-consumer \
  --bootstrap-server localhost:9092 \
  --topic pdf.generated \
  --from-beginning
```

## Testing

### Test Document Upload

```bash
# Create a test PDF
echo "Test PDF content" > test.pdf

# Upload document
curl -X POST http://localhost:8084/api/v1/documents \
  -F "file=@test.pdf" \
  -F "documentType=MANUAL_UPLOAD" \
  -F "invoiceId=test-invoice-123"

# Response: {"documentId": "...", "storageUrl": "...", ...}
```

### Test Document Download

```bash
# Download by ID (replace with actual document ID)
curl -O -J http://localhost:8084/api/v1/documents/{documentId}
```

### Test Health Endpoint

```bash
curl http://localhost:8084/actuator/health | jq
```

### Publish Test Kafka Event

```bash
# Create test event
cat > test-event.json <<EOF
{
  "eventId": "test-event-001",
  "invoiceId": "test-invoice-123",
  "invoiceNumber": "INV-2025-001",
  "documentId": "test-doc-001",
  "documentUrl": "http://example.com/test.pdf",
  "fileSize": 12345,
  "xmlEmbedded": true,
  "digitallySigned": false,
  "generatedAt": "2025-12-10T10:00:00Z"
}
EOF

# Publish to Kafka
docker exec -i document-storage-kafka kafka-console-producer \
  --bootstrap-server localhost:9092 \
  --topic pdf.generated < test-event.json
```

## Troubleshooting

### Service Won't Start

```bash
# Check logs
docker-compose logs document-storage-service

# Check dependencies
docker-compose ps

# Restart specific service
docker-compose restart document-storage-service
```

### MongoDB Connection Issues

```bash
# Verify MongoDB is running
docker exec -it document-storage-mongodb mongosh -u admin -p admin123

# Check user permissions
use document_storage
db.auth('docuser', 'docuser123')
db.documents.find().limit(1)
```

### Kafka Connection Issues

```bash
# Check Kafka broker
docker exec -it document-storage-kafka kafka-broker-api-versions \
  --bootstrap-server localhost:9092

# Check consumer group
docker exec -it document-storage-kafka kafka-consumer-groups \
  --bootstrap-server localhost:9092 \
  --group document-storage-service \
  --describe
```

### Permission Issues (Local Storage)

```bash
# Fix ownership of document storage volume
docker run --rm -v document-storage-service_document_storage:/data \
  alpine chown -R 1000:1000 /data
```

### Reset Everything

```bash
# Stop and remove all containers, networks, and volumes
docker-compose down -v

# Remove images
docker rmi document-storage-service:latest

# Start fresh
docker-compose up -d --build
```

## Network Configuration

Services communicate on the `document-storage-network` bridge network:

- **Subnet**: 172.25.0.0/16
- **Services**: All services can reach each other by service name
- **External Access**: Only exposed ports are accessible from host

### Port Mappings

| Service | Internal Port | External Port | Purpose |
|---------|--------------|---------------|---------|
| Document Storage | 8084 | 8084 | REST API |
| MongoDB | 27017 | 27017 | Database |
| Kafka | 9092 | 9092 | External clients |
| Kafka | 29092 | - | Inter-service communication |
| Zookeeper | 2181 | 2181 | Kafka coordination |
| Kafka UI | 8080 | 8090 | Web UI (debug mode) |
| MongoDB Express | 8081 | 8081 | Web UI (debug mode) |

## Performance Tuning

### JVM Heap Size

Adjust in `docker-compose.yml`:

```yaml
environment:
  JAVA_OPTS: -Xms1g -Xmx2g -XX:+UseG1GC
```

### MongoDB Connection Pool

Add to service environment:

```yaml
environment:
  SPRING_DATA_MONGODB_MAX_POOL_SIZE: 50
  SPRING_DATA_MONGODB_MIN_POOL_SIZE: 10
```

### Kafka Consumer Threads

```yaml
environment:
  SPRING_KAFKA_LISTENER_CONCURRENCY: 3
```

## Integration with Other Services

### Full Invoice Processing Pipeline

To run the complete pipeline:

1. Start all microservices using the main docker-compose in the repository root
2. Or connect this service to existing Kafka/MongoDB instances by setting environment variables

### Service Discovery (Eureka)

To enable Eureka integration:

```yaml
environment:
  EUREKA_ENABLED: true
  EUREKA_SERVER: http://eureka-server:8761/eureka/
```

Add Eureka server to `docker-compose.yml` or use external instance.

## Production Considerations

For production deployments:

1. **Use external MongoDB** (DocumentDB, MongoDB Atlas, or self-managed cluster)
2. **Use external Kafka** (Amazon MSK, Confluent Cloud, or self-managed cluster)
3. **Use S3 storage** instead of local filesystem
4. **Enable authentication** for MongoDB and Kafka
5. **Use secrets management** instead of plain environment variables
6. **Enable TLS/SSL** for all connections
7. **Set resource limits** in docker-compose
8. **Configure logging** to external aggregator
9. **Set up monitoring** (Prometheus, Grafana)
10. **Implement backup strategy**

See [DEPLOYMENT_GUIDE.md](../docs/DEPLOYMENT_GUIDE.md) for production deployment options (AWS, OpenShift).