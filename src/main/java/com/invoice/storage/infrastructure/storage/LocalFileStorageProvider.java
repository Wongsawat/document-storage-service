package com.invoice.storage.infrastructure.storage;

import com.invoice.storage.domain.service.FileStorageProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Local filesystem storage provider
 */
@Component
@ConditionalOnProperty(name = "app.storage.provider", havingValue = "local", matchIfMissing = true)
@Slf4j
public class LocalFileStorageProvider implements FileStorageProvider {

    @Value("${app.storage.local.path:/var/documents}")
    private String basePath;

    @Value("${app.storage.local.base-url:http://localhost:8084}")
    private String baseUrl;

    @Override
    public StorageResult store(byte[] content, String fileName) throws StorageException {
        try {
            // Create directory structure: basePath/YYYY/MM/DD/
            LocalDate now = LocalDate.now();
            Path directory = Paths.get(basePath,
                String.valueOf(now.getYear()),
                String.format("%02d", now.getMonthValue()),
                String.format("%02d", now.getDayOfMonth()));

            Files.createDirectories(directory);

            // Generate unique filename
            String uniqueFileName = generateUniqueFileName(fileName);
            Path filePath = directory.resolve(uniqueFileName);

            // Write file
            Files.write(filePath, content);

            String relativePath = Paths.get(basePath).relativize(filePath).toString().replace("\\", "/");
            String url = baseUrl + "/documents/" + relativePath;

            log.info("Stored file: {} (size: {} bytes)", filePath, content.length);

            return new StorageResult(filePath.toString(), url);

        } catch (IOException e) {
            log.error("Failed to store file: {}", fileName, e);
            throw new StorageException("Failed to store file", e);
        }
    }

    @Override
    public byte[] retrieve(String path) throws StorageException {
        try {
            Path filePath = Paths.get(path);

            if (!Files.exists(filePath)) {
                throw new StorageException("File not found: " + path);
            }

            byte[] content = Files.readAllBytes(filePath);
            log.debug("Retrieved file: {} (size: {} bytes)", path, content.length);

            return content;

        } catch (IOException e) {
            log.error("Failed to retrieve file: {}", path, e);
            throw new StorageException("Failed to retrieve file", e);
        }
    }

    @Override
    public void delete(String path) throws StorageException {
        try {
            Path filePath = Paths.get(path);

            if (Files.exists(filePath)) {
                Files.delete(filePath);
                log.info("Deleted file: {}", path);
            } else {
                log.warn("File not found for deletion: {}", path);
            }

        } catch (IOException e) {
            log.error("Failed to delete file: {}", path, e);
            throw new StorageException("Failed to delete file", e);
        }
    }

    @Override
    public boolean exists(String path) {
        return Files.exists(Paths.get(path));
    }

    private String generateUniqueFileName(String originalFileName) {
        String extension = "";
        int dotIndex = originalFileName.lastIndexOf('.');
        if (dotIndex > 0) {
            extension = originalFileName.substring(dotIndex);
        }
        return UUID.randomUUID().toString() + extension;
    }
}
