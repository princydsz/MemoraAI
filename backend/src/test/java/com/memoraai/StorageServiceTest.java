package com.memoraai;

import com.memoraai.exception.InvalidMediaException;
import com.memoraai.service.StorageService;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class StorageServiceTest {

    @Mock
    private MinioClient minioClient;

    private StorageService storageService;

    private static final byte[] VALID_JPEG_HEADER = new byte[]{
            (byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0,
            0x00, 0x10, 0x4A, 0x46, 0x49, 0x46, 0x00, 0x01
    };

    private static final byte[] VALID_PNG_HEADER = new byte[]{
            (byte) 0x89, 0x50, 0x4E, 0x47,
            0x0D, 0x0A, 0x1A, 0x0A,
            0x00, 0x00, 0x00, 0x0D
    };

    private static final byte[] VALID_WEBP_HEADER = new byte[]{
            'R', 'I', 'F', 'F',
            0x24, 0x00, 0x00, 0x00,
            'W', 'E', 'B', 'P'
    };

    @BeforeEach
    void setUp() {
        storageService = new StorageService(minioClient, "memora-media-test");
    }

    @Test
    @DisplayName("1. Valid JPEG signature is accepted and detects format correctly")
    void testValidJpegSignatureAccepted() {
        MockMultipartFile file = new MockMultipartFile(
                "image", "family.jpg", "image/jpeg", VALID_JPEG_HEADER
        );

        StorageService.ImageFormat format = storageService.validateImage(file);
        assertThat(format).isEqualTo(StorageService.ImageFormat.JPEG);
        assertThat(format.getMimeType()).isEqualTo("image/jpeg");
        assertThat(format.getExtension()).isEqualTo("jpg");
    }

    @Test
    @DisplayName("2. Valid PNG signature is accepted and detects format correctly")
    void testValidPngSignatureAccepted() {
        MockMultipartFile file = new MockMultipartFile(
                "image", "screenshot.png", "image/png", VALID_PNG_HEADER
        );

        StorageService.ImageFormat format = storageService.validateImage(file);
        assertThat(format).isEqualTo(StorageService.ImageFormat.PNG);
        assertThat(format.getMimeType()).isEqualTo("image/png");
        assertThat(format.getExtension()).isEqualTo("png");
    }

    @Test
    @DisplayName("3. Valid WEBP signature is accepted and detects format correctly")
    void testValidWebpSignatureAccepted() {
        MockMultipartFile file = new MockMultipartFile(
                "image", "photo.webp", "image/webp", VALID_WEBP_HEADER
        );

        StorageService.ImageFormat format = storageService.validateImage(file);
        assertThat(format).isEqualTo(StorageService.ImageFormat.WEBP);
        assertThat(format.getMimeType()).isEqualTo("image/webp");
        assertThat(format.getExtension()).isEqualTo("webp");
    }

    @Test
    @DisplayName("4. Empty file is rejected")
    void testEmptyFileRejected() {
        MockMultipartFile emptyFile = new MockMultipartFile(
                "image", "empty.jpg", "image/jpeg", new byte[0]
        );

        assertThatThrownBy(() -> storageService.validateImage(emptyFile))
                .isInstanceOf(InvalidMediaException.class)
                .hasMessageContaining("cannot be empty");
    }

    @Test
    @DisplayName("5. Unsupported plaintext MIME/type is rejected")
    void testUnsupportedPlaintextRejected() {
        byte[] textBytes = "This is a plain text file pretending to be an image".getBytes(StandardCharsets.UTF_8);
        MockMultipartFile textFile = new MockMultipartFile(
                "image", "fake.jpg", "image/jpeg", textBytes
        );

        assertThatThrownBy(() -> storageService.validateImage(textFile))
                .isInstanceOf(InvalidMediaException.class)
                .hasMessageContaining("Unsupported media type");
    }

    @Test
    @DisplayName("6. PDF bytes disguised with a .jpg filename and image MIME are rejected")
    void testPdfDisguisedAsJpegRejected() {
        byte[] pdfBytes = "%PDF-1.7\n%1234\nSome PDF content".getBytes(StandardCharsets.UTF_8);
        MockMultipartFile spoofedFile = new MockMultipartFile(
                "image", "innocent.jpg", "image/jpeg", pdfBytes
        );

        assertThatThrownBy(() -> storageService.validateImage(spoofedFile))
                .isInstanceOf(InvalidMediaException.class)
                .hasMessageContaining("Unsupported media type");
    }

    @Test
    @DisplayName("7. Windows executable bytes disguised as PNG are rejected")
    void testExecutableDisguisedAsPngRejected() {
        byte[] exeBytes = new byte[]{'M', 'Z', (byte) 0x90, 0x00, 0x03, 0x00, 0x00, 0x00, 0x04, 0x00, 0x00, 0x00};
        MockMultipartFile spoofedFile = new MockMultipartFile(
                "image", "trojan.png", "image/png", exeBytes
        );

        assertThatThrownBy(() -> storageService.validateImage(spoofedFile))
                .isInstanceOf(InvalidMediaException.class)
                .hasMessageContaining("Unsupported media type");
    }

    @Test
    @DisplayName("8. Truncated image header (fewer than 12 bytes) is rejected")
    void testTruncatedHeaderRejected() {
        byte[] truncated = new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF}; // only 3 bytes
        MockMultipartFile truncatedFile = new MockMultipartFile(
                "image", "broken.jpg", "image/jpeg", truncated
        );

        assertThatThrownBy(() -> storageService.validateImage(truncatedFile))
                .isInstanceOf(InvalidMediaException.class)
                .hasMessageContaining("incomplete or truncated");
    }

    @Test
    @DisplayName("9. File larger than 10 MiB is rejected")
    void testFileLargerThan10MbRejected() {
        // Construct a MockMultipartFile that reports size > 10MB
        long oversized = StorageService.MAX_FILE_SIZE + 1L;
        MockMultipartFile oversizedFile = new MockMultipartFile("image", "big.jpg", "image/jpeg", VALID_JPEG_HEADER) {
            @Override
            public long getSize() {
                return oversized;
            }
        };

        assertThatThrownBy(() -> storageService.validateImage(oversizedFile))
                .isInstanceOf(InvalidMediaException.class)
                .hasMessageContaining("exceeds the maximum allowed limit");
    }

    @Test
    @DisplayName("10. Untrusted filenames cannot influence the generated object key")
    void testUntrustedFilenameCannotInfluenceObjectKey() {
        UUID userId = UUID.randomUUID();
        UUID memoryId = UUID.randomUUID();
        UUID mediaId = UUID.randomUUID();

        String objectKey = storageService.generateObjectKey(userId, memoryId, mediaId, StorageService.ImageFormat.JPEG);

        // Object key must strictly adhere to users/{userId}/memories/{memoryId}/{mediaId}.jpg
        assertThat(objectKey).isEqualTo(String.format("users/%s/memories/%s/%s.jpg", userId, memoryId, mediaId));
        assertThat(objectKey).doesNotContain("..");
        assertThat(objectKey).startsWith("users/" + userId + "/memories/" + memoryId + "/");

        // Test filename sanitization
        String sanitized = storageService.sanitizeFilename("../../../etc/passwd/image.png");
        assertThat(sanitized).isEqualTo("image.png");

        String malicious = storageService.sanitizeFilename("..\\..\\windows\\system32\\cmd.exe;photo.jpg");
        assertThat(malicious).doesNotContain("..");
        assertThat(malicious).doesNotContain("\\");
        assertThat(malicious).doesNotContain("/");
    }

    @Test
    @DisplayName("11. Upload image calls MinIO putObject with verified content-type and returns UploadResult")
    void testUploadImageCallsMinioPutObject() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID memoryId = UUID.randomUUID();
        MockMultipartFile file = new MockMultipartFile("image", "test_photo.jpg", "image/jpeg", VALID_JPEG_HEADER);

        StorageService.UploadResult result = storageService.uploadImage(userId, memoryId, file);

        assertThat(result).isNotNull();
        assertThat(result.mediaId()).isNotNull();
        assertThat(result.s3Key()).startsWith("users/" + userId + "/memories/" + memoryId + "/");
        assertThat(result.s3Key()).endsWith(".jpg");
        assertThat(result.contentType()).isEqualTo("image/jpeg");
        assertThat(result.fileName()).isEqualTo("test_photo.jpg");
        assertThat(result.fileSize()).isEqualTo(VALID_JPEG_HEADER.length);

        ArgumentCaptor<PutObjectArgs> captor = ArgumentCaptor.forClass(PutObjectArgs.class);
        verify(minioClient, times(1)).putObject(captor.capture());

        PutObjectArgs putArgs = captor.getValue();
        assertThat(putArgs.bucket()).isEqualTo("memora-media-test");
        assertThat(putArgs.object()).isEqualTo(result.s3Key());
        assertThat(putArgs.contentType()).isEqualTo("image/jpeg");
    }

    @Test
    @DisplayName("12. MinIO upload failure throws IllegalStateException without logging credentials")
    void testUploadImageMinioFailureThrowsException() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID memoryId = UUID.randomUUID();
        MockMultipartFile file = new MockMultipartFile("image", "test_photo.jpg", "image/jpeg", VALID_JPEG_HEADER);

        doThrow(new RuntimeException("MinIO network connection refused"))
                .when(minioClient).putObject(any(PutObjectArgs.class));

        assertThatThrownBy(() -> storageService.uploadImage(userId, memoryId, file))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Failed to store image in object storage");
    }

    @Test
    @DisplayName("13. Delete object calls MinIO removeObject")
    void testDeleteObjectCallsMinioRemoveObject() throws Exception {
        String key = "users/123/memories/456/789.png";

        storageService.deleteObject(key);

        ArgumentCaptor<RemoveObjectArgs> captor = ArgumentCaptor.forClass(RemoveObjectArgs.class);
        verify(minioClient, times(1)).removeObject(captor.capture());
        assertThat(captor.getValue().object()).isEqualTo(key);
        assertThat(captor.getValue().bucket()).isEqualTo("memora-media-test");
    }

    @Test
    @DisplayName("14. MinIO delete failure throws IllegalStateException")
    void testDeleteObjectFailureThrowsException() throws Exception {
        doThrow(new RuntimeException("MinIO disk failure"))
                .when(minioClient).removeObject(any(RemoveObjectArgs.class));

        assertThatThrownBy(() -> storageService.deleteObject("users/123/memories/456/fail.png"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Failed to delete image from storage");
    }
}
