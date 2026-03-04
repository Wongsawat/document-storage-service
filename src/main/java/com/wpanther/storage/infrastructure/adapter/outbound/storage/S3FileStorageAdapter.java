package com.wpanther.storage.infrastructure.adapter.outbound.storage;

import com.wpanther.storage.domain.service.FileStorageProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

import java.time.LocalDate;
import java.util.UUID;

/**
 * AWS S3 storage provider implementation
 * Stores documents in S3 with date-based key structure
 */
@Component
@ConditionalOnProperty(name = "app.storage.provider", havingValue = "s3")
@Slf4j
public class S3FileStorageAdapter implements FileStorageProvider {

    private final S3Client s3Client;
    private final String bucketName;
    private final String baseUrl;

    public S3FileStorageAdapter(
        @Value("${app.storage.s3.bucket-name}") String bucketName,
        @Value("${app.storage.s3.region}") String region,
        @Value("${app.storage.s3.access-key}") String accessKey,
        @Value("${app.storage.s3.secret-key}") String secretKey,
        @Value("${app.storage.s3.base-url}") String baseUrl,
        @Value("${app.storage.s3.endpoint:}") String endpoint,
        @Value("${app.storage.s3.path-style-access:false}") boolean pathStyleAccess
    ) {
        this.bucketName = bucketName;
        this.baseUrl = baseUrl;

        // Initialize S3 client
        AwsBasicCredentials credentials = AwsBasicCredentials.create(accessKey, secretKey);
        var builder = S3Client.builder()
            .region(Region.of(region))
            .credentialsProvider(StaticCredentialsProvider.create(credentials));

        // Configure custom endpoint for S3-compatible storage (Ceph, MinIO, etc.)
        if (endpoint != null && !endpoint.isEmpty()) {
            builder.endpointOverride(java.net.URI.create(endpoint));
            log.info("Using custom S3 endpoint: {}", endpoint);
        }

        // Enable path-style access for S3-compatible storage
        if (pathStyleAccess) {
            builder.forcePathStyle(true);
            log.info("Using path-style access for S3");
        }

        this.s3Client = builder.build();

        log.info("Initialized S3 storage provider: bucket={}, region={}, endpoint={}",
            bucketName, region, endpoint != null && !endpoint.isEmpty() ? endpoint : "AWS");
    }

    @Override
    public StorageResult store(byte[] content, String fileName) throws StorageException {
        try {
            String key = generateS3Key(fileName);

            PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                .bucket(bucketName)
                .key(key)
                .contentType(determineContentType(fileName))
                .contentLength((long) content.length)
                .build();

            s3Client.putObject(putObjectRequest, RequestBody.fromBytes(content));

            String url = generateUrl(key);
            log.info("Stored file in S3: bucket={}, key={}, size={} bytes", bucketName, key, content.length);

            return new StorageResult(key, url);

        } catch (S3Exception e) {
            log.error("Failed to store file in S3: {}", fileName, e);
            throw new StorageException("Failed to store file in S3", e);
        }
    }

    @Override
    public byte[] retrieve(String path) throws StorageException {
        try {
            GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                .bucket(bucketName)
                .key(path)
                .build();

            byte[] content = s3Client.getObject(getObjectRequest).readAllBytes();
            log.info("Retrieved file from S3: bucket={}, key={}, size={} bytes", bucketName, path, content.length);

            return content;

        } catch (S3Exception e) {
            if (e.statusCode() == 404) {
                throw new StorageException("File not found in S3: " + path, e);
            }
            log.error("Failed to retrieve file from S3: {}", path, e);
            throw new StorageException("Failed to retrieve file from S3", e);
        } catch (Exception e) {
            log.error("Error reading file content from S3: {}", path, e);
            throw new StorageException("Failed to read file content", e);
        }
    }

    @Override
    public void delete(String path) throws StorageException {
        try {
            DeleteObjectRequest deleteObjectRequest = DeleteObjectRequest.builder()
                .bucket(bucketName)
                .key(path)
                .build();

            s3Client.deleteObject(deleteObjectRequest);
            log.info("Deleted file from S3: bucket={}, key={}", bucketName, path);

        } catch (S3Exception e) {
            log.error("Failed to delete file from S3: {}", path, e);
            throw new StorageException("Failed to delete file from S3", e);
        }
    }

    @Override
    public boolean exists(String path) {
        try {
            HeadObjectRequest headObjectRequest = HeadObjectRequest.builder()
                .bucket(bucketName)
                .key(path)
                .build();

            s3Client.headObject(headObjectRequest);
            return true;

        } catch (NoSuchKeyException e) {
            return false;
        } catch (S3Exception e) {
            log.warn("Error checking file existence in S3: {}", path, e);
            return false;
        }
    }

    /**
     * Generate S3 key with date-based structure: YYYY/MM/DD/UUID_filename
     */
    private String generateS3Key(String fileName) {
        LocalDate now = LocalDate.now();
        String uniqueId = UUID.randomUUID().toString();

        return String.format("%04d/%02d/%02d/%s_%s",
            now.getYear(),
            now.getMonthValue(),
            now.getDayOfMonth(),
            uniqueId,
            fileName
        );
    }

    /**
     * Generate public URL for S3 object
     */
    private String generateUrl(String key) {
        if (baseUrl != null && !baseUrl.isEmpty()) {
            return baseUrl + "/" + key;
        }

        // Use S3 URL format
        return String.format("https://%s.s3.amazonaws.com/%s", bucketName, key);
    }

    /**
     * Determine content type from filename
     */
    private String determineContentType(String fileName) {
        String lowerFileName = fileName.toLowerCase();

        if (lowerFileName.endsWith(".pdf")) {
            return "application/pdf";
        } else if (lowerFileName.endsWith(".xml")) {
            return "application/xml";
        } else if (lowerFileName.endsWith(".json")) {
            return "application/json";
        } else if (lowerFileName.endsWith(".png")) {
            return "image/png";
        } else if (lowerFileName.endsWith(".jpg") || lowerFileName.endsWith(".jpeg")) {
            return "image/jpeg";
        }

        return "application/octet-stream";
    }
}
