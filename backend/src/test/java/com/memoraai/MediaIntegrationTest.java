package com.memoraai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.memoraai.entity.Memory;
import com.memoraai.entity.MemoryMedia;
import com.memoraai.entity.Role;
import com.memoraai.entity.User;
import com.memoraai.repository.MemoryMediaRepository;
import com.memoraai.repository.MemoryRepository;
import com.memoraai.repository.UserRepository;
import com.memoraai.security.JwtService;
import com.memoraai.service.AiService;
import com.memoraai.service.StorageService;
import com.memoraai.service.VectorStoreService;
import io.minio.GetObjectArgs;
import io.minio.GetObjectResponse;
import io.minio.MinioClient;
import io.minio.ObjectWriteResponse;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
public class MediaIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private MemoryRepository memoryRepository;

    @SpyBean
    private MemoryMediaRepository memoryMediaRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtService jwtService;

    @MockBean
    private MinioClient minioClient;

    @MockBean
    private AiService aiService;

    @MockBean
    private VectorStoreService vectorStoreService;

    private User userA;
    private User userB;
    private String tokenA;
    private String tokenB;
    private Memory memoryA;

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

    private static final byte[] VALID_WEBM_HEADER = new byte[]{
            (byte) 0x1A, 0x45, (byte) 0xDF, (byte) 0xA3,
            0x01, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00
    };

    @BeforeEach
    void setUp() throws Exception {
        memoryMediaRepository.deleteAll();
        memoryRepository.deleteAll();
        userRepository.deleteAll();

        reset(minioClient);

        userA = userRepository.save(new User("User Alpha", "alpha@example.com", passwordEncoder.encode("Password123!"), Role.USER));
        userB = userRepository.save(new User("User Beta", "beta@example.com", passwordEncoder.encode("Password123!"), Role.USER));

        tokenA = jwtService.generateToken(userA);
        tokenB = jwtService.generateToken(userB);

        memoryA = memoryRepository.save(new Memory(userA, "Kyoto Trip", "Visiting temples in Kyoto", "Travel", "kyoto,japan"));

        // Default mock behavior for MinIO
        when(minioClient.putObject(any(PutObjectArgs.class))).thenReturn(mock(ObjectWriteResponse.class));
    }

    @Test
    @DisplayName("1. POST /api/memories/{memoryId}/media successfully uploads single image and persists metadata")
    void testSuccessfulSingleImageUpload() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "files", "temple.jpg", "image/jpeg", VALID_JPEG_HEADER
        );

        mockMvc.perform(multipart("/api/memories/" + memoryA.getId() + "/media")
                        .file(file)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenA))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").isNotEmpty())
                .andExpect(jsonPath("$[0].memoryId").value(memoryA.getId().toString()))
                .andExpect(jsonPath("$[0].fileName").value("temple.jpg"))
                .andExpect(jsonPath("$[0].contentType").value("image/jpeg"))
                .andExpect(jsonPath("$[0].fileSize").value(VALID_JPEG_HEADER.length))
                .andExpect(jsonPath("$[0].createdAt").isNotEmpty())
                .andExpect(jsonPath("$[0].s3Key").doesNotExist()); // Must not expose internal S3 key

        List<MemoryMedia> mediaList = memoryMediaRepository.findAllByMemoryId(memoryA.getId());
        assertThat(mediaList).hasSize(1);
        assertThat(mediaList.get(0).getFileName()).isEqualTo("temple.jpg");
        assertThat(mediaList.get(0).getS3Key()).startsWith("users/" + userA.getId() + "/memories/" + memoryA.getId() + "/");
        assertThat(mediaList.get(0).getS3Key()).endsWith(".jpg");

        verify(minioClient, times(1)).putObject(any(PutObjectArgs.class));
    }

    @Test
    @DisplayName("2. POST /api/memories/{memoryId}/media supports batch upload of multiple images")
    void testSuccessfulBatchImageUpload() throws Exception {
        MockMultipartFile file1 = new MockMultipartFile("files", "photo1.jpg", "image/jpeg", VALID_JPEG_HEADER);
        MockMultipartFile file2 = new MockMultipartFile("files", "photo2.png", "image/png", VALID_PNG_HEADER);

        mockMvc.perform(multipart("/api/memories/" + memoryA.getId() + "/media")
                        .file(file1)
                        .file(file2)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenA))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].fileName").value("photo1.jpg"))
                .andExpect(jsonPath("$[1].fileName").value("photo2.png"));

        assertThat(memoryMediaRepository.countByMemoryId(memoryA.getId())).isEqualTo(2);
        verify(minioClient, times(2)).putObject(any(PutObjectArgs.class));
    }

    @Test
    @DisplayName("3. POST upload rejects empty file with HTTP 400")
    void testUploadEmptyFileRejected() throws Exception {
        MockMultipartFile emptyFile = new MockMultipartFile("files", "empty.jpg", "image/jpeg", new byte[0]);

        mockMvc.perform(multipart("/api/memories/" + memoryA.getId() + "/media")
                        .file(emptyFile)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenA))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Invalid Media"))
                .andExpect(jsonPath("$.message", containsString("cannot be empty")));

        assertThat(memoryMediaRepository.countByMemoryId(memoryA.getId())).isEqualTo(0);
        verify(minioClient, never()).putObject(any(PutObjectArgs.class));
    }

    @Test
    @DisplayName("4. POST upload rejects unsupported plaintext file with HTTP 400")
    void testUploadUnsupportedFileTypeRejected() throws Exception {
        MockMultipartFile textFile = new MockMultipartFile(
                "files", "notes.txt", "text/plain", "This is plain text not an image".getBytes(StandardCharsets.UTF_8)
        );

        mockMvc.perform(multipart("/api/memories/" + memoryA.getId() + "/media")
                        .file(textFile)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenA))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Invalid Media"))
                .andExpect(jsonPath("$.message", containsString("Unsupported media type")));

        assertThat(memoryMediaRepository.countByMemoryId(memoryA.getId())).isEqualTo(0);
        verify(minioClient, never()).putObject(any(PutObjectArgs.class));
    }

    @Test
    @DisplayName("5. POST upload rejects spoofed PDF file disguised as JPEG with HTTP 400")
    void testUploadSpoofedPdfFileRejected() throws Exception {
        byte[] pdfBytes = "%PDF-1.7\n%Fake PDF content pretending to be an image".getBytes(StandardCharsets.UTF_8);
        MockMultipartFile spoofedFile = new MockMultipartFile("files", "malicious.jpg", "image/jpeg", pdfBytes);

        mockMvc.perform(multipart("/api/memories/" + memoryA.getId() + "/media")
                        .file(spoofedFile)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenA))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Invalid Media"))
                .andExpect(jsonPath("$.message", containsString("Unsupported media type")));

        assertThat(memoryMediaRepository.countByMemoryId(memoryA.getId())).isEqualTo(0);
        verify(minioClient, never()).putObject(any(PutObjectArgs.class));
    }

    @Test
    @DisplayName("6. POST upload rejects oversized image exceeding 10MB limit with HTTP 400")
    void testUploadOversizedFileRejected() throws Exception {
        long oversized = StorageService.MAX_FILE_SIZE + 1024L;
        MockMultipartFile oversizedFile = new MockMultipartFile("files", "huge.jpg", "image/jpeg", VALID_JPEG_HEADER) {
            @Override
            public long getSize() {
                return oversized;
            }
        };

        mockMvc.perform(multipart("/api/memories/" + memoryA.getId() + "/media")
                        .file(oversizedFile)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenA))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Invalid Media"))
                .andExpect(jsonPath("$.message", containsString("exceeds the maximum allowed limit")));

        assertThat(memoryMediaRepository.countByMemoryId(memoryA.getId())).isEqualTo(0);
        verify(minioClient, never()).putObject(any(PutObjectArgs.class));
    }

    @Test
    @DisplayName("7. POST upload rejects attempts to exceed the 5 images per memory limit")
    void testUploadExceedingFiveImagesLimitRejected() throws Exception {
        // Pre-populate 4 media items
        for (int i = 1; i <= 4; i++) {
            memoryMediaRepository.save(new MemoryMedia(memoryA, userA, "img" + i + ".jpg", "image/jpeg", 1000L, "key-" + i));
        }
        assertThat(memoryMediaRepository.countByMemoryId(memoryA.getId())).isEqualTo(4);

        // Attempting to upload 2 more images (would total 6) must be rejected
        MockMultipartFile file1 = new MockMultipartFile("files", "new1.jpg", "image/jpeg", VALID_JPEG_HEADER);
        MockMultipartFile file2 = new MockMultipartFile("files", "new2.jpg", "image/jpeg", VALID_JPEG_HEADER);

        mockMvc.perform(multipart("/api/memories/" + memoryA.getId() + "/media")
                        .file(file1)
                        .file(file2)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenA))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Invalid Media"))
                .andExpect(jsonPath("$.message", containsString("exceeds maximum limit of 5 images per memory")));

        // Count must remain 4 and no MinIO upload should have taken place
        assertThat(memoryMediaRepository.countByMemoryId(memoryA.getId())).isEqualTo(4);
        verify(minioClient, never()).putObject(any(PutObjectArgs.class));
    }

    @Test
    @DisplayName("8. User isolation: User B cannot upload to User A's memory (returns 404)")
    void testUserIsolation_UploadToOtherUsersMemoryReturns404() throws Exception {
        MockMultipartFile file = new MockMultipartFile("files", "infiltrate.jpg", "image/jpeg", VALID_JPEG_HEADER);

        mockMvc.perform(multipart("/api/memories/" + memoryA.getId() + "/media")
                        .file(file)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenB))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Not Found"));

        assertThat(memoryMediaRepository.countByMemoryId(memoryA.getId())).isEqualTo(0);
        verify(minioClient, never()).putObject(any(PutObjectArgs.class));
    }

    @Test
    @DisplayName("9. GET /api/memories/{memoryId}/media lists attachments in chronological order")
    void testGetMediaList() throws Exception {
        MemoryMedia m1 = memoryMediaRepository.save(new MemoryMedia(memoryA, userA, "pic1.jpg", "image/jpeg", 1000L, "key-1"));
        MemoryMedia m2 = memoryMediaRepository.save(new MemoryMedia(memoryA, userA, "pic2.png", "image/png", 2000L, "key-2"));

        mockMvc.perform(get("/api/memories/" + memoryA.getId() + "/media")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].fileName").value("pic1.jpg"))
                .andExpect(jsonPath("$[1].fileName").value("pic2.png"))
                .andExpect(jsonPath("$[0].s3Key").doesNotExist());
    }

    @Test
    @DisplayName("10. User isolation: User B cannot list attachments for User A's memory (returns 404)")
    void testUserIsolation_ListOtherUsersMemoryReturns404() throws Exception {
        memoryMediaRepository.save(new MemoryMedia(memoryA, userA, "secret.jpg", "image/jpeg", 1000L, "key-secret"));

        mockMvc.perform(get("/api/memories/" + memoryA.getId() + "/media")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenB))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Not Found"));
    }

    @Test
    @DisplayName("11. GET /api/memories/{memoryId}/media/{mediaId} streams verified image with security headers")
    void testSuccessfulDownloadMediaStreamAndHeaders() throws Exception {
        MemoryMedia media = memoryMediaRepository.save(
                new MemoryMedia(memoryA, userA, "bamboo_forest.jpg", "image/jpeg", (long) VALID_JPEG_HEADER.length, "key-bamboo")
        );

        // Mock MinIO getObject response
        GetObjectResponse mockResponse = mock(GetObjectResponse.class);
        ByteArrayInputStream bais = new ByteArrayInputStream(VALID_JPEG_HEADER);
        when(mockResponse.read(any(byte[].class), anyInt(), anyInt()))
                .thenAnswer(inv -> bais.read(inv.getArgument(0), inv.getArgument(1), inv.getArgument(2)));
        when(mockResponse.read(any(byte[].class)))
                .thenAnswer(inv -> bais.read(inv.getArgument(0)));
        when(mockResponse.read())
                .thenAnswer(inv -> bais.read());
        when(mockResponse.transferTo(any(java.io.OutputStream.class)))
                .thenAnswer(inv -> {
                    java.io.OutputStream os = inv.getArgument(0);
                    os.write(VALID_JPEG_HEADER);
                    return (long) VALID_JPEG_HEADER.length;
                });
        when(minioClient.getObject(any(GetObjectArgs.class))).thenReturn(mockResponse);

        mockMvc.perform(get("/api/memories/" + memoryA.getId() + "/media/" + media.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "image/jpeg"))
                .andExpect(header().string("Content-Length", String.valueOf(VALID_JPEG_HEADER.length)))
                .andExpect(header().string("Content-Disposition", "inline; filename=\"bamboo_forest.jpg\""))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("Cache-Control", "private, no-store"))
                .andExpect(content().bytes(VALID_JPEG_HEADER));
    }

    @Test
    @DisplayName("12. User isolation: User B cannot download User A's media attachment (returns 404)")
    void testUserIsolation_DownloadOtherUsersMediaReturns404() throws Exception {
        MemoryMedia media = memoryMediaRepository.save(
                new MemoryMedia(memoryA, userA, "private_doc.png", "image/png", 500L, "key-private")
        );

        mockMvc.perform(get("/api/memories/" + memoryA.getId() + "/media/" + media.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenB))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Not Found"));

        verify(minioClient, never()).getObject(any(GetObjectArgs.class));
    }

    @Test
    @DisplayName("13. Tampered memory ID / media ID combination returns 404")
    void testTamperedMemoryAndMediaIdCombinationReturns404() throws Exception {
        Memory memoryOther = memoryRepository.save(new Memory(userA, "Other Memory", "Notes", "General", null));
        MemoryMedia mediaOnFirst = memoryMediaRepository.save(
                new MemoryMedia(memoryA, userA, "scoped.jpg", "image/jpeg", 500L, "key-scoped")
        );

        // Attempt to request mediaOnFirst using memoryOther's ID
        mockMvc.perform(get("/api/memories/" + memoryOther.getId() + "/media/" + mediaOnFirst.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenA))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Not Found"));
    }

    @Test
    @DisplayName("14. DELETE /api/memories/{memoryId}/media/{mediaId} deletes MinIO object and metadata")
    void testSuccessfulDeleteMedia() throws Exception {
        MemoryMedia media = memoryMediaRepository.save(
                new MemoryMedia(memoryA, userA, "delete_me.jpg", "image/jpeg", 500L, "key-delete-target")
        );

        mockMvc.perform(delete("/api/memories/" + memoryA.getId() + "/media/" + media.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenA))
                .andExpect(status().isNoContent());

        ArgumentCaptor<RemoveObjectArgs> captor = ArgumentCaptor.forClass(RemoveObjectArgs.class);
        verify(minioClient, times(1)).removeObject(captor.capture());
        assertThat(captor.getValue().object()).isEqualTo("key-delete-target");

        assertThat(memoryMediaRepository.findById(media.getId())).isEmpty();
    }

    @Test
    @DisplayName("15. User isolation: User B cannot delete User A's media (returns 404)")
    void testUserIsolation_DeleteOtherUsersMediaReturns404() throws Exception {
        MemoryMedia media = memoryMediaRepository.save(
                new MemoryMedia(memoryA, userA, "safe.jpg", "image/jpeg", 500L, "key-safe")
        );

        mockMvc.perform(delete("/api/memories/" + memoryA.getId() + "/media/" + media.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenB))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Not Found"));

        verify(minioClient, never()).removeObject(any(RemoveObjectArgs.class));
        assertThat(memoryMediaRepository.findById(media.getId())).isPresent();
    }

    @Test
    @DisplayName("16. MinIO upload failure prevents metadata persistence and returns error")
    void testMinioUploadFailureDoesNotPersistMetadata() throws Exception {
        doThrow(new RuntimeException("MinIO network failure during upload"))
                .when(minioClient).putObject(any(PutObjectArgs.class));

        MockMultipartFile file = new MockMultipartFile("files", "fail.jpg", "image/jpeg", VALID_JPEG_HEADER);

        mockMvc.perform(multipart("/api/memories/" + memoryA.getId() + "/media")
                        .file(file)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenA))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error").value("Internal Server Error"));

        assertThat(memoryMediaRepository.countByMemoryId(memoryA.getId())).isEqualTo(0);
    }

    @Test
    @DisplayName("17. MinIO delete failure prevents metadata deletion from PostgreSQL")
    void testMinioDeleteFailureAbortsMediaDeletionAndPreservesMetadata() throws Exception {
        MemoryMedia media = memoryMediaRepository.save(
                new MemoryMedia(memoryA, userA, "retain_on_fail.jpg", "image/jpeg", 500L, "key-retain")
        );

        doThrow(new RuntimeException("MinIO disk timeout on remove"))
                .when(minioClient).removeObject(any(RemoveObjectArgs.class));

        mockMvc.perform(delete("/api/memories/" + memoryA.getId() + "/media/" + media.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenA))
                .andExpect(status().isInternalServerError());

        // Crucial safety check: Database record MUST NOT be deleted when MinIO fails!
        Optional<MemoryMedia> stillPresent = memoryMediaRepository.findById(media.getId());
        assertThat(stillPresent).isPresent();
    }

    @Test
    @DisplayName("18. DELETE /api/memories/{memoryId} removes all associated MinIO objects before cascading DB delete")
    void testDeleteMemoryCleansUpAllAttachedMinioObjects() throws Exception {
        MemoryMedia m1 = memoryMediaRepository.save(new MemoryMedia(memoryA, userA, "a.jpg", "image/jpeg", 100L, "s3-a"));
        MemoryMedia m2 = memoryMediaRepository.save(new MemoryMedia(memoryA, userA, "b.jpg", "image/jpeg", 200L, "s3-b"));

        mockMvc.perform(delete("/api/memories/" + memoryA.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenA))
                .andExpect(status().isNoContent());

        ArgumentCaptor<RemoveObjectArgs> captor = ArgumentCaptor.forClass(RemoveObjectArgs.class);
        verify(minioClient, times(2)).removeObject(captor.capture());

        List<String> removedKeys = captor.getAllValues().stream().map(RemoveObjectArgs::object).toList();
        assertThat(removedKeys).containsExactlyInAnyOrder("s3-a", "s3-b");

        // Verify DB cascade deleted the memory and its media rows
        assertThat(memoryRepository.findById(memoryA.getId())).isEmpty();
        assertThat(memoryMediaRepository.countByMemoryId(memoryA.getId())).isEqualTo(0);
    }

    @Test
    @DisplayName("19. When MinIO fails during memory deletion, memory and media records are preserved in database")
    void testDeleteMemoryMinioFailureAbortsMemoryDeletionAndPreservesDatabaseState() throws Exception {
        MemoryMedia m1 = memoryMediaRepository.save(new MemoryMedia(memoryA, userA, "a.jpg", "image/jpeg", 100L, "s3-fail-key"));

        doThrow(new RuntimeException("MinIO storage unavailable"))
                .when(minioClient).removeObject(any(RemoveObjectArgs.class));

        mockMvc.perform(delete("/api/memories/" + memoryA.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenA))
                .andExpect(status().isInternalServerError());

        // Keys and records MUST NOT be lost!
        assertThat(memoryRepository.findById(memoryA.getId())).isPresent();
        assertThat(memoryMediaRepository.countByMemoryId(memoryA.getId())).isEqualTo(1);
    }

    @Test
    @DisplayName("20. Database persistence failure triggers MinIO rollback compensation")
    void testUploadDatabasePersistFailureTriggersRollbackCompensation() throws Exception {
        doThrow(new DataIntegrityViolationException("Simulated database constraint/commit failure"))
                .when(memoryMediaRepository).save(any(MemoryMedia.class));

        MockMultipartFile file = new MockMultipartFile("files", "persist_fail.jpg", "image/jpeg", VALID_JPEG_HEADER);

        mockMvc.perform(multipart("/api/memories/" + memoryA.getId() + "/media")
                        .file(file)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenA))
                .andExpect(status().isInternalServerError());

        // Verify that MinIO upload happened, and then compensation removeObject was invoked
        verify(minioClient, times(1)).putObject(any(PutObjectArgs.class));
        verify(minioClient, times(1)).removeObject(any(RemoveObjectArgs.class));
        assertThat(memoryMediaRepository.countByMemoryId(memoryA.getId())).isEqualTo(0);
    }

    @Test
    @DisplayName("21. When upload rollback encounters a MinIO cleanup error, original failure is returned without masking")
    void testUploadRollbackWhenCleanupFailsLogsErrorWithoutMaskingFailure() throws Exception {
        doThrow(new DataIntegrityViolationException("Simulated database failure"))
                .when(memoryMediaRepository).save(any(MemoryMedia.class));
        doThrow(new RuntimeException("MinIO disk timeout during rollback cleanup"))
                .when(minioClient).removeObject(any(RemoveObjectArgs.class));

        MockMultipartFile file = new MockMultipartFile("files", "double_fail.jpg", "image/jpeg", VALID_JPEG_HEADER);

        mockMvc.perform(multipart("/api/memories/" + memoryA.getId() + "/media")
                        .file(file)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenA))
                .andExpect(status().isInternalServerError());

        assertThat(memoryMediaRepository.countByMemoryId(memoryA.getId())).isEqualTo(0);
    }

    @Test
    @DisplayName("22. Concurrent uploads to the same memory serialize and cannot exceed 5 attachments")
    void testConcurrentUploadsDoNotExceedFiveImagesLimit() throws Exception {
        // Pre-populate with 3 media items
        for (int i = 1; i <= 3; i++) {
            memoryMediaRepository.save(new MemoryMedia(memoryA, userA, "init" + i + ".jpg", "image/jpeg", 1000L, "key-init-" + i));
        }
        assertThat(memoryMediaRepository.countByMemoryId(memoryA.getId())).isEqualTo(3);

        int threads = 2;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch readyLatch = new CountDownLatch(threads);
        CountDownLatch startLatch = new CountDownLatch(1);
        List<Future<Integer>> futures = new ArrayList<>();

        for (int i = 1; i <= threads; i++) {
            final int threadId = i;
            futures.add(executor.submit(() -> {
                readyLatch.countDown();
                startLatch.await(); // Synchronize release
                MockMultipartFile f1 = new MockMultipartFile("files", "c" + threadId + "_1.jpg", "image/jpeg", VALID_JPEG_HEADER);
                MockMultipartFile f2 = new MockMultipartFile("files", "c" + threadId + "_2.jpg", "image/jpeg", VALID_JPEG_HEADER);
                return mockMvc.perform(multipart("/api/memories/" + memoryA.getId() + "/media")
                                .file(f1)
                                .file(f2)
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenA))
                        .andReturn()
                        .getResponse()
                        .getStatus();
            }));
        }

        readyLatch.await(5, TimeUnit.SECONDS);
        startLatch.countDown(); // Fire both threads simultaneously

        List<Integer> statuses = new ArrayList<>();
        for (Future<Integer> future : futures) {
            statuses.add(future.get(10, TimeUnit.SECONDS));
        }
        executor.shutdown();

        // Exactly one request must succeed (201 Created) and one must be rejected (400 Bad Request)
        assertThat(statuses).containsExactlyInAnyOrder(201, 400);

        // Crucial assertion: Total media count in database MUST be exactly 5, never 7!
        assertThat(memoryMediaRepository.countByMemoryId(memoryA.getId())).isEqualTo(5);
    }

    @Test
    @DisplayName("21. POST /api/memories/{memoryId}/media successfully uploads voice audio (WebM) and persists metadata")
    void testSuccessfulVoiceAudioUpload() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "files", "voice_reflection.webm", "audio/webm", VALID_WEBM_HEADER
        );

        mockMvc.perform(multipart("/api/memories/" + memoryA.getId() + "/media")
                        .file(file)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenA))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").isNotEmpty())
                .andExpect(jsonPath("$[0].memoryId").value(memoryA.getId().toString()))
                .andExpect(jsonPath("$[0].fileName").value("voice_reflection.webm"))
                .andExpect(jsonPath("$[0].contentType").value("audio/webm"))
                .andExpect(jsonPath("$[0].fileSize").value(VALID_WEBM_HEADER.length))
                .andExpect(jsonPath("$[0].createdAt").isNotEmpty())
                .andExpect(jsonPath("$[0].s3Key").doesNotExist());

        List<MemoryMedia> mediaList = memoryMediaRepository.findAllByMemoryId(memoryA.getId());
        assertThat(mediaList).hasSize(1);
        assertThat(mediaList.get(0).getFileName()).isEqualTo("voice_reflection.webm");
        assertThat(mediaList.get(0).getContentType()).isEqualTo("audio/webm");
        assertThat(mediaList.get(0).getS3Key()).startsWith("users/" + userA.getId() + "/memories/" + memoryA.getId() + "/");
        assertThat(mediaList.get(0).getS3Key()).endsWith(".webm");

        ArgumentCaptor<PutObjectArgs> captor = ArgumentCaptor.forClass(PutObjectArgs.class);
        verify(minioClient, atLeastOnce()).putObject(captor.capture());
        assertThat(captor.getValue().contentType()).isEqualTo("audio/webm");
    }

    @Test
    @DisplayName("22. GET /api/memories/{memoryId}/media/{mediaId} streams verified audio with correct Content-Type")
    void testSuccessfulAudioStreamRetrieval() throws Exception {
        MemoryMedia media = memoryMediaRepository.save(
                new MemoryMedia(memoryA, userA, "voice_memo.webm", "audio/webm", (long) VALID_WEBM_HEADER.length, "key-voice-audio")
        );

        GetObjectResponse mockResponse = mock(GetObjectResponse.class);
        ByteArrayInputStream bais = new ByteArrayInputStream(VALID_WEBM_HEADER);
        when(mockResponse.read(any(byte[].class), anyInt(), anyInt()))
                .thenAnswer(inv -> bais.read(inv.getArgument(0), inv.getArgument(1), inv.getArgument(2)));
        when(mockResponse.read(any(byte[].class)))
                .thenAnswer(inv -> bais.read(inv.getArgument(0)));
        when(mockResponse.read())
                .thenAnswer(inv -> bais.read());
        when(mockResponse.transferTo(any(java.io.OutputStream.class)))
                .thenAnswer(inv -> {
                    java.io.OutputStream os = inv.getArgument(0);
                    os.write(VALID_WEBM_HEADER);
                    return (long) VALID_WEBM_HEADER.length;
                });
        when(minioClient.getObject(any(GetObjectArgs.class))).thenReturn(mockResponse);

        mockMvc.perform(get("/api/memories/" + memoryA.getId() + "/media/" + media.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "audio/webm"))
                .andExpect(header().string("Content-Length", String.valueOf(VALID_WEBM_HEADER.length)))
                .andExpect(header().string("Content-Disposition", "inline; filename=\"voice_memo.webm\""))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("Cache-Control", "private, no-store"))
                .andExpect(content().bytes(VALID_WEBM_HEADER));
    }

    @Test
    @DisplayName("23. User isolation: User B cannot download User A's audio recording (returns 404)")
    void testUserIsolation_DownloadOtherUsersAudioReturns404() throws Exception {
        MemoryMedia audioMedia = memoryMediaRepository.save(
                new MemoryMedia(memoryA, userA, "secret_audio.webm", "audio/webm", 1024L, "key-secret-audio")
        );

        mockMvc.perform(get("/api/memories/" + memoryA.getId() + "/media/" + audioMedia.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenB))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Not Found"));

        verify(minioClient, never()).getObject(any(GetObjectArgs.class));
    }

    @Test
    @DisplayName("24. POST upload rejects audio larger than 25MB with HTTP 400")
    void testUploadOversizedAudioFileRejected() throws Exception {
        long oversized = StorageService.MAX_AUDIO_SIZE + 1L; // > 25MB
        MockMultipartFile oversizedAudio = new MockMultipartFile(
                "files", "giant.webm", "audio/webm", VALID_WEBM_HEADER
        ) {
            @Override
            public long getSize() {
                return oversized;
            }
        };

        mockMvc.perform(multipart("/api/memories/" + memoryA.getId() + "/media")
                        .file(oversizedAudio)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenA))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Invalid Media"))
                .andExpect(jsonPath("$.message", containsString("exceeds the maximum allowed limit")));

        assertThat(memoryMediaRepository.countByMemoryId(memoryA.getId())).isEqualTo(0);
    }
}
