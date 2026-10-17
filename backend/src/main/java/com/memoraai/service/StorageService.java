package com.memoraai.service;

import com.memoraai.exception.InvalidMediaException;
import io.minio.GetObjectArgs;
import io.minio.GetObjectResponse;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.InputStream;
import java.util.UUID;

/**
 * Storage service responsible for image validation and interaction with MinIO object storage.
 *
 * <p><strong>Security & Format Limitation Notice:</strong>
 * Magic-byte inspection is a fast, first-line signature check confirming container signatures
 * (JPEG, PNG, WEBP). It does not guarantee that every pixel stream is free of complex malformations,
 * parser vulnerabilities, or decompression bomb characteristics. Additional downstream protections
 * (e.g., maximum memory limits, strict Content-Type headers, inline-only delivery) complement this check.
 */
@Service
public class StorageService {

    private static final Logger log = LoggerFactory.getLogger(StorageService.class);

    public static final long MAX_FILE_SIZE = 10 * 1024 * 1024L; // 10 MiB

    private final MinioClient minioClient;
    private final String bucketName;

    public StorageService(
            MinioClient minioClient,
            @Value("${minio.bucket-name:memora-media}") String bucketName) {
        this.minioClient = minioClient;
        this.bucketName = bucketName;
    }

    public enum ImageFormat {
        JPEG("image/jpeg", "jpg"),
        PNG("image/png", "png"),
        WEBP("image/webp", "webp");

        private final String mimeType;
        private final String extension;

        ImageFormat(String mimeType, String extension) {
            this.mimeType = mimeType;
            this.extension = extension;
        }

        public String getMimeType() {
            return mimeType;
        }

        public String getExtension() {
            return extension;
        }
    }

    public record UploadResult(
            UUID mediaId,
            String s3Key,
            String contentType,
            String fileName,
            long fileSize
    ) {}

    /**
     * Inspects actual byte signatures of the uploaded file to detect its image format.
     * Does not trust client-supplied extensions or Content-Type headers.
     */
    public ImageFormat detectFormat(byte[] header) {
        if (header == null || header.length < 12) {
            throw new InvalidMediaException("Invalid image: file header is incomplete or truncated.");
        }

        // JPEG: FF D8 FF
        if ((header[0] & 0xFF) == 0xFF && (header[1] & 0xFF) == 0xD8 && (header[2] & 0xFF) == 0xFF) {
            return ImageFormat.JPEG;
        }

        // PNG: 89 50 4E 47 0D 0A 1A 0A
        if ((header[0] & 0xFF) == 0x89 && header[1] == 0x50 && header[2] == 0x4E && header[3] == 0x47
                && header[4] == 0x0D && header[5] == 0x0A && header[6] == 0x1A && header[7] == 0x0A) {
            return ImageFormat.PNG;
        }

        // WEBP: 'RIFF' .... 'WEBP'
        if (header[0] == 'R' && header[1] == 'I' && header[2] == 'F' && header[3] == 'F'
                && header[8] == 'W' && header[9] == 'E' && header[10] == 'B' && header[11] == 'P') {
            return ImageFormat.WEBP;
        }

        throw new InvalidMediaException("Unsupported media type: only JPEG, PNG, and WEBP images are supported.");
    }

    /**
     * Validates file size, empty status, and header bytes.
     */
    public ImageFormat validateImage(MultipartFile file) {
        if (file == null || file.isEmpty() || file.getSize() <= 0) {
            throw new InvalidMediaException("Image file cannot be empty.");
        }

        if (file.getSize() > MAX_FILE_SIZE) {
            throw new InvalidMediaException(String.format(
                    "Image file size (%d bytes) exceeds the maximum allowed limit of 10MB (%d bytes).",
                    file.getSize(), MAX_FILE_SIZE));
        }

        byte[] header = new byte[12];
        try (InputStream is = file.getInputStream()) {
            int bytesRead = is.readNBytes(header, 0, 12);
            if (bytesRead < 12) {
                throw new InvalidMediaException("Invalid image: file header is incomplete or truncated.");
            }
        } catch (InvalidMediaException e) {
            throw e;
        } catch (Exception e) {
            throw new InvalidMediaException("Failed to read image content for validation: " + e.getMessage(), e);
        }

        return detectFormat(header);
    }

    /**
     * Sanitizes user-provided filename to prevent path traversal or control character injection.
     * Normalizes both forward and backward slashes regardless of host OS and eliminates directory traversal dots.
     */
    public String sanitizeFilename(String filename) {
        if (filename == null || filename.trim().isEmpty()) {
            return "unnamed_image";
        }
        // Normalize backslashes to forward slashes across all platforms
        String normalized = filename.replace('\\', '/');
        int lastSlash = normalized.lastIndexOf('/');
        String baseName = (lastSlash >= 0) ? normalized.substring(lastSlash + 1) : normalized;

        // Strip any remaining directory traversal dots and non-whitelisted characters
        String clean = baseName.replace("..", "_")
                .replaceAll("[^a-zA-Z0-9._\\- ]", "_")
                .trim();

        while (clean.startsWith(".")) {
            clean = clean.substring(1).trim();
        }

        if (clean.isEmpty() || clean.equals("_")) {
            return "unnamed_image";
        }
        if (clean.length() > 200) {
            clean = clean.substring(0, 200);
        }
        return clean;
    }

    /**
     * Generates a deterministic, server-controlled object key.
     * Never incorporates untrusted path components.
     */
    public String generateObjectKey(UUID userId, UUID memoryId, UUID mediaId, ImageFormat format) {
        if (userId == null || memoryId == null || mediaId == null || format == null) {
            throw new IllegalArgumentException("User ID, Memory ID, Media ID, and Format are required to construct object key.");
        }
        return String.format("users/%s/memories/%s/%s.%s", userId, memoryId, mediaId, format.getExtension());
    }

    /**
     * Uploads validated image to MinIO with server-generated key.
     */
    public UploadResult uploadImage(UUID userId, UUID memoryId, MultipartFile file) {
        if (userId == null || memoryId == null) {
            throw new IllegalArgumentException("User ID and Memory ID are required for image upload.");
        }

        ImageFormat format = validateImage(file);
        UUID mediaId = UUID.randomUUID();
        String s3Key = generateObjectKey(userId, memoryId, mediaId, format);
        String safeFilename = sanitizeFilename(file.getOriginalFilename());

        try (InputStream is = file.getInputStream()) {
            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(bucketName)
                            .object(s3Key)
                            .stream(is, file.getSize(), -1)
                            .contentType(format.getMimeType())
                            .build()
            );
            log.info("Uploaded image {} ({} bytes, MIME: {}) for memory {}",
                    mediaId, file.getSize(), format.getMimeType(), memoryId);
        } catch (Exception e) {
            log.error("Failed to store image in MinIO: {}", e.getMessage());
            throw new IllegalStateException("Failed to store image in object storage: " + e.getMessage(), e);
        }

        return new UploadResult(mediaId, s3Key, format.getMimeType(), safeFilename, file.getSize());
    }

    /**
     * Streams object from MinIO by internal key.
     */
    public GetObjectResponse getObject(String s3Key) {
        if (s3Key == null || s3Key.trim().isEmpty()) {
            throw new IllegalArgumentException("Object key cannot be blank.");
        }

        try {
            return minioClient.getObject(
                    GetObjectArgs.builder()
                            .bucket(bucketName)
                            .object(s3Key.trim())
                            .build()
            );
        } catch (Exception e) {
            log.error("Failed to retrieve object from MinIO: {}", e.getMessage());
            throw new IllegalStateException("Failed to read image from storage: " + e.getMessage(), e);
        }
    }

    /**
     * Deletes object from MinIO by internal key.
     */
    public void deleteObject(String s3Key) {
        if (s3Key == null || s3Key.trim().isEmpty()) {
            return;
        }

        try {
            minioClient.removeObject(
                    RemoveObjectArgs.builder()
                            .bucket(bucketName)
                            .object(s3Key.trim())
                            .build()
            );
            log.info("Deleted MinIO object with key '{}'", s3Key.trim());
        } catch (Exception e) {
            log.error("Failed to delete object from MinIO: {}", e.getMessage());
            throw new IllegalStateException("Failed to delete image from storage: " + e.getMessage(), e);
        }
    }

    public String getBucketName() {
        return bucketName;
    }
}
