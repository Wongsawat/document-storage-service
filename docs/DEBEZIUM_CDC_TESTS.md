# Debezium CDC Integration Testing Guide

**Document Storage Service - Full End-to-End CDC Pipeline Testing**

**Date:** 2026-03-09
**Version:** 1.0.0
**Service:** document-storage-service (Port 8084)

---

## Overview

This document describes the **full Debezium Change Data Capture (CDC) integration testing** for the document-storage-service. These tests validate the complete end-to-end pipeline from PostgreSQL outbox events through Debezium to Kafka consumers.

### CDC Pipeline Architecture

```
┌─────────────────────────────────────────────────────────────────────────────────────────────────────────────────┐
│                            COMPLETE DEBEZIUM CDC PIPELINE                                                      │
└─────────────────────────────────────────────────────────────────────────────────────────────────────────────────┘

1. POSTGRESQL OUTBOX WRITE
   Service Layer → @Transactional → PostgreSQL INSERT into outbox_events
                                                           ↓
2. DEBEZIUM CAPTURE
   PostgreSQL WAL → Debezium Connector (pgoutput) → Change Event
                                                           ↓
3. KAFKA PUBLISH
   Debezium → Kafka Connect → Kafka Topic: debezium-postgres-cdc.{db}.{schema}.{table}
                                                           ↓
4. CONSUMER PROCESSING
   Kafka Consumer → Deserialize → Validate Event → Downstream Service
```

### Key Components

| Component | Container | Purpose |
|-----------|-----------|---------|
| **PostgreSQL** | `postgres:16-alpine` | Source database with outbox_events table |
| **Debezium Connect** | `debezium/connect:2.5.4.Final` | CDC connector that captures WAL changes |
| **Kafka** | `confluentinc/cp-kafka:7.5.0` | Message broker for CDC events |
| **MongoDB** | `mongo:7` | Document metadata storage (service data) |
| **Test Consumer** | Java KafkaConsumer | Validates events received from Kafka |

---

## Test Structure

### Test Classes

#### 1. SagaFlowIntegrationTest (CDC Foundation)

**Location:** `src/test/java/com/wpanther/storage/integration/SagaFlowIntegrationTest.java`

**Purpose:** Tests the foundation of the outbox pattern without Debezium

**Tests:**
- ✅ Document and outbox event created transactionally
- ✅ Orphaned document detection
- ✅ Outbox repository reconciliation methods
- ✅ Document repository reconciliation queries
- ✅ Compensating events for orphaned documents

**Runtime:** ~1 minute
**Docker Required:** Yes (Kafka, PostgreSQL, MongoDB)
**Debezium:** No

#### 2. DebeziumCdcIntegrationTest (Full CDC Pipeline)

**Location:** `src/test/java/com/wpanther/storage/integration/DebeziumCdcIntegrationTest.java`

**Purpose:** Tests the complete end-to-end Debezium CDC pipeline

**Tests:**
- ✅ Capture outbox event with Debezium CDC pipeline
- ✅ Capture multiple outbox events in correct order
- ✅ Handle event status updates through CDC
- ✅ Include complete event metadata in CDC payload

**Runtime:** ~3-5 minutes
**Docker Required:** Yes (Kafka, PostgreSQL, MongoDB, Debezium Connect)
**Debezium:** Yes

---

## Running the Tests

### Prerequisites

1. **Docker or Podman** must be running:
   ```bash
   # Verify Docker is available
   docker ps

   # For Podman, set environment variable
   export DOCKER_HOST="unix:///run/user/$(id -u)/podman/podman.sock"
   ```

2. **Available Ports:**
   - `5432` - PostgreSQL
   - `9092` - Kafka
   - `27017` - MongoDB
   - `8083` - Debezium Connect

### Run CDC Foundation Tests (No Debezium)

```bash
# Run only SagaFlowIntegrationTest
mvn test -Dtest=SagaFlowIntegrationTest

# Run with verbose logging
mvn test -Dtest=SagaFlowIntegrationTest -Dlogging.level.com.wpanther.storage=DEBUG
```

### Run Full Debezium CDC Tests

```bash
# Run only DebeziumCdcIntegrationTest
mvn test -Dtest=DebeziumCdcIntegrationTest

# Run with verbose logging
mvn test -Dtest=DebeziumCdcIntegrationTest -Dlogging.level.com.wpanther.storage=DEBUG

# Run all integration tests
mvn test -Dtest="*IntegrationTest" -DfailIfNoTests=false
```

### Run Specific Test Method

```bash
# Run single test method
mvn test -Dtest=DebeziumCdcIntegrationTest#shouldCaptureOutboxEventWithDebezium
```

---

## Debezium Connector Configuration

### Connector Settings

The tests register a Debezium PostgreSQL connector with the following configuration:

```json
{
  "name": "postgres-connector",
  "config": {
    "connector.class": "io.debezium.connector.postgresql.PostgresConnector",
    "database.hostname": "postgres",
    "database.port": 5432,
    "database.user": "test",
    "database.password": "test",
    "database.dbname": "documentstorage_test",
    "topic.prefix": "debezium-postgres-cdc",
    "plugin.name": "pgoutput",
    "table.include.list": "public.outbox_events",
    "slot.name": "debezium_slot",
    "publication.name": "debezium_publication",
    "transforms": "unwrap",
    "transforms.unwrap.type": "io.debezium.transforms.ExtractNewRecordState",
    "transforms.unwrap.drop.tombstones": "true"
  }
}
```

### Key Configuration Explained

| Setting | Value | Purpose |
|---------|-------|---------|
| `plugin.name` | `pgoutput` | Use PostgreSQL's logical decoding |
| `table.include.list` | `public.outbox_events` | Only capture outbox_events table |
| `slot.name` | `debezium_slot` | Replication slot name |
| `topic.prefix` | `debezium-postgres-cdc` | Prefix for Kafka topics |
| `transforms.unwrap` | ExtractNewRecordState | Flatten Debezium envelope |
| `drop.tombstones` | `true` | Don't emit null for deletes |

### Kafka Topic Naming

Events are published to:
```
debezium-postgres-cdc.documentstorage_test.public.outbox_events
```

**Format:** `{prefix}.{database}.{schema}.{table}`

---

## CDC Event Format

### Debezium Event Structure

```json
{
  "schema": {
    "type": "struct",
    "fields": [...]
  },
  "payload": {
    "before": null,
    "after": {
      "id": "3e0f51a6-...",
      "aggregate_type": "StoredDocument",
      "aggregate_id": "doc-123",
      "event_type": "DocumentStoredEvent",
      "payload": "{\"documentId\":\"doc-123\"}",
      "created_at": "1699876800",
      "published_at": null,
      "status": "PENDING",
      "retry_count": 0,
      "topic": "document.stored",
      "partition_key": "doc-123",
      "headers": null,
      "error_message": null
    },
    "op": "c",           // c=create, u=update, d=delete
    "ts_ms": 1699876800123
  }
}
```

### Event Types (op field)

| Operation | Code | Description |
|-----------|------|-------------|
| Create | `c` | New row inserted |
| Update | `u` | Existing row updated |
| Delete | `d` | Row deleted |
| Read | `r` | Initial snapshot (if enabled) |

---

## Test Scenarios

### Scenario 1: Single Outbox Event

**Test:** `shouldCaptureOutboxEventWithDebezium`

**Flow:**
1. Create `OutboxEventEntity` with PENDING status
2. Save to PostgreSQL via `outboxRepository.save()`
3. Debezium captures INSERT from WAL
4. Debezium publishes to Kafka topic
5. Test consumer receives event
6. Validate event structure and payload

**Assertions:**
- ✅ Event received within 30 seconds
- ✅ `aggregate_id` matches document ID
- ✅ `event_type` is "DocumentStoredEvent"
- ✅ `status` is "PENDING"
- ✅ Debezium envelope structure valid

### Scenario 2: Multiple Events in Order

**Test:** `shouldCaptureMultipleEventsInCorrectOrder`

**Flow:**
1. Create 3 outbox events with different document IDs
2. Save in sequence (event1, event2, event3)
3. Consumer receives all events
4. Validate order preserved

**Assertions:**
- ✅ All 3 events received
- ✅ Events in same order as inserted
- ✅ No duplicate events
- ✅ No missing events

### Scenario 3: Event Status Updates

**Test:** `shouldHandleEventStatusUpdates`

**Flow:**
1. Create event with PENDING status
2. Wait for initial CDC event
3. Update status to PUBLISHED
4. Consumer receives update event
5. Validate new status captured

**Assertions:**
- ✅ Update event received
- ✅ `status` changed to "PUBLISHED"
- ✅ `published_at` timestamp set
- ✅ `op` field is "u" (update)

### Scenario 4: Complete Event Metadata

**Test:** `shouldIncludeCompleteEventMetadata`

**Flow:**
1. Create event with all fields populated
2. Consumer receives event
3. Validate all metadata present

**Assertions:**
- ✅ `aggregate_id` present
- ✅ `aggregate_type` present
- ✅ `event_type` present
- ✅ `status` present
- ✅ `topic` present
- ✅ `partition_key` present
- ✅ `retry_count` present
- ✅ `created_at` present
- ✅ `payload` present

---

## Troubleshooting

### Test Failures

#### Docker Not Available

**Error:**
```
Could not find a valid Docker environment
```

**Solution:**
```bash
# Start Docker
sudo systemctl start docker

# Or for Podman
export DOCKER_HOST="unix:///run/user/$(id -u)/podman/podman.sock"
```

#### Debezium Container Timeout

**Error:**
```
Waiting for Debezium Connect to start...
```

**Solution:**
- Increase startup timeout in test
- Check Docker logs: `docker logs <container-id>`
- Verify network connectivity between containers

#### Kafka Consumer Not Receiving Events

**Error:**
```
Timeout waiting for events from Kafka
```

**Solution:**
```bash
# Verify Kafka topic exists
docker exec <kafka-container> kafka-topics --bootstrap-server localhost:9092 --list

# Check topic messages
docker exec <kafka-container> kafka-console-consumer \
  --bootstrap-server localhost:9092 \
  --topic debezium-postgres-cdc.documentstorage_test.public.outbox_events \
  --from-beginning

# Check Debezium connector status
curl http://localhost:8083/connectors/postgres-connector/status
```

#### Connector Registration Fails

**Error:**
```
Failed to register Debezium connector: 500
```

**Solution:**
- Check PostgreSQL is accessible from Debezium container
- Verify PostgreSQL has `wal_level=logical`
- Check replication slot doesn't already exist:
  ```sql
  SELECT * FROM pg_replication_slots;
  ```

---

## Performance Considerations

### Test Execution Time

| Test | Typical Duration | Bottleneck |
|------|-----------------|------------|
| SagaFlowIntegrationTest | 1-2 min | Container startup |
| DebeziumCdcIntegrationTest | 3-5 min | Debezium initialization, CDC latency |

### Optimization Tips

1. **Parallel Execution:** Tests run sequentially due to shared containers
2. **Container Reuse:** Containers created once per test class
3. **Network:** All containers use same network for faster communication
4. **Polling Interval:** 1 second balance between responsiveness and CPU

### Resource Usage

- **Memory:** ~2-3 GB for all containers
- **CPU:** Moderate during CDC capture
- **Disk:** ~500 MB for Docker images

---

## CI/CD Integration

### GitHub Actions Example

```yaml
name: CDC Integration Tests

on: [push, pull_request]

jobs:
  cdc-tests:
    runs-on: ubuntu-latest

    services:
      docker:
        image: docker:24-dind
        options: --privileged

    steps:
      - uses: actions/checkout@v3

      - name: Set up JDK 21
        uses: actions/setup-java@v3
        with:
          java-version: '21'
          distribution: 'temurin'

      - name: Run CDC Integration Tests
        run: mvn test -Dtest=DebeziumCdcIntegrationTest
        env:
          DOCKER_HOST: unix:///run/user/1000/docker.sock
```

### Jenkins Pipeline Example

```groovy
pipeline {
    agent any

    stages {
        stage('CDC Tests') {
            steps {
                sh 'mvn test -Dtest=DebeziumCdcIntegrationTest'
            }
            post {
                always {
                    junit '**/target/surefire-reports/*.xml'
                }
            }
        }
    }
}
```

---

## Production Deployment Considerations

### Debezium in Production

When deploying Debezium in production:

1. **High Availability:**
   - Deploy multiple Kafka Connect workers
   - Configure `offset.storage.topic` with replication factor > 1
   - Use `group.id` for connector failover

2. **Monitoring:**
   - Monitor Debezium connector metrics
   - Track lag in source transactions
   - Alert on connector failures

3. **Schema Evolution:**
   - Use Avro or Protobuf schemas with Schema Registry
   - Enable `schema.name.adaptation` for backward compatibility

4. **Performance:**
   - Tune `snapshot.fetch.size` for initial load
   - Adjust `max.batch.size` and `max.poll.records`
   - Use `database.response.timeout` for slow queries

### Configuration Differences

| Setting | Test | Production |
|---------|-------|------------|
| `snapshot.mode` | `schema_only` | `initial` or `when_needed` |
| `slot.drop.on.stop` | `false` | `false` (preserve slot) |
| `tombstones.on.delete` | `false` | `true` (for compaction) |
| `provide.transaction.metadata` | `false` | `true` (for ordering) |

---

## Related Documentation

- [Transactional Outbox Pattern](../ARCHITECTURE_REVIEW.md#transactional-outbox-pattern)
- [Custom Metrics](CUSTOM_METRICS.md)
- [Saga Orchestration](../CLAUDE.md#saga-orchration-pattern)
- [Debezium PostgreSQL Connector](https://debezium.io/documentation/connectors/postgresql/)

---

## References

- [Debezium Documentation](https://debezium.io/documentation/)
- [Testcontainers Documentation](https://www.testcontainers.org/)
- [Kafka Consumer Documentation](https://kafka.apache.org/documentation/#consumers)
- [PostgreSQL Logical Decoding](https://www.postgresql.org/docs/current/logicaldecoding.html)
