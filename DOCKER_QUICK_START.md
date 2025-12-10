# Docker Quick Start Guide

Get the Document Storage Service running with Docker Compose in minutes.

## Prerequisites

- Docker 20.10+
- Docker Compose 2.0+
- 4GB RAM minimum
- 20GB disk space

## 1. Quick Start (3 Steps)

### Step 1: Build the Application

```bash
mvn clean package
```

### Step 2: Configure Environment (Optional)

```bash
# Copy example environment file
cp .env.example .env

# Edit .env with your settings (optional - defaults work fine)
nano .env
```

### Step 3: Start Services

```bash
# Start all services
docker-compose up -d

# View logs
docker-compose logs -f document-storage-service
```

✅ **Service is ready when you see:**
```
Started DocumentStorageServiceApplication in X.XXX seconds
```

## 2. Verify Installation

### Check Service Health

```bash
curl http://localhost:8084/actuator/health
```

**Expected Response:**
```json
{
  "status": "UP",
  "components": {
    "mongo": {"status": "UP"},
    "diskSpace": {"status": "UP"},
    "ping": {"status": "UP"}
  }
}
```

### Check All Services

```bash
docker-compose ps
```

**Expected Output:**
```
NAME                           STATUS    PORTS
document-storage-kafka         Up        0.0.0.0:9092->9092/tcp
document-storage-mongodb       Up        0.0.0.0:27017->27017/tcp
document-storage-service       Up        0.0.0.0:8084->8084/tcp
document-storage-zookeeper     Up        0.0.0.0:2181->2181/tcp
```

## 3. Test the Service

### Upload a Document

```bash
# Create a test PDF
echo "Test PDF content" > test-invoice.pdf

# Upload it
curl -X POST http://localhost:8084/api/v1/documents \
  -F "file=@test-invoice.pdf" \
  -F "documentType=MANUAL_UPLOAD" \
  -F "invoiceId=$(uuidgen)" \
  -F "invoiceNumber=INV-TEST-001" \
  | jq

# Save the documentId from response
export DOC_ID="<documentId-from-response>"
```

**Expected Response:**
```json
{
  "documentId": "550e8400-e29b-41d4-a716-446655440000",
  "fileName": "550e8400-e29b-41d4-a716-446655440000.pdf",
  "originalFileName": "test-invoice.pdf",
  "storageUrl": "http://localhost:8084/api/v1/documents/...",
  "fileSize": 123,
  "checksum": "abc123...",
  "createdAt": "2025-12-10T10:30:00Z"
}
```

### Download the Document

```bash
curl -O -J http://localhost:8084/api/v1/documents/$DOC_ID
```

### Get Document Metadata

```bash
curl http://localhost:8084/api/v1/documents/$DOC_ID/metadata | jq
```

### List Documents by Invoice

```bash
curl "http://localhost:8084/api/v1/documents/invoice/$(uuidgen)" | jq
```

## 4. Access Management Tools (Debug Mode)

Start with debug tools enabled:

```bash
docker-compose --profile debug up -d
```

### MongoDB Express (Database UI)

- **URL**: http://localhost:8081
- **Username**: `admin`
- **Password**: `pass`

### Kafka UI (Message Broker UI)

- **URL**: http://localhost:8090
- **Browse topics**: `pdf.generated`, `document.stored`

## 5. Common Operations

### View Logs

```bash
# All services
docker-compose logs -f

# Specific service
docker-compose logs -f document-storage-service

# Last 100 lines
docker-compose logs --tail=100 document-storage-service
```

### Restart Service

```bash
docker-compose restart document-storage-service
```

### Rebuild After Code Changes

```bash
# 1. Build new JAR
mvn clean package

# 2. Rebuild and restart
docker-compose up -d --build document-storage-service
```

### Stop Everything

```bash
# Stop services (keeps data)
docker-compose down

# Stop and remove all data
docker-compose down -v
```

## 6. Storage Options

### Local Filesystem (Default)

Documents stored in Docker volume `document_storage`:

```bash
# View stored documents
docker run --rm -v document-storage-service_document_storage:/data alpine ls -lR /data
```

### AWS S3 Storage

Edit `.env`:

```bash
STORAGE_PROVIDER=s3
S3_BUCKET_NAME=your-bucket-name
AWS_REGION=us-east-1
AWS_ACCESS_KEY=AKIAxxxxxxxx
AWS_SECRET_KEY=xxxxxxxxxxxxxxxx
```

Restart service:

```bash
docker-compose down
docker-compose up -d
```

## 7. Integration Testing

### Send Kafka Event (Simulate PDF Generation Service)

```bash
# Create test event
cat > pdf-event.json <<EOF
{
  "eventId": "evt-$(uuidgen)",
  "invoiceId": "inv-$(uuidgen)",
  "invoiceNumber": "INV-2025-001",
  "documentId": "doc-$(uuidgen)",
  "documentUrl": "http://example.com/sample.pdf",
  "fileSize": 12345,
  "xmlEmbedded": true,
  "digitallySigned": false,
  "generatedAt": "$(date -u +%Y-%m-%dT%H:%M:%SZ)"
}
EOF

# Publish to Kafka
docker exec -i document-storage-kafka kafka-console-producer \
  --bootstrap-server localhost:9092 \
  --topic pdf.generated < pdf-event.json

# Check service logs for processing
docker-compose logs -f document-storage-service
```

## 8. Monitoring

### Health Endpoints

```bash
# Overall health
curl http://localhost:8084/actuator/health

# Liveness (Kubernetes)
curl http://localhost:8084/actuator/health/liveness

# Readiness (Kubernetes)
curl http://localhost:8084/actuator/health/readiness
```

### Prometheus Metrics

```bash
curl http://localhost:8084/actuator/prometheus
```

### Application Info

```bash
curl http://localhost:8084/actuator/info | jq
```

## 9. Database Operations

### Connect to MongoDB

```bash
docker exec -it document-storage-mongodb mongosh -u admin -p admin123
```

Inside MongoDB shell:

```javascript
// Switch to database
use document_storage

// Authenticate as service user
db.auth('docuser', 'docuser123')

// List all documents
db.documents.find().pretty()

// Count documents
db.documents.countDocuments()

// Find by invoice ID
db.documents.find({invoiceId: "your-invoice-id"}).pretty()

// Check indexes
db.documents.getIndexes()
```

### Backup Database

```bash
# Create backup
docker exec document-storage-mongodb mongodump \
  --username=docuser \
  --password=docuser123 \
  --db=document_storage \
  --out=/tmp/backup

# Copy to host
docker cp document-storage-mongodb:/tmp/backup ./mongodb-backup-$(date +%Y%m%d)
```

### Restore Database

```bash
# Copy backup to container
docker cp ./mongodb-backup-20251210 document-storage-mongodb:/tmp/restore

# Restore
docker exec document-storage-mongodb mongorestore \
  --username=docuser \
  --password=docuser123 \
  --db=document_storage \
  /tmp/restore/document_storage
```

## 10. Kafka Operations

### List Topics

```bash
docker exec document-storage-kafka kafka-topics \
  --bootstrap-server localhost:9092 \
  --list
```

### View Messages in Topic

```bash
# From beginning
docker exec document-storage-kafka kafka-console-consumer \
  --bootstrap-server localhost:9092 \
  --topic pdf.generated \
  --from-beginning

# Latest messages only
docker exec document-storage-kafka kafka-console-consumer \
  --bootstrap-server localhost:9092 \
  --topic pdf.generated
```

### Check Consumer Group

```bash
docker exec document-storage-kafka kafka-consumer-groups \
  --bootstrap-server localhost:9092 \
  --group document-storage-service \
  --describe
```

## 11. Troubleshooting

### Service Won't Start

```bash
# Check logs
docker-compose logs document-storage-service

# Common issues:
# 1. MongoDB not ready - wait 30 seconds and retry
# 2. Port 8084 in use - stop other services or change port
# 3. Out of memory - increase Docker memory limit
```

### Can't Connect to MongoDB

```bash
# Verify MongoDB is running
docker exec document-storage-mongodb mongosh --eval "db.adminCommand('ping')"

# Check network
docker network inspect document-storage-service_document-storage-network
```

### Kafka Connection Timeout

```bash
# Check Kafka health
docker exec document-storage-kafka kafka-broker-api-versions \
  --bootstrap-server localhost:9092

# Restart Kafka
docker-compose restart kafka
```

### Reset Everything

```bash
# Nuclear option: remove everything and start fresh
docker-compose down -v
docker system prune -f
mvn clean package
docker-compose up -d --build
```

## 12. Performance Tuning

### Increase JVM Memory

Edit `docker-compose.yml`:

```yaml
environment:
  JAVA_OPTS: -Xms1g -Xmx2g -XX:+UseG1GC
```

### Scale Service

```bash
# Run 3 instances
docker-compose up -d --scale document-storage-service=3

# Note: Use load balancer for multiple instances
```

### Kafka Consumer Concurrency

Add to `.env`:

```bash
SPRING_KAFKA_LISTENER_CONCURRENCY=5
```

## 13. Development Workflow

### Typical Development Cycle

```bash
# 1. Make code changes
vim src/main/java/com/invoice/storage/...

# 2. Rebuild
mvn clean package

# 3. Update container
docker-compose up -d --build document-storage-service

# 4. View logs
docker-compose logs -f document-storage-service

# 5. Test changes
curl http://localhost:8084/actuator/health
```

### Hot Reload (Optional)

Use Spring Boot DevTools for faster iteration:

```bash
# Run locally instead of Docker
export MONGODB_HOST=localhost
export KAFKA_BROKERS=localhost:9092
mvn spring-boot:run
```

## 14. Production Checklist

Before deploying to production:

- [ ] Change default MongoDB passwords in `.env`
- [ ] Use AWS S3 or enterprise storage (not local filesystem)
- [ ] Enable MongoDB authentication and TLS
- [ ] Enable Kafka authentication (SASL/SSL)
- [ ] Set appropriate resource limits
- [ ] Configure external monitoring (Prometheus/Grafana)
- [ ] Set up log aggregation (ELK, CloudWatch)
- [ ] Configure backup strategy
- [ ] Review security groups/firewall rules
- [ ] Enable HTTPS/TLS for API endpoints
- [ ] Use secrets management (AWS Secrets Manager, Vault)

See [DEPLOYMENT_GUIDE.md](docs/DEPLOYMENT_GUIDE.md) for production deployment.

## 15. Additional Resources

- **Full Documentation**: [CLAUDE.md](CLAUDE.md)
- **Program Flow**: [docs/PROGRAM_FLOW.md](docs/PROGRAM_FLOW.md)
- **Deployment Guide**: [docs/DEPLOYMENT_GUIDE.md](docs/DEPLOYMENT_GUIDE.md)
- **Docker Details**: [docker/README.md](docker/README.md)

## 16. Getting Help

### Check Service Status

```bash
# Service health
curl http://localhost:8084/actuator/health | jq

# All containers
docker-compose ps

# Container logs
docker-compose logs --tail=50 document-storage-service
```

### Common Issues & Solutions

| Issue | Solution |
|-------|----------|
| Port 8084 already in use | Change port in `docker-compose.yml` or stop conflicting service |
| MongoDB connection refused | Wait 30s for MongoDB to start, check `docker-compose logs mongodb` |
| Kafka timeout | Restart Kafka: `docker-compose restart kafka zookeeper` |
| Out of disk space | Clean up: `docker system prune -a --volumes` |
| Permission denied | Run with sudo or add user to docker group |

---

**Quick Reference:**

```bash
# Start
docker-compose up -d

# Stop
docker-compose down

# Logs
docker-compose logs -f document-storage-service

# Rebuild
mvn clean package && docker-compose up -d --build

# Reset
docker-compose down -v

# Health
curl http://localhost:8084/actuator/health
```

Enjoy using the Document Storage Service! 🚀