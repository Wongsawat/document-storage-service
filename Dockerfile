# Multi-stage build for Document Storage Service
#
# Build from the monorepo root (etax/) to include sibling dependencies:
#   docker build -f invoice-microservices/services/document-storage-service/Dockerfile .
#
# Stage 1: Build saga-commons library (dependency)
FROM maven:3.9-eclipse-temurin-21-alpine AS build-saga-commons

WORKDIR /saga-commons
COPY saga-commons/pom.xml .
RUN mvn dependency:go-offline -B
COPY saga-commons/src ./src
RUN mvn clean install -DskipTests -B

# Stage 2: Build document-storage-service
FROM maven:3.9-eclipse-temurin-21-alpine AS build

WORKDIR /app

# Copy saga-commons from previous build stage into local Maven repo
COPY --from=build-saga-commons /root/.m2/repository/com/wpanther/saga-commons /root/.m2/repository/com/wpanther/saga-commons

# Copy pom.xml and download dependencies (for caching)
COPY invoice-microservices/services/document-storage-service/pom.xml .
RUN mvn dependency:go-offline -B

# Copy source code and build
COPY invoice-microservices/services/document-storage-service/src ./src
RUN mvn clean package -DskipTests -B

# Runtime stage
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

# Create non-root user
RUN addgroup -S appgroup && adduser -S appuser -G appgroup

# Create document storage directory
RUN mkdir -p /var/documents && \
    chown -R appuser:appgroup /var/documents

# Copy JAR from build stage
COPY --from=build /app/target/*.jar app.jar

# Change ownership
RUN chown appuser:appgroup app.jar

# Switch to non-root user
USER appuser

# Expose port
EXPOSE 8084

# Health check
HEALTHCHECK --interval=30s --timeout=3s --start-period=40s --retries=3 \
  CMD wget --no-verbose --tries=1 --spider http://localhost:8084/actuator/health || exit 1

# Run application
ENTRYPOINT ["java", "-jar", "app.jar"]
