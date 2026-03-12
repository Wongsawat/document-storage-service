package com.wpanther.storage.infrastructure.adapter.outbound.storage;

import com.wpanther.storage.domain.model.StorageException;
import com.wpanther.storage.domain.model.StorageResult;
import com.wpanther.storage.application.port.out.StorageProviderPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Local filesystem storage adapter
 * Implements StorageProviderPort for local file storage
 */
@Component
@ConditionalOnProperty(name = "app.storage.provider", havingValue = "local", matchIfMissing = true)
@Slf4j
public class LocalFileStorageAdapter implements StorageProviderPort {

    private static final String PROVIDER_NAME = "local";

    @Value("${app.storage.local.path:/var/documents}")
    private String basePath;

    @Value("${app.storage.local.base-url:http://localhost:8084}")
    private String baseUrl;

    @Override
    public StorageResult store(String documentId, InputStream content,
                                String originalFilename, long size) throws StorageException {
        try {
            // Create directory structure: basePath/YYYY/MM/DD/
            LocalDate now = LocalDate.now();
            Path directory = Paths.get(basePath,
                String.valueOf(now.getYear()),
                String.format("%02d", now.getMonthValue()),
                String.format("%02d", now.getDayOfMonth()));

            Files.createDirectories(directory);

            // Generate unique filename (keep only extension from original)
            String uniqueFileName = generateUniqueFileName(originalFilename);
            Path filePath = directory.resolve(uniqueFileName);

            // Copy stream to file
            long bytesCopied = Files.copy(content, filePath, StandardCopyOption.REPLACE_EXISTING);

            String relativePath = Paths.get(basePath).relativize(filePath).toString().replace("\\", "/");
            String url = baseUrl + "/documents/" + relativePath;

            log.info("Stored file: {} (size: {} bytes)", filePath, bytesCopied);

            return StorageResult.success(filePath.toString(), PROVIDER_NAME);

        } catch (IOException e) {
            log.error("Failed to store file: {}", originalFilename, e);
            throw new StorageException("Failed to store file: " + originalFilename, e);
        }
    }

    @Override
    public InputStream retrieve(String storageLocation) throws StorageException {
        try {
            Path filePath = Paths.get(storageLocation);

            if (!Files.exists(filePath)) {
                throw new StorageException("File not found: " + storageLocation);
            }

            log.debug("Retrieving file: {}", storageLocation);
            return Files.newInputStream(filePath);

        } catch (IOException e) {
            log.error("Failed to retrieve file: {}", storageLocation, e);
            throw new StorageException("Failed to retrieve file: " + storageLocation, e);
        }
    }

    @Override
    public void delete(String storageLocation) throws StorageException {
        try {
            Path filePath = Paths.get(storageLocation);

            if (Files.exists(filePath)) {
                Files.delete(filePath);
                log.info("Deleted file: {}", storageLocation);
            } else {
                log.warn("File not found for deletion: {}", storageLocation);
            }

        } catch (IOException e) {
            log.error("Failed to delete file: {}", storageLocation, e);
            throw new StorageException("Failed to delete file: " + storageLocation, e);
        }
    }

    @Override
    public boolean exists(String storageLocation) {
        return Files.exists(Paths.get(storageLocation));
    }

    /**
     * Generate unique filename keeping only the extension from original filename
     */
    private String generateUniqueFileName(String originalFileName) {
        String extension = "";
        int dotIndex = originalFileName.lastIndexOf('.');
        if (dotIndex > 0) {
            extension = originalFileName.substring(dotIndex);
        }
        return UUID.randomUUID().toString() + extension;
    }
}
