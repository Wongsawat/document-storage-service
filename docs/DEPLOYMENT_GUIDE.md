# Deployment Guide

This guide covers deployment of the Document Storage Service to Docker, AWS, and Red Hat OpenShift.

## Table of Contents
- [Prerequisites](#prerequisites)
- [Docker Deployment](#docker-deployment)
- [AWS Deployment](#aws-deployment)
- [Red Hat OpenShift Deployment](#red-hat-openshift-deployment)
- [Configuration Reference](#configuration-reference)
- [Monitoring and Health Checks](#monitoring-and-health-checks)
- [Troubleshooting](#troubleshooting)

---

## Prerequisites

### Build Requirements
- Java 21+
- Maven 3.6+
- Docker (for containerized deployments)

### Runtime Dependencies
- MongoDB 4.4+ (metadata storage)
- Apache Kafka 3.0+ (event streaming)
- AWS S3 or S3-compatible storage (optional, for production)
- Eureka Server (optional, for service discovery)

### External Services
- PDF Generation Service (publishes to `pdf.generated` topic)
- Other microservices in the invoice processing pipeline

---

## Docker Deployment

### 1. Building the Docker Image

#### Using Maven Plugin
```bash
cd /path/to/document-storage-service
mvn clean package
docker build -t document-storage-service:1.0.0 .
```

#### Multi-Architecture Build
```bash
docker buildx build --platform linux/amd64,linux/arm64 \
  -t document-storage-service:1.0.0 \
  --push .
```

### 2. Running Standalone Container

#### Local Filesystem Storage
```bash
docker run -d \
  --name document-storage \
  -p 8084:8084 \
  -e MONGODB_HOST=mongodb \
  -e MONGODB_PORT=27017 \
  -e MONGODB_DATABASE=document_storage \
  -e KAFKA_BROKERS=kafka:29092 \
  -e STORAGE_PROVIDER=local \
  -e LOCAL_STORAGE_PATH=/var/documents \
  -v /host/path/to/documents:/var/documents \
  --network invoice-network \
  document-storage-service:1.0.0
```

#### AWS S3 Storage
```bash
docker run -d \
  --name document-storage \
  -p 8084:8084 \
  -e MONGODB_HOST=mongodb \
  -e KAFKA_BROKERS=kafka:29092 \
  -e STORAGE_PROVIDER=s3 \
  -e S3_BUCKET_NAME=invoice-documents-prod \
  -e AWS_REGION=us-east-1 \
  -e AWS_ACCESS_KEY=AKIAIOSFODNN7EXAMPLE \
  -e AWS_SECRET_KEY=wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY \
  --network invoice-network \
  document-storage-service:1.0.0
```

### 3. Docker Compose Deployment

Create `docker-compose.yml`:

```yaml
version: '3.8'

services:
  mongodb:
    image: mongo:7.0
    container_name: document-storage-mongo
    ports:
      - "27017:27017"
    environment:
      MONGO_INITDB_DATABASE: document_storage
    volumes:
      - mongodb_data:/data/db
    networks:
      - invoice-network
    healthcheck:
      test: ["CMD", "mongosh", "--eval", "db.adminCommand('ping')"]
      interval: 10s
      timeout: 5s
      retries: 5

  kafka:
    image: confluentinc/cp-kafka:7.5.0
    container_name: invoice-kafka
    ports:
      - "9092:9092"
    environment:
      KAFKA_BROKER_ID: 1
      KAFKA_LISTENER_SECURITY_PROTOCOL_MAP: PLAINTEXT:PLAINTEXT,PLAINTEXT_INTERNAL:PLAINTEXT
      KAFKA_ADVERTISED_LISTENERS: PLAINTEXT://localhost:9092,PLAINTEXT_INTERNAL://kafka:29092
      KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR: 1
      KAFKA_TRANSACTION_STATE_LOG_MIN_ISR: 1
      KAFKA_TRANSACTION_STATE_LOG_REPLICATION_FACTOR: 1
      KAFKA_ZOOKEEPER_CONNECT: zookeeper:2181
    depends_on:
      - zookeeper
    networks:
      - invoice-network
    healthcheck:
      test: ["CMD-SHELL", "kafka-broker-api-versions --bootstrap-server=localhost:9092"]
      interval: 10s
      timeout: 5s
      retries: 5

  zookeeper:
    image: confluentinc/cp-zookeeper:7.5.0
    container_name: invoice-zookeeper
    environment:
      ZOOKEEPER_CLIENT_PORT: 2181
      ZOOKEEPER_TICK_TIME: 2000
    networks:
      - invoice-network

  document-storage-service:
    build: .
    image: document-storage-service:1.0.0
    container_name: document-storage
    ports:
      - "8084:8084"
    environment:
      # MongoDB
      MONGODB_HOST: mongodb
      MONGODB_PORT: 27017
      MONGODB_DATABASE: document_storage

      # Kafka
      KAFKA_BROKERS: kafka:29092

      # Storage - Local (default)
      STORAGE_PROVIDER: local
      LOCAL_STORAGE_PATH: /var/documents
      STORAGE_BASE_URL: http://localhost:8084/api/v1/documents

      # Storage - S3 (uncomment to use)
      # STORAGE_PROVIDER: s3
      # S3_BUCKET_NAME: invoice-documents
      # AWS_REGION: us-east-1
      # AWS_ACCESS_KEY: ${AWS_ACCESS_KEY}
      # AWS_SECRET_KEY: ${AWS_SECRET_KEY}

      # Service Discovery
      EUREKA_ENABLED: false

      # Logging
      LOGGING_LEVEL_COM_INVOICE_STORAGE: INFO
    volumes:
      - document_storage:/var/documents
    depends_on:
      mongodb:
        condition: service_healthy
      kafka:
        condition: service_healthy
    networks:
      - invoice-network
    healthcheck:
      test: ["CMD-SHELL", "wget --no-verbose --tries=1 --spider http://localhost:8084/actuator/health || exit 1"]
      interval: 30s
      timeout: 10s
      retries: 3
      start_period: 60s
    restart: unless-stopped

volumes:
  mongodb_data:
  document_storage:

networks:
  invoice-network:
    driver: bridge
```

Run the stack:
```bash
docker-compose up -d
```

Check logs:
```bash
docker-compose logs -f document-storage-service
```

### 4. Docker Registry Push

#### Docker Hub
```bash
docker tag document-storage-service:1.0.0 yourusername/document-storage-service:1.0.0
docker push yourusername/document-storage-service:1.0.0
```

#### AWS ECR
```bash
# Authenticate
aws ecr get-login-password --region us-east-1 | \
  docker login --username AWS --password-stdin 123456789012.dkr.ecr.us-east-1.amazonaws.com

# Create repository (first time)
aws ecr create-repository --repository-name document-storage-service --region us-east-1

# Tag and push
docker tag document-storage-service:1.0.0 \
  123456789012.dkr.ecr.us-east-1.amazonaws.com/document-storage-service:1.0.0

docker push 123456789012.dkr.ecr.us-east-1.amazonaws.com/document-storage-service:1.0.0
```

---

## AWS Deployment

### Option 1: AWS ECS (Elastic Container Service)

#### Prerequisites
- AWS CLI configured
- ECS cluster created
- VPC with public/private subnets
- Security groups configured
- AWS DocumentDB (MongoDB-compatible) or MongoDB Atlas
- Amazon MSK (Kafka) or self-managed Kafka

#### Task Definition

Create `ecs-task-definition.json`:

```json
{
  "family": "document-storage-service",
  "networkMode": "awsvpc",
  "requiresCompatibilities": ["FARGATE"],
  "cpu": "1024",
  "memory": "2048",
  "executionRoleArn": "arn:aws:iam::123456789012:role/ecsTaskExecutionRole",
  "taskRoleArn": "arn:aws:iam::123456789012:role/documentStorageTaskRole",
  "containerDefinitions": [
    {
      "name": "document-storage",
      "image": "123456789012.dkr.ecr.us-east-1.amazonaws.com/document-storage-service:1.0.0",
      "portMappings": [
        {
          "containerPort": 8084,
          "protocol": "tcp"
        }
      ],
      "environment": [
        {
          "name": "MONGODB_HOST",
          "value": "docdb-cluster.cluster-xxxxx.us-east-1.docdb.amazonaws.com"
        },
        {
          "name": "MONGODB_PORT",
          "value": "27017"
        },
        {
          "name": "MONGODB_DATABASE",
          "value": "document_storage"
        },
        {
          "name": "KAFKA_BROKERS",
          "value": "b-1.msk-cluster.xxxxx.kafka.us-east-1.amazonaws.com:9092"
        },
        {
          "name": "STORAGE_PROVIDER",
          "value": "s3"
        },
        {
          "name": "S3_BUCKET_NAME",
          "value": "invoice-documents-prod"
        },
        {
          "name": "AWS_REGION",
          "value": "us-east-1"
        },
        {
          "name": "EUREKA_ENABLED",
          "value": "false"
        }
      ],
      "secrets": [
        {
          "name": "MONGODB_USERNAME",
          "valueFrom": "arn:aws:secretsmanager:us-east-1:123456789012:secret:docdb/username-xxxxx"
        },
        {
          "name": "MONGODB_PASSWORD",
          "valueFrom": "arn:aws:secretsmanager:us-east-1:123456789012:secret:docdb/password-xxxxx"
        }
      ],
      "logConfiguration": {
        "logDriver": "awslogs",
        "options": {
          "awslogs-group": "/ecs/document-storage-service",
          "awslogs-region": "us-east-1",
          "awslogs-stream-prefix": "ecs"
        }
      },
      "healthCheck": {
        "command": [
          "CMD-SHELL",
          "wget --no-verbose --tries=1 --spider http://localhost:8084/actuator/health || exit 1"
        ],
        "interval": 30,
        "timeout": 5,
        "retries": 3,
        "startPeriod": 60
      }
    }
  ]
}
```

#### IAM Task Role Policy

Create IAM policy for S3 access (`documentStorageTaskRole`):

```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Action": [
        "s3:PutObject",
        "s3:GetObject",
        "s3:DeleteObject",
        "s3:ListBucket"
      ],
      "Resource": [
        "arn:aws:s3:::invoice-documents-prod",
        "arn:aws:s3:::invoice-documents-prod/*"
      ]
    }
  ]
}
```

#### Deploy to ECS

```bash
# Register task definition
aws ecs register-task-definition --cli-input-json file://ecs-task-definition.json

# Create service
aws ecs create-service \
  --cluster invoice-cluster \
  --service-name document-storage-service \
  --task-definition document-storage-service:1 \
  --desired-count 2 \
  --launch-type FARGATE \
  --network-configuration "awsvpcConfiguration={subnets=[subnet-xxxxx,subnet-yyyyy],securityGroups=[sg-xxxxx],assignPublicIp=DISABLED}" \
  --load-balancers "targetGroupArn=arn:aws:elasticloadbalancing:us-east-1:123456789012:targetgroup/doc-storage-tg/xxxxx,containerName=document-storage,containerPort=8084"
```

### Option 2: AWS EKS (Elastic Kubernetes Service)

#### Kubernetes Deployment

Create `k8s-deployment.yaml`:

```yaml
apiVersion: v1
kind: Namespace
metadata:
  name: invoice-services
---
apiVersion: v1
kind: ConfigMap
metadata:
  name: document-storage-config
  namespace: invoice-services
data:
  MONGODB_HOST: "mongodb-service.invoice-services.svc.cluster.local"
  MONGODB_PORT: "27017"
  MONGODB_DATABASE: "document_storage"
  KAFKA_BROKERS: "kafka-service.invoice-services.svc.cluster.local:9092"
  STORAGE_PROVIDER: "s3"
  S3_BUCKET_NAME: "invoice-documents-prod"
  AWS_REGION: "us-east-1"
  EUREKA_ENABLED: "false"
---
apiVersion: v1
kind: Secret
metadata:
  name: document-storage-secrets
  namespace: invoice-services
type: Opaque
stringData:
  MONGODB_USERNAME: "admin"
  MONGODB_PASSWORD: "changeme"
---
apiVersion: apps/v1
kind: Deployment
metadata:
  name: document-storage-service
  namespace: invoice-services
  labels:
    app: document-storage
    version: v1
spec:
  replicas: 3
  selector:
    matchLabels:
      app: document-storage
  template:
    metadata:
      labels:
        app: document-storage
        version: v1
    spec:
      serviceAccountName: document-storage-sa
      containers:
      - name: document-storage
        image: 123456789012.dkr.ecr.us-east-1.amazonaws.com/document-storage-service:1.0.0
        imagePullPolicy: Always
        ports:
        - containerPort: 8084
          name: http
        envFrom:
        - configMapRef:
            name: document-storage-config
        - secretRef:
            name: document-storage-secrets
        resources:
          requests:
            memory: "1Gi"
            cpu: "500m"
          limits:
            memory: "2Gi"
            cpu: "1000m"
        livenessProbe:
          httpGet:
            path: /actuator/health/liveness
            port: 8084
          initialDelaySeconds: 60
          periodSeconds: 10
          timeoutSeconds: 5
          failureThreshold: 3
        readinessProbe:
          httpGet:
            path: /actuator/health/readiness
            port: 8084
          initialDelaySeconds: 30
          periodSeconds: 5
          timeoutSeconds: 3
          failureThreshold: 3
---
apiVersion: v1
kind: Service
metadata:
  name: document-storage-service
  namespace: invoice-services
spec:
  type: ClusterIP
  selector:
    app: document-storage
  ports:
  - port: 8084
    targetPort: 8084
    protocol: TCP
    name: http
---
apiVersion: v1
kind: ServiceAccount
metadata:
  name: document-storage-sa
  namespace: invoice-services
  annotations:
    eks.amazonaws.com/role-arn: arn:aws:iam::123456789012:role/DocumentStorageS3Role
---
apiVersion: autoscaling/v2
kind: HorizontalPodAutoscaler
metadata:
  name: document-storage-hpa
  namespace: invoice-services
spec:
  scaleTargetRef:
    apiVersion: apps/v1
    kind: Deployment
    name: document-storage-service
  minReplicas: 2
  maxReplicas: 10
  metrics:
  - type: Resource
    resource:
      name: cpu
      target:
        type: Utilization
        averageUtilization: 70
  - type: Resource
    resource:
      name: memory
      target:
        type: Utilization
        averageUtilization: 80
```

Deploy to EKS:
```bash
kubectl apply -f k8s-deployment.yaml
```

### Option 3: AWS Elastic Beanstalk

Create `.ebextensions/01-document-storage.config`:

```yaml
option_settings:
  aws:elasticbeanstalk:application:environment:
    MONGODB_HOST: docdb-cluster.cluster-xxxxx.us-east-1.docdb.amazonaws.com
    MONGODB_PORT: 27017
    MONGODB_DATABASE: document_storage
    KAFKA_BROKERS: b-1.msk-cluster.xxxxx.kafka.us-east-1.amazonaws.com:9092
    STORAGE_PROVIDER: s3
    S3_BUCKET_NAME: invoice-documents-prod
    AWS_REGION: us-east-1
    SERVER_PORT: 5000
```

Deploy:
```bash
eb init -p docker document-storage-service
eb create document-storage-prod --instance-type t3.medium
```

---

## Red Hat OpenShift Deployment

### 1. Prerequisites

- OpenShift CLI (`oc`) installed
- Access to OpenShift cluster (4.10+)
- Image registry (OpenShift internal or external)

### 2. Create OpenShift Project

```bash
oc new-project invoice-services
```

### 3. Deploy MongoDB Operator (Recommended)

```bash
# Install MongoDB Community Operator
oc apply -f https://raw.githubusercontent.com/mongodb/mongodb-kubernetes-operator/master/config/crd/bases/mongodbcommunity.mongodb.com_mongodbcommunity.yaml

# Create MongoDB instance
cat <<EOF | oc apply -f -
apiVersion: mongodbcommunity.mongodb.com/v1
kind: MongoDBCommunity
metadata:
  name: document-storage-mongo
  namespace: invoice-services
spec:
  members: 3
  type: ReplicaSet
  version: "7.0.0"
  security:
    authentication:
      modes: ["SCRAM"]
  users:
    - name: admin
      db: admin
      passwordSecretRef:
        name: mongodb-admin-password
      roles:
        - name: clusterAdmin
          db: admin
        - name: userAdminAnyDatabase
          db: admin
    - name: docuser
      db: document_storage
      passwordSecretRef:
        name: mongodb-docuser-password
      roles:
        - name: readWrite
          db: document_storage
  statefulSet:
    spec:
      volumeClaimTemplates:
        - metadata:
            name: data-volume
          spec:
            accessModes: [ "ReadWriteOnce" ]
            resources:
              requests:
                storage: 50Gi
            storageClassName: gp3-csi
EOF
```

### 4. Create Secrets

```bash
# MongoDB secrets
oc create secret generic mongodb-admin-password \
  --from-literal=password='admin-strong-password'

oc create secret generic mongodb-docuser-password \
  --from-literal=password='docuser-strong-password'

# S3 credentials (if using S3)
oc create secret generic s3-credentials \
  --from-literal=AWS_ACCESS_KEY='AKIAIOSFODNN7EXAMPLE' \
  --from-literal=AWS_SECRET_KEY='wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY'
```

### 5. OpenShift Deployment Configuration

Create `openshift-deployment.yaml`:

```yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: document-storage-config
  namespace: invoice-services
data:
  application.yml: |
    server:
      port: 8084
    spring:
      application:
        name: document-storage-service
      data:
        mongodb:
          host: ${MONGODB_HOST}
          port: ${MONGODB_PORT}
          database: ${MONGODB_DATABASE}
          username: ${MONGODB_USERNAME}
          password: ${MONGODB_PASSWORD}
      kafka:
        bootstrap-servers: ${KAFKA_BROKERS}
    app:
      storage:
        provider: ${STORAGE_PROVIDER}
        s3:
          bucket-name: ${S3_BUCKET_NAME}
          region: ${AWS_REGION}
---
apiVersion: apps/v1
kind: Deployment
metadata:
  name: document-storage-service
  namespace: invoice-services
  labels:
    app: document-storage
    app.kubernetes.io/name: document-storage-service
    app.kubernetes.io/component: microservice
    app.openshift.io/runtime: java
spec:
  replicas: 2
  selector:
    matchLabels:
      app: document-storage
  template:
    metadata:
      labels:
        app: document-storage
        deploymentconfig: document-storage-service
    spec:
      containers:
      - name: document-storage
        image: image-registry.openshift-image-registry.svc:5000/invoice-services/document-storage-service:latest
        imagePullPolicy: Always
        ports:
        - containerPort: 8084
          protocol: TCP
        env:
        - name: MONGODB_HOST
          value: "document-storage-mongo-svc.invoice-services.svc.cluster.local"
        - name: MONGODB_PORT
          value: "27017"
        - name: MONGODB_DATABASE
          value: "document_storage"
        - name: MONGODB_USERNAME
          value: "docuser"
        - name: MONGODB_PASSWORD
          valueFrom:
            secretKeyRef:
              name: mongodb-docuser-password
              key: password
        - name: KAFKA_BROKERS
          value: "kafka-kafka-bootstrap.invoice-services.svc.cluster.local:9092"
        - name: STORAGE_PROVIDER
          value: "s3"
        - name: S3_BUCKET_NAME
          value: "invoice-documents-prod"
        - name: AWS_REGION
          value: "us-east-1"
        - name: AWS_ACCESS_KEY
          valueFrom:
            secretKeyRef:
              name: s3-credentials
              key: AWS_ACCESS_KEY
        - name: AWS_SECRET_KEY
          valueFrom:
            secretKeyRef:
              name: s3-credentials
              key: AWS_SECRET_KEY
        - name: JAVA_OPTS
          value: "-Xms512m -Xmx1536m -XX:+UseG1GC"
        resources:
          requests:
            memory: "1Gi"
            cpu: "500m"
          limits:
            memory: "2Gi"
            cpu: "1500m"
        livenessProbe:
          httpGet:
            path: /actuator/health/liveness
            port: 8084
            scheme: HTTP
          initialDelaySeconds: 90
          periodSeconds: 10
          timeoutSeconds: 5
          successThreshold: 1
          failureThreshold: 3
        readinessProbe:
          httpGet:
            path: /actuator/health/readiness
            port: 8084
            scheme: HTTP
          initialDelaySeconds: 60
          periodSeconds: 5
          timeoutSeconds: 3
          successThreshold: 1
          failureThreshold: 3
        volumeMounts:
        - name: config-volume
          mountPath: /app/config
      volumes:
      - name: config-volume
        configMap:
          name: document-storage-config
      securityContext:
        runAsNonRoot: true
        runAsUser: 1000
        fsGroup: 1000
---
apiVersion: v1
kind: Service
metadata:
  name: document-storage-service
  namespace: invoice-services
  labels:
    app: document-storage
spec:
  ports:
  - name: 8084-tcp
    port: 8084
    protocol: TCP
    targetPort: 8084
  selector:
    app: document-storage
  type: ClusterIP
---
apiVersion: route.openshift.io/v1
kind: Route
metadata:
  name: document-storage
  namespace: invoice-services
  labels:
    app: document-storage
spec:
  to:
    kind: Service
    name: document-storage-service
  port:
    targetPort: 8084-tcp
  tls:
    termination: edge
    insecureEdgeTerminationPolicy: Redirect
  wildcardPolicy: None
```

### 6. Build and Deploy on OpenShift

#### Using Source-to-Image (S2I)

```bash
# Create build config
oc new-build --name=document-storage-service \
  --binary=true \
  --strategy=docker \
  --to=document-storage-service:latest

# Build from local Dockerfile
oc start-build document-storage-service \
  --from-dir=. \
  --follow

# Deploy
oc apply -f openshift-deployment.yaml
```

#### Using ImageStream from External Registry

```bash
# Create ImageStream from ECR
oc import-image document-storage-service:1.0.0 \
  --from=123456789012.dkr.ecr.us-east-1.amazonaws.com/document-storage-service:1.0.0 \
  --confirm

# Update deployment to use ImageStream
oc set image deployment/document-storage-service \
  document-storage=image-registry.openshift-image-registry.svc:5000/invoice-services/document-storage-service:1.0.0
```

### 7. Persistent Volume for Local Storage (Optional)

If using local filesystem storage instead of S3:

```yaml
apiVersion: v1
kind: PersistentVolumeClaim
metadata:
  name: document-storage-pvc
  namespace: invoice-services
spec:
  accessModes:
    - ReadWriteMany
  resources:
    requests:
      storage: 500Gi
  storageClassName: nfs-storage  # or gp3-csi for AWS EBS
---
# Add to Deployment spec.template.spec:
volumes:
- name: document-storage
  persistentVolumeClaim:
    claimName: document-storage-pvc
# Add to container:
volumeMounts:
- name: document-storage
  mountPath: /var/documents
```

### 8. Horizontal Pod Autoscaler

```bash
oc autoscale deployment document-storage-service \
  --min=2 \
  --max=10 \
  --cpu-percent=70
```

### 9. Monitoring with OpenShift Monitoring Stack

Create `ServiceMonitor` for Prometheus:

```yaml
apiVersion: monitoring.coreos.com/v1
kind: ServiceMonitor
metadata:
  name: document-storage-metrics
  namespace: invoice-services
  labels:
    app: document-storage
spec:
  selector:
    matchLabels:
      app: document-storage
  endpoints:
  - port: 8084-tcp
    path: /actuator/prometheus
    interval: 30s
```

---

## Configuration Reference

### Environment Variables

| Variable | Required | Default | Description |
|----------|----------|---------|-------------|
| `MONGODB_HOST` | Yes | `localhost` | MongoDB hostname |
| `MONGODB_PORT` | No | `27017` | MongoDB port |
| `MONGODB_DATABASE` | No | `document_storage` | Database name |
| `MONGODB_USERNAME` | No | - | MongoDB username |
| `MONGODB_PASSWORD` | No | - | MongoDB password |
| `KAFKA_BROKERS` | Yes | `localhost:9092` | Kafka bootstrap servers |
| `STORAGE_PROVIDER` | No | `local` | Storage backend (`local` or `s3`) |
| `LOCAL_STORAGE_PATH` | No | `/var/documents` | Local storage directory path |
| `STORAGE_BASE_URL` | No | `http://localhost:8084/api/v1/documents` | Base URL for document access |
| `S3_BUCKET_NAME` | Conditional | - | S3 bucket name (required if `STORAGE_PROVIDER=s3`) |
| `AWS_REGION` | No | `us-east-1` | AWS region |
| `AWS_ACCESS_KEY` | Conditional | - | AWS access key (if not using IAM roles) |
| `AWS_SECRET_KEY` | Conditional | - | AWS secret key (if not using IAM roles) |
| `S3_ENDPOINT` | No | - | S3-compatible endpoint (MinIO, Ceph) |
| `S3_PATH_STYLE_ACCESS` | No | `false` | Enable path-style S3 access |
| `EUREKA_ENABLED` | No | `true` | Enable Eureka service discovery |
| `EUREKA_SERVER` | No | `http://localhost:8761/eureka/` | Eureka server URL |
| `JAVA_OPTS` | No | - | JVM options |

### Storage Provider Configuration

#### Local Filesystem
- Suitable for: Development, single-node deployments
- Requires: Persistent volume in Kubernetes/OpenShift
- Limitations: Not suitable for multi-replica deployments without shared storage (NFS, EFS)

#### AWS S3
- Suitable for: Production, multi-region deployments
- Requires: S3 bucket, IAM permissions
- Benefits: Highly available, scalable, no storage management

#### S3-Compatible (MinIO, Ceph)
```bash
export S3_ENDPOINT=https://minio.example.com
export S3_PATH_STYLE_ACCESS=true
export S3_BUCKET_NAME=documents
```

### Resource Requirements

#### Minimum (Development)
- CPU: 0.5 cores
- Memory: 1 GB
- Storage: 50 GB (if using local storage)

#### Recommended (Production)
- CPU: 1-2 cores
- Memory: 2 GB
- Storage: 500 GB+ (if using local storage)

#### Large Scale (High Traffic)
- CPU: 2-4 cores
- Memory: 4 GB
- Storage: S3 (unlimited)
- Replicas: 3-10 with HPA

---

## Monitoring and Health Checks

### Health Endpoints

| Endpoint | Purpose |
|----------|---------|
| `/actuator/health` | Overall health status |
| `/actuator/health/liveness` | Kubernetes liveness probe |
| `/actuator/health/readiness` | Kubernetes readiness probe |
| `/actuator/metrics` | Metrics endpoint |
| `/actuator/prometheus` | Prometheus metrics |

### Key Metrics to Monitor

```promql
# Request rate
rate(http_server_requests_seconds_count[5m])

# Error rate
rate(http_server_requests_seconds_count{status=~"5.."}[5m])

# Document upload latency (p95)
histogram_quantile(0.95, rate(http_server_requests_seconds_bucket{uri="/api/v1/documents",method="POST"}[5m]))

# MongoDB connection pool
mongodb_driver_pool_size

# Kafka consumer lag
kafka_consumer_fetch_manager_records_lag_max

# S3 upload failures
s3_storage_upload_errors_total
```

### CloudWatch Alarms (AWS)

```bash
# High error rate
aws cloudwatch put-metric-alarm \
  --alarm-name document-storage-high-error-rate \
  --alarm-description "Document Storage 5xx error rate > 1%" \
  --metric-name 5XXError \
  --namespace AWS/ApplicationELB \
  --statistic Average \
  --period 60 \
  --evaluation-periods 2 \
  --threshold 1.0 \
  --comparison-operator GreaterThanThreshold

# Memory utilization
aws cloudwatch put-metric-alarm \
  --alarm-name document-storage-high-memory \
  --metric-name MemoryUtilization \
  --namespace AWS/ECS \
  --statistic Average \
  --period 300 \
  --evaluation-periods 2 \
  --threshold 85.0 \
  --comparison-operator GreaterThanThreshold
```

---

## Troubleshooting

### Common Issues

#### 1. Service Won't Start

**Symptom**: Container crashes on startup

**Check**:
```bash
# Docker
docker logs document-storage

# Kubernetes
kubectl logs -n invoice-services deployment/document-storage-service

# OpenShift
oc logs -n invoice-services deployment/document-storage-service
```

**Common Causes**:
- MongoDB connection refused → Check `MONGODB_HOST` and network connectivity
- Kafka connection timeout → Check `KAFKA_BROKERS` and firewall rules
- Out of memory → Increase container memory limit

#### 2. Cannot Upload Documents

**Symptom**: 500 error on POST `/api/v1/documents`

**Check**:
```bash
# Local storage permissions
ls -la /var/documents

# S3 bucket permissions
aws s3 ls s3://invoice-documents-prod/
```

**Solutions**:
- Local: Ensure `/var/documents` is writable by user `appuser` (UID 1000)
- S3: Verify IAM role has `s3:PutObject` permission

#### 3. Checksum Verification Failures

**Symptom**: Error "Document checksum mismatch" on download

**Possible Causes**:
- Storage corruption (disk failure)
- Network issues during S3 upload/download
- Concurrent modification

**Solution**: Delete and re-upload document

#### 4. Kafka Consumer Not Processing Events

**Check consumer group status**:
```bash
kafka-consumer-groups --bootstrap-server localhost:9092 \
  --group document-storage-service \
  --describe
```

**Reset offsets if needed**:
```bash
kafka-consumer-groups --bootstrap-server localhost:9092 \
  --group document-storage-service \
  --topic pdf.generated \
  --reset-offsets --to-earliest \
  --execute
```

#### 5. High Latency on Document Downloads

**Solutions**:
- Enable CloudFront/CDN for S3
- Increase read IOPS (local storage)
- Check network bandwidth
- Add caching layer (Redis)

#### 6. MongoDB Connection Pool Exhausted

**Symptom**: `MongoTimeoutException: Timed out after 30000 ms while waiting for a server`

**Solution**: Increase connection pool size in `application.yml`:
```yaml
spring:
  data:
    mongodb:
      max-pool-size: 50
      min-pool-size: 10
```

### Debug Mode

Enable debug logging:
```bash
# Environment variable
export LOGGING_LEVEL_COM_INVOICE_STORAGE=DEBUG

# Or in application.yml
logging:
  level:
    com.invoice.storage: DEBUG
    org.springframework.data.mongodb: DEBUG
    org.springframework.kafka: DEBUG
```

### Health Check Troubleshooting

```bash
# Check health endpoint
curl http://localhost:8084/actuator/health

# Expected response
{
  "status": "UP",
  "components": {
    "mongo": {"status": "UP"},
    "diskSpace": {"status": "UP"},
    "ping": {"status": "UP"}
  }
}
```

---

## Security Considerations

### Network Security

#### AWS Security Groups
```bash
# Inbound rules for ECS tasks
- Port 8084 from ALB security group (application traffic)
- Port 27017 to DocumentDB security group (MongoDB)
- Port 9092 to MSK security group (Kafka)
```

#### OpenShift Network Policies
```yaml
apiVersion: networking.k8s.io/v1
kind: NetworkPolicy
metadata:
  name: document-storage-netpol
  namespace: invoice-services
spec:
  podSelector:
    matchLabels:
      app: document-storage
  policyTypes:
  - Ingress
  - Egress
  ingress:
  - from:
    - podSelector:
        matchLabels:
          app: api-gateway
    ports:
    - protocol: TCP
      port: 8084
  egress:
  - to:
    - podSelector:
        matchLabels:
          app: mongodb
    ports:
    - protocol: TCP
      port: 27017
  - to:
    - podSelector:
        matchLabels:
          app: kafka
    ports:
    - protocol: TCP
      port: 9092
```

### Secret Management

**AWS Secrets Manager**:
```bash
aws secretsmanager create-secret \
  --name invoice/document-storage/mongodb \
  --secret-string '{"username":"admin","password":"strongpassword"}'
```

**OpenShift Sealed Secrets**:
```bash
kubeseal --format yaml < mongodb-secret.yaml > mongodb-sealed-secret.yaml
oc apply -f mongodb-sealed-secret.yaml
```

### S3 Bucket Policy

```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Principal": {
        "AWS": "arn:aws:iam::123456789012:role/DocumentStorageTaskRole"
      },
      "Action": [
        "s3:PutObject",
        "s3:GetObject",
        "s3:DeleteObject"
      ],
      "Resource": "arn:aws:s3:::invoice-documents-prod/*"
    },
    {
      "Effect": "Allow",
      "Principal": {
        "AWS": "arn:aws:iam::123456789012:role/DocumentStorageTaskRole"
      },
      "Action": "s3:ListBucket",
      "Resource": "arn:aws:s3:::invoice-documents-prod"
    }
  ]
}
```

---

## Performance Tuning

### JVM Tuning

```bash
# For containers with 2GB memory
JAVA_OPTS="-Xms1g -Xmx1536m -XX:+UseG1GC -XX:MaxGCPauseMillis=200 -XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=/tmp/heapdump.hprof"

# For high-throughput workloads
JAVA_OPTS="-XX:+UseG1GC -XX:G1HeapRegionSize=16M -XX:InitiatingHeapOccupancyPercent=45"
```

### MongoDB Indexing

```javascript
// Connect to MongoDB
use document_storage;

// Create indexes
db.documents.createIndex({ "fileName": 1 });
db.documents.createIndex({ "documentType": 1 });
db.documents.createIndex({ "invoiceId": 1 });
db.documents.createIndex({ "invoiceNumber": 1 });
db.documents.createIndex({ "createdAt": -1 });
db.documents.createIndex({ "expiresAt": 1 }, { expireAfterSeconds: 0 });

// Compound index for common queries
db.documents.createIndex({ "invoiceId": 1, "documentType": 1, "createdAt": -1 });
```

### S3 Transfer Acceleration

Enable in `application.yml`:
```yaml
app:
  storage:
    s3:
      transfer-acceleration: true
```

---

## Backup and Disaster Recovery

### MongoDB Backup

#### AWS DocumentDB
```bash
# Automated backups (configured in AWS Console)
# Retention: 7-35 days
# Point-in-time recovery enabled
```

#### Self-Managed MongoDB
```bash
# Mongodump backup
mongodump --host localhost --port 27017 \
  --db document_storage \
  --out /backup/mongodb-$(date +%Y%m%d)

# Restore
mongorestore --host localhost --port 27017 \
  --db document_storage \
  /backup/mongodb-20251210/document_storage
```

### S3 Versioning and Lifecycle

```bash
# Enable versioning
aws s3api put-bucket-versioning \
  --bucket invoice-documents-prod \
  --versioning-configuration Status=Enabled

# Lifecycle policy (transition to Glacier after 90 days)
aws s3api put-bucket-lifecycle-configuration \
  --bucket invoice-documents-prod \
  --lifecycle-configuration file://lifecycle.json
```

`lifecycle.json`:
```json
{
  "Rules": [
    {
      "Id": "ArchiveOldDocuments",
      "Status": "Enabled",
      "Filter": {},
      "Transitions": [
        {
          "Days": 90,
          "StorageClass": "GLACIER"
        }
      ]
    }
  ]
}
```

---

## Appendix

### A. Complete docker-compose.yml with All Services

See [docker-compose.yml](../docker-compose.yml) in repository root.

### B. Kubernetes YAML Files

All Kubernetes manifests are available in the `k8s/` directory.

### C. Terraform Modules (AWS)

Infrastructure as Code examples are in the `terraform/` directory.

### D. OpenShift Templates

Red Hat OpenShift templates are in the `openshift/` directory.

---

## Support and Documentation

- [PROGRAM_FLOW.md](PROGRAM_FLOW.md) - Detailed program flow diagrams
- [CLAUDE.md](../CLAUDE.md) - Project-specific guidance
- [Main Repository Documentation](../../README.md)

For issues and questions, refer to the main project repository.
