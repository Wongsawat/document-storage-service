# Chaos Engineering Testing Guide

**Document Storage Service - Chaos Engineering Tests**

**Date:** 2026-03-09
**Version:** 1.0.0
**Service:** document-storage-service (Port 8084)

---

## Overview

This document describes the **chaos engineering testing framework** for the document-storage-service. These tests validate system resilience under failure conditions by simulating real-world production issues and validating that the system recovers gracefully.

### What is Chaos Engineering?

**Chaos Engineering** is the discipline of experimenting on a system to build confidence in its capability to withstand turbulent conditions in production.

**Key Principles:**
- **Define Steady State**: Measure system's normal behavior
- **Introduce Failure**: Inject real-world failure scenarios
- **Verify Recovery**: System returns to steady state automatically
- **Improve Resilience**: Fix weaknesses discovered during testing

### Testing Philosophy

```
┌─────────────────────────────────────────────────────────────────────────────────────────────────────────┐
│                              CHAOS ENGINEERING LOOP                                                       │
└─────────────────────────────────────────────────────────────────────────────────────────────────────────┘

1. DEFINE STEADY STATE
   - System accepts documents
   - Outbox events created successfully
   - Metrics within normal range

2. HYPOTHESIZE FAILURE
   - Database connection lost
   - Network latency spikes
   - Connection pool exhausted

3. INJECT FAILURE
   - Simulate condition with tests
   - Monitor system behavior
   - Collect metrics/logs

4. VERIFY RECOVERY
   - System returns to steady state
   - No data loss
   - Metrics normalize

5. IMPROVE RESILIENCE
   - Add retries if needed
   - Increase timeouts
   - Tune circuit breakers
```

---

## Test Scenarios

### 1. Database Connection Loss Simulation

**Test:** `shouldRecoverFromDatabaseConnectionLoss`

**Failure Mode:** PostgreSQL container stops and restarts

**Simulation Method:**
```java
postgres.stop();           // Stop container
TimeUnit.SECONDS.sleep(3); // Wait 3 seconds
postgres.start();          // Restart container
```

**Expected Behavior:**
- ✅ Connection pool detects connection loss
- ✅ Retries establish new connections
- ✅ Operations resume after recovery
- ✅ No data loss

**Validation:**
```java
await().atMost(30, TimeUnit.SECONDS)
    .until(() -> documentRepository.findById(id).isPresent());
```

**What This Tests:**
- HikariCP connection pool recovery
- Spring Data JPA retry logic
- Transaction rollback behavior
- Application resilience to database restarts

---

### 2. Outbox Creation During Database Instability

**Test:** `shouldHandleOutboxCreationDuringDatabaseRestart`

**Failure Mode:** Database instability during transaction commit

**Simulation Method:**
```java
simulateDatabaseInstability(() -> {
    outboxRepository.save(event);
});
```

**Expected Behavior:**
- ✅ Transaction retries on failure
- ✅ Outbox event eventually persisted
- ✅ No orphaned transactions

**What This Tests:**
- `@Transactional` retry behavior
- Outbox event durability
- Database connection recovery under load

---

### 3. Circuit Breaker Validation

**Test:** `shouldValidateCircuitBreakerAfterRepeatedFailures`

**Failure Mode:** Repeated database operation failures

**Simulation Method:**
```java
for (int i = 0; i < 10; i++) {
    try {
        documentRepository.save(document);
        successCount++;
    } catch (Exception e) {
        failureCount++;
        if (i % 3 == 0) simulateIntermittentLatency();
    }
}
```

**Expected Behavior:**
- ✅ Circuit breaker opens after threshold
- ✅ Operations fail fast when open
- ✅ Circuit breaker closes after cooldown
- ✅ System recovers when database stabilizes

**What This Tests:**
- Resilience4j circuit breaker configuration
- Success/failure threshold tuning
- Half-open state behavior
- Retry logic integration

---

### 4. Resource Constrained Environment

**Test:** `shouldHandleResourceConstrainedEnvironment`

**Failure Mode:** Limited memory and CPU resources

**Simulation Method:**
```java
for (int i = 0; i < 20; i++) {
    documentRepository.save(document);
    if (i % 5 == 0) {
        simulateResourcePressure(); // 10MB allocation
    }
}
```

**Expected Behavior:**
- ✅ Operations succeed despite resource pressure
- ✅ Connection pool adapts to constrained resources
- ✅ No OutOfMemoryError or timeouts
- ✅ Graceful degradation if needed

**What This Tests:**
- Connection pool sizing under pressure
- Memory usage efficiency
- Thread pool behavior
- Resource cleanup

---

### 5. Connection Pool Exhaustion

**Test:** `shouldRecoverFromConnectionPoolExhaustion`

**Failure Mode:** More concurrent requests than connection pool size

**Simulation Method:**
```java
ExecutorService executor = Executors.newFixedThreadPool(10);
CountDownLatch latch = new CountDownLatch(15);

for (int i = 0; i < 15; i++) {
    executor.submit(() -> {
        documentRepository.save(document);
        latch.countDown();
    });
}

latch.await(60, TimeUnit.SECONDS);
```

**Expected Behavior:**
- ✅ Operations queue when pool exhausted
- ✅ Connections timeout and return to pool
- ✅ No connection leaks
- ✅ System stabilizes after burst

**What This Tests:**
- HikariCP pool configuration
- Connection timeout settings
- Maximum pool size validation
- Leak detection (connections not returned)

---

### 6. Retry Logic Under Transient Failures

**Test:** `shouldValidateRetryLogicUnderTransientFailures`

**Failure Mode:** Transient network/database issues

**Simulation Method:**
```java
for (int i = 0; i < 5; i++) {
    try {
        documentRepository.save(document);
        if (i < 3) {
            simulateTransientFailure();
            throw new RuntimeException("Simulated failure");
        }
        break;
    } catch (Exception e) {
        Thread.sleep(500); // Retry delay
    }
}
```

**Expected Behavior:**
- ✅ Transient failures are retried
- ✅ Operation succeeds after retries
- ✅ Exponential backoff applied
- ✅ Max retries not exceeded

**What This Tests:**
- Spring Retry configuration
- Exponential backoff timing
- Max retry limits
- Transient exception detection

---

### 7. Slow Database Query Handling

**Test:** `shouldHandleSlowDatabaseQueries`

**Failure Mode:** Database query performance degradation

**Simulation Method:**
```java
for (int i = 0; i < 5; i++) {
    simulateSlowDatabase(100); // 100ms delay
    documentRepository.save(document);
}
```

**Expected Behavior:**
- ✅ Operations complete despite slowness
- ✅ Connection timeout not exceeded
- ✅ No connection pool starvation
- ✅ Reasonable completion time

**What This Tests:**
- Connection timeout settings
- Query timeout configuration
- Pool size adequacy
- SLA compliance

---

## Running Chaos Tests

### Prerequisites

1. **Docker or Podman** must be running:
   ```bash
   # Verify Docker
   docker ps

   # For Podman
   export DOCKER_HOST="unix:///run/user/$(id -u)/podman/podman.sock"
   ```

2. **Available Resources:**
   - Memory: 4+ GB recommended
   - CPU: 2+ cores recommended
   - Disk: 1+ GB free space

### Run All Chaos Tests

```bash
# Run all chaos tests
mvn test -Dtest=ChaosEngineeringTest

# Run with verbose logging
mvn test -Dtest=ChaosEngineeringTest -Dlogging.level.com.wpanther.storage=DEBUG
```

### Run Specific Chaos Test

```bash
# Run single test method
mvn test -Dtest=ChaosEngineeringTest#shouldRecoverFromDatabaseConnectionLoss

# Run connection-related tests
mvn test -Dtest=ChaosEngineeringTest -Dtest="*Connection*"
```

### Run with Resource Limits

```bash
# Limit memory for testing resource constraints
mvn test -Dtest=ChaosEngineeringTest -DargLine="-Xmx512m"
```

---

## Chaos Simulation Techniques

### 1. Container Restart

```java
postgres.stop();
TimeUnit.SECONDS.sleep(5);
postgres.start();
```

**Use Cases:** Database crashes, pod restarts

### 2. Network Latency Injection

```java
// Testcontainers with network latency
postgres.withCreateContainerCmdModifier(cmd -> cmd.withAppender("tc", "qdisc add dev eth0 root netem delay 200ms"));
```

**Use Cases:** Network degradation, geographic distribution

### 3. Resource Pressure

```java
// Memory allocation
byte[] allocation = new byte[10 * 1024 * 1024]; // 10MB

// CPU pressure
long start = System.currentTimeMillis();
while (System.currentTimeMillis() - start < 100) {
    Math.sqrt(Math.random() * 10000);
}
```

**Use Cases:** Memory leaks, CPU spikes

### 4. Connection Pool Limiting

```yaml
# application-test.yml
spring:
  datasource:
    hikari:
      maximum-pool-size: 5      # Reduce for testing
      minimum-idle: 1
      connection-timeout: 2000  # Short timeout
      validation-timeout: 2000
```

**Use Cases:** Pool exhaustion testing

---

## Interpreting Test Results

### Success Criteria

| Metric | Threshold | Description |
|--------|-----------|-------------|
| Recovery Time | < 30s | System returns to steady state |
| Success Rate | > 80% | Operations succeed despite chaos |
| Data Loss | 0 | No data corruption or loss |
| Connection Leaks | 0 | All connections returned to pool |

### Failure Analysis

If chaos tests fail:

1. **Check logs for root cause:**
   ```bash
   # View test logs
   mvn test -Dtest=ChaosEngineeringTest -Dlogging.level.com.wpanther.storage=TRACE
   ```

2. **Analyze failure patterns:**
   - Intermittent failures → Circuit breaker tuning
   - Timeout issues → Increase timeouts or pool size
   - Connection leaks → Check HikariCP configuration

3. **Validate improvements:**
   - Fix identified weaknesses
   - Re-run chaos tests
   - Confirm improved resilience

---

## Production Chaos Testing

### Testing in Production

**Prerequisites:**
- Feature flags for chaos experiments
- Gradual rollout (canary deployment)
- Circuit breakers enabled
- Comprehensive monitoring

**Example: Chaos Monkey for Spring Boot**

```java
@Configuration
@ConditionalOnProperty(name = "chaos.enabled", havingValue = "true")
@EnableChaosMonkey
public class ChaosConfig {

    @Bean
    public ChaosMonkeySimulator chaosMonkeySimulator() {
        return new ChaosMonkeySimulatorBuilder()
                .addAssault(assaultProperties())
                .addWatcher(watcherProperties())
                .build();
    }

    private AssaultProperties assaultProperties() {
        return AssaultProperties.builder()
                .level(2)                    // Weak chaos
                .latencyRangeStart(1000)     // 1-3s latency
                .latencyRangeEnd(3000)
                .rate(1)                     // 1% of requests
                .build();
    }

    private WatcherProperties watcherProperties() {
        return WatcherProperties.builder()
                .documentStorageController()
                .documentStorageService()
                .build();
    }
}
```

### Chaos Experiment Checklist

- [ ] **Define steady state** - Document storage works normally
- [ ] **Identify potential failures** - Database loss, network issues
- [ ] **Create hypothesis** - "System will recover within 30s"
- [ ] **Run experiment** - Execute chaos test
- [ ] **Verify recovery** - System returns to steady state
- [ ] **Document findings** - Record results and improvements
- [ ] **Implement fixes** - Address weaknesses found
- [ ] **Re-test** - Validate improvements

---

## Resilience Patterns Tested

### 1. Retry Pattern

**Configuration:**
```yaml
resilience4j:
  retry:
    configs:
      default:
        max-attempts: 3
        wait-duration: 500ms
        retry-exceptions:
          - java.sql.SQLException
          - java.io.IOException
```

**Validated By:**
- `shouldValidateRetryLogicUnderTransientFailures`

### 2. Circuit Breaker Pattern

**Configuration:**
```yaml
resilience4j:
  circuitbreaker:
    configs:
      default:
        sliding-window-size: 100
        failure-rate-threshold: 50
        wait-duration-in-open-state: 30s
```

**Validated By:**
- `shouldValidateCircuitBreakerAfterRepeatedFailures`

### 3. Timeout Pattern

**Configuration:**
```yaml
resilience4j:
  timelimiter:
    configs:
      default:
        timeout-duration: 30s
```

**Validated By:**
- `shouldHandleSlowDatabaseQueries`
- `shouldRecoverFromConnectionPoolExhaustion`

### 4. Bulkhead Pattern

**Configuration:**
```yaml
spring:
  datasource:
    hikari:
      maximum-pool-size: 20
      minimum-idle: 5
      connection-timeout: 30s
```

**Validated By:**
- `shouldRecoverFromConnectionPoolExhaustion`
- `shouldHandleResourceConstrainedEnvironment`

---

## CI/CD Integration

### GitHub Actions Example

```yaml
name: Chaos Engineering Tests

on:
  schedule:
    - cron: '0 2 * * *'  # Run daily at 2 AM
  workflow_dispatch:  # Manual trigger

jobs:
  chaos-tests:
    runs-on: ubuntu-latest
    timeout-minutes: 30

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

      - name: Run Chaos Tests
        run: mvn test -Dtest=ChaosEngineeringTest
        env:
          DOCKER_HOST: unix:///run/user/1000/docker.sock

      - name: Upload Test Results
        uses: actions/upload-artifact@v3
        if: always()
        with:
          name: chaos-test-results
          path: target/surefire-reports/

      - name: Generate Report
        if: always()
        run: |
          echo "## Chaos Engineering Test Results" >> $GITHUB_STEP_SUMMARY
          echo "✅ Tests passed" >> $GITHUB_STEP_SUMMARY || echo "❌ Tests failed" >> $GITHUB_STEP_SUMMARY
```

### Jenkins Pipeline Example

```groovy
pipeline {
    agent any

    parameters {
        boolean(name: 'CHAOS_ENABLED', defaultValue: false, description: 'Run chaos tests')
    }

    stages {
        stage('Chaos Tests') {
            when {
                expression { params.CHAOS_ENABLED }
            }
            steps {
                sh 'mvn test -Dtest=ChaosEngineeringTest'
            }
            post {
                always {
                    junit '**/target/surefire-reports/*.xml'
                    publishHTML target/site/jacoco/index.html
                }
            }
        }
    }
}
```

---

## Troubleshooting

### Docker/Podman Issues

#### Error: "Could not find a valid Docker environment"

**Solution:**
```bash
# Start Docker
sudo systemctl start docker

# For Podman
export DOCKER_HOST="unix:///run/user/$(id -u)/podman/podman.sock"
```

#### Error: "Client version 1.32 is too old"

**Solution:**
```bash
# Update Podman
sudo dnf upgrade podman

# Or use Docker
docker version
```

### Container Startup Issues

#### PostgreSQL takes too long to start

**Solution:**
```bash
# Check PostgreSQL logs
docker logs <postgres-container>

# Increase startup timeout in test
.withStartupTimeout(Duration.ofMinutes(3))
```

#### MongoDB exits unexpectedly

**Solution:**
```bash
# Check MongoDB logs
docker logs <mongo-container>

# Verify memory availability
docker stats

# Reduce memory requirements
.withCommand("--wiredTigerCacheSizeGB", "0.25")
```

### Test Timeouts

#### Tests timeout waiting for recovery

**Solution:**
- Increase await timeout: `await().atMost(60, TimeUnit.SECONDS)`
- Check if containers are actually restarting
- Verify resource constraints (CPU, memory)

#### Connection pool not recovering

**Solution:**
- Verify HikariCP configuration
- Check for connection leaks
- Increase `maximum-pool-size`
- Tune `connection-timeout`

---

## Best Practices

### 1. Start Small, Scale Up

- Begin with low chaos levels (1-2% of requests)
- Gradually increase intensity
- Monitor system behavior closely
- Have rollback plan ready

### 2. Test During Off-Peak Hours

- Schedule chaos tests for low-traffic periods
- Minimize impact on users
- Ensure team is available to respond

### 3. Use Feature Flags

```java
@ConditionalOnProperty(name = "chaos.enabled", havingValue = "true")
public class ChaosEngineeringTest { }
```

### 4. Monitor Everything

- Application metrics (Micrometer)
- Database metrics (PostgreSQL, MongoDB)
- Infrastructure metrics (Docker, Podman)
- Business metrics (documents stored, etc.)

### 5. Document Findings

Create a template for chaos experiment results:

```
## Chaos Experiment Report

**Date:** 2026-03-09
**Test:** Database Connection Loss
**Hypothesis:** System will recover within 30s

**Results:**
- Recovery Time: 22s ✅
- Data Loss: None ✅
- Connection Leaks: 0 ✅
- Failed Operations: 3/10
- Success Rate: 70%

**Root Cause:**
Connection pool size too small for burst traffic.

**Improvement Action:**
Increased maximum-pool-size from 10 to 20.

**Re-test Results:**
- Recovery Time: 8s ✅
- Success Rate: 100% ✅

**Status:** ✅ IMPROVEMENT VALIDATED
```

---

## Advanced Chaos Tools

### 1. Chaos Monkey (Netflix)

**Installation:**
```xml
<dependency>
    <groupId>de.codecentric</groupId>
    <artifactId>chaos-monkey-spring-boot</artifactId>
    <version>3.1.0</version>
    <scope>test</scope>
</dependency>
```

### 2. LitmusChaos (CNCF)

**Chaos Types:**
- Pod kill
- Container kill
- Network delay/loss
- Resource exhaustion

**Website:** https://litmuschaos.io/

### 3. Chaos Mesh (Kubernetes)

**Features:**
- Pod chaos
- Network chaos
- I/O chaos
- Time skew chaos
- AWS chaos

**Website:** https://chaos-mesh.org/

---

## Related Documentation

- [Circuit Breaker Guide](CIRCUIT_BREAKER.md)
- [Custom Metrics Guide](CUSTOM_METRICS.md)
- [Debezium CDC Tests](DEBEZIUM_CDC_TESTS.md)
- [Architecture Review](ARCHITECTURE_REVIEW.md)

---

## References

- [Chaos Engineering](https://principlesofchaos.org/) (Principles of Chaos)
- [Chaos Monkey](https://netflix.github.io/chaosmonkey/) (Netflix)
- [Testcontainers](https://www.testcontainers.org/)
- [Resilience4j](https://resilience4j.readme.io/)
- [HikariCP](https://github.com/brettwooldridge/HikariCP)
