package com.memoraai;

import com.memoraai.dto.ErrorResponse;
import com.memoraai.entity.Memory;
import com.memoraai.entity.MemoryMedia;
import com.memoraai.entity.Role;
import com.memoraai.entity.User;
import com.memoraai.exception.GlobalExceptionHandler;
import com.memoraai.exception.InvalidMediaException;
import com.memoraai.repository.MemoryMediaRepository;
import com.memoraai.repository.MemoryRepository;
import com.memoraai.repository.UserRepository;
import com.memoraai.service.AiService;
import com.memoraai.service.VectorStoreService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
public class MemoryMediaRepositoryTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private MemoryRepository memoryRepository;

    @Autowired
    private MemoryMediaRepository memoryMediaRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private GlobalExceptionHandler globalExceptionHandler;

    @MockBean
    private AiService aiService;

    @MockBean
    private VectorStoreService vectorStoreService;

    private User userA;
    private User userB;
    private Memory memoryA;

    @BeforeEach
    void setUp() {
        memoryMediaRepository.deleteAll();
        memoryRepository.deleteAll();
        userRepository.deleteAll();

        userA = userRepository.save(new User("User Alpha", "alpha@example.com", passwordEncoder.encode("Password123!"), Role.USER));
        userB = userRepository.save(new User("User Beta", "beta@example.com", passwordEncoder.encode("Password123!"), Role.USER));

        memoryA = memoryRepository.save(new Memory(userA, "Vacation in Tokyo", "Visiting Tokyo Tower", "Travel", "tokyo,travel"));
    }

    @Test
    @DisplayName("1. Save and retrieve MemoryMedia entity with all metadata fields")
    void testSaveAndRetrieveMemoryMedia() {
        MemoryMedia media = new MemoryMedia(
                memoryA,
                userA,
                "tokyo_tower.jpg",
                "image/jpeg",
                2048576L,
                "users/" + userA.getId() + "/memories/" + memoryA.getId() + "/tokyo_tower.jpg"
        );

        MemoryMedia saved = memoryMediaRepository.save(media);

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getFileName()).isEqualTo("tokyo_tower.jpg");
        assertThat(saved.getContentType()).isEqualTo("image/jpeg");
        assertThat(saved.getFileSize()).isEqualTo(2048576L);
        assertThat(saved.getS3Key()).startsWith("users/");
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getMemory().getId()).isEqualTo(memoryA.getId());
        assertThat(saved.getUser().getId()).isEqualTo(userA.getId());
    }

    @Test
    @DisplayName("2. countByMemoryId correctly counts media items for a specific memory")
    void testCountByMemoryId() {
        assertThat(memoryMediaRepository.countByMemoryId(memoryA.getId())).isEqualTo(0);

        memoryMediaRepository.save(new MemoryMedia(memoryA, userA, "photo1.png", "image/png", 1024L, "key-1"));
        memoryMediaRepository.save(new MemoryMedia(memoryA, userA, "photo2.png", "image/png", 2048L, "key-2"));

        assertThat(memoryMediaRepository.countByMemoryId(memoryA.getId())).isEqualTo(2);
    }

    @Test
    @DisplayName("3. findAllByMemoryIdAndUserIdOrderByCreatedAtAsc returns only owned media in order")
    void testFindAllByMemoryIdAndUserId() {
        MemoryMedia m1 = memoryMediaRepository.save(new MemoryMedia(memoryA, userA, "a.jpg", "image/jpeg", 1000L, "key-a"));
        MemoryMedia m2 = memoryMediaRepository.save(new MemoryMedia(memoryA, userA, "b.jpg", "image/jpeg", 2000L, "key-b"));

        List<MemoryMedia> resultsA = memoryMediaRepository.findAllByMemoryIdAndUserIdOrderByCreatedAtAsc(memoryA.getId(), userA.getId());
        assertThat(resultsA).hasSize(2);
        assertThat(resultsA.get(0).getFileName()).isEqualTo("a.jpg");
        assertThat(resultsA.get(1).getFileName()).isEqualTo("b.jpg");

        // User B cannot see User A's media for this memory
        List<MemoryMedia> resultsB = memoryMediaRepository.findAllByMemoryIdAndUserIdOrderByCreatedAtAsc(memoryA.getId(), userB.getId());
        assertThat(resultsB).isEmpty();
    }

    @Test
    @DisplayName("4. findByIdAndUserId prevents cross-user access to media items")
    void testFindByIdAndUserId_UserIsolation() {
        MemoryMedia media = memoryMediaRepository.save(new MemoryMedia(memoryA, userA, "private.jpg", "image/jpeg", 5000L, "key-private"));

        Optional<MemoryMedia> foundByOwner = memoryMediaRepository.findByIdAndUserId(media.getId(), userA.getId());
        assertThat(foundByOwner).isPresent();
        assertThat(foundByOwner.get().getFileName()).isEqualTo("private.jpg");

        Optional<MemoryMedia> foundByOtherUser = memoryMediaRepository.findByIdAndUserId(media.getId(), userB.getId());
        assertThat(foundByOtherUser).isEmpty();
    }

    @Test
    @DisplayName("5. findByIdAndMemoryIdAndUserId verifies memory, media, and user match")
    void testFindByIdAndMemoryIdAndUserId() {
        MemoryMedia media = memoryMediaRepository.save(new MemoryMedia(memoryA, userA, "scoped.webp", "image/webp", 3000L, "key-scoped"));

        Optional<MemoryMedia> match = memoryMediaRepository.findByIdAndMemoryIdAndUserId(media.getId(), memoryA.getId(), userA.getId());
        assertThat(match).isPresent();

        UUID randomMemoryId = UUID.randomUUID();
        Optional<MemoryMedia> wrongMemory = memoryMediaRepository.findByIdAndMemoryIdAndUserId(media.getId(), randomMemoryId, userA.getId());
        assertThat(wrongMemory).isEmpty();

        Optional<MemoryMedia> wrongUser = memoryMediaRepository.findByIdAndMemoryIdAndUserId(media.getId(), memoryA.getId(), userB.getId());
        assertThat(wrongUser).isEmpty();
    }

    @Test
    @DisplayName("6. findAllByMemoryId lists all media for cleanup before memory deletion")
    void testFindAllByMemoryId() {
        memoryMediaRepository.save(new MemoryMedia(memoryA, userA, "c1.jpg", "image/jpeg", 100L, "k1"));
        memoryMediaRepository.save(new MemoryMedia(memoryA, userA, "c2.jpg", "image/jpeg", 200L, "k2"));

        List<MemoryMedia> cleanupList = memoryMediaRepository.findAllByMemoryId(memoryA.getId());
        assertThat(cleanupList).hasSize(2);
    }

    @Test
    @DisplayName("7. Cascade deletion: deleting memory cascades to associated memory_media rows")
    void testCascadeDelete() {
        memoryMediaRepository.save(new MemoryMedia(memoryA, userA, "cascade.jpg", "image/jpeg", 100L, "k-cascade"));
        assertThat(memoryMediaRepository.countByMemoryId(memoryA.getId())).isEqualTo(1);

        memoryRepository.delete(memoryA);

        assertThat(memoryMediaRepository.countByMemoryId(memoryA.getId())).isEqualTo(0);
    }

    @Test
    @DisplayName("8. GlobalExceptionHandler maps InvalidMediaException to HTTP 400 Bad Request")
    void testInvalidMediaExceptionHandler() {
        InvalidMediaException ex = new InvalidMediaException("Corrupt image header: only JPEG, PNG, and WEBP supported.");
        ResponseEntity<ErrorResponse> response = globalExceptionHandler.handleInvalidMedia(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getStatus()).isEqualTo(400);
        assertThat(response.getBody().getError()).isEqualTo("Invalid Media");
        assertThat(response.getBody().getMessage()).contains("Corrupt image header");
    }

    @Test
    @DisplayName("9. GlobalExceptionHandler maps MaxUploadSizeExceededException to HTTP 413 Payload Too Large")
    void testMaxUploadSizeExceededExceptionHandler() {
        MaxUploadSizeExceededException ex = new MaxUploadSizeExceededException(52428800L);
        ResponseEntity<ErrorResponse> response = globalExceptionHandler.handleMaxUploadSizeExceeded(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.PAYLOAD_TOO_LARGE);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getStatus()).isEqualTo(413);
        assertThat(response.getBody().getError()).isEqualTo("Payload Too Large");
        assertThat(response.getBody().getMessage()).contains("10MB");
    }
}
