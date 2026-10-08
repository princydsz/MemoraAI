package com.memoraai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.memoraai.dto.AiProcessMemoryResponse;
import com.memoraai.dto.CreateMemoryRequest;
import com.memoraai.dto.UpdateMemoryRequest;
import com.memoraai.entity.Memory;
import com.memoraai.entity.Role;
import com.memoraai.entity.User;
import com.memoraai.repository.MemoryRepository;
import com.memoraai.repository.UserRepository;
import com.memoraai.security.JwtService;
import com.memoraai.service.AiService;
import com.memoraai.service.VectorStoreService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
public class MemoryIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private MemoryRepository memoryRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtService jwtService;

    @MockBean
    private AiService aiService;

    @MockBean
    private VectorStoreService vectorStoreService;

    private User userA;
    private User userB;
    private String tokenA;
    private String tokenB;

    @BeforeEach
    void setUp() {
        memoryRepository.deleteAll();
        userRepository.deleteAll();

        userA = userRepository.save(new User("User Alpha", "alpha@example.com", passwordEncoder.encode("Password123!"), Role.USER));
        userB = userRepository.save(new User("User Beta", "beta@example.com", passwordEncoder.encode("Password123!"), Role.USER));

        tokenA = jwtService.generateToken(userA);
        tokenB = jwtService.generateToken(userB);
    }

    @Test
    @DisplayName("1. Unauthenticated user cannot access /api/memories")
    void testUnauthenticatedAccessDenied() throws Exception {
        mockMvc.perform(get("/api/memories"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("2. Authenticated user can create a memory")
    void testCreateMemorySuccess() throws Exception {
        CreateMemoryRequest request = new CreateMemoryRequest("Summer Vacation", "Went to the beach with family.", "Travel", "vacation,beach");

        mockMvc.perform(post("/api/memories")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.title", is("Summer Vacation")))
                .andExpect(jsonPath("$.content", is("Went to the beach with family.")))
                .andExpect(jsonPath("$.category", is("Travel")))
                .andExpect(jsonPath("$.tags", is("vacation,beach")))
                .andExpect(jsonPath("$.createdAt").isNotEmpty());

        assertThat(memoryRepository.findAllByUserIdOrderByCreatedAtDesc(userA.getId())).hasSize(1);
    }

    @Test
    @DisplayName("3. Authenticated user can list their memories")
    void testListUserMemories() throws Exception {
        memoryRepository.save(new Memory(userA, "Memory 1", "Content 1", "Work", "tag1"));
        memoryRepository.save(new Memory(userA, "Memory 2", "Content 2", "Personal", "tag2"));

        mockMvc.perform(get("/api/memories")
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].title", notNullValue()))
                .andExpect(jsonPath("$[1].title", notNullValue()));
    }

    @Test
    @DisplayName("4. Authenticated user can retrieve their own memory")
    void testGetOwnMemorySuccess() throws Exception {
        Memory memory = memoryRepository.save(new Memory(userA, "Graduation Day", "Graduated from university.", "Milestone", "education"));

        mockMvc.perform(get("/api/memories/" + memory.getId())
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(memory.getId().toString())))
                .andExpect(jsonPath("$.title", is("Graduation Day")))
                .andExpect(jsonPath("$.content", is("Graduated from university.")));
    }

    @Test
    @DisplayName("5. Authenticated user can update their own memory")
    void testUpdateOwnMemorySuccess() throws Exception {
        Memory memory = memoryRepository.save(new Memory(userA, "Old Title", "Old Content", "Draft", "test"));
        UpdateMemoryRequest updateRequest = new UpdateMemoryRequest("Updated Title", "Updated Content", "Final", "updated");

        mockMvc.perform(put("/api/memories/" + memory.getId())
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title", is("Updated Title")))
                .andExpect(jsonPath("$.content", is("Updated Content")))
                .andExpect(jsonPath("$.category", is("Final")))
                .andExpect(jsonPath("$.tags", is("updated")));

        Optional<Memory> updated = memoryRepository.findByIdAndUserId(memory.getId(), userA.getId());
        assertThat(updated).isPresent();
        assertThat(updated.get().getTitle()).isEqualTo("Updated Title");
    }

    @Test
    @DisplayName("6. Authenticated user can delete their own memory")
    void testDeleteOwnMemorySuccess() throws Exception {
        Memory memory = memoryRepository.save(new Memory(userA, "To Be Deleted", "Content to delete", "Temp", "delete"));

        mockMvc.perform(delete("/api/memories/" + memory.getId())
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isNoContent());

        assertThat(memoryRepository.findByIdAndUserId(memory.getId(), userA.getId())).isEmpty();
    }

    @Test
    @DisplayName("7. User A cannot retrieve User B's memory (returns 404 Not Found)")
    void testUserCannotGetOtherUsersMemory() throws Exception {
        Memory memoryB = memoryRepository.save(new Memory(userB, "Secret Memory B", "User B Private Data", "Secret", "confidential"));

        mockMvc.perform(get("/api/memories/" + memoryB.getId())
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status", is(404)))
                .andExpect(jsonPath("$.error", is("Not Found")));
    }

    @Test
    @DisplayName("8. User A cannot update User B's memory (returns 404 Not Found)")
    void testUserCannotUpdateOtherUsersMemory() throws Exception {
        Memory memoryB = memoryRepository.save(new Memory(userB, "Original B", "Original Content B", "Secret", "confidential"));
        UpdateMemoryRequest attackRequest = new UpdateMemoryRequest("Hacked Title", "Hacked Content", "Hacked", "hacked");

        mockMvc.perform(put("/api/memories/" + memoryB.getId())
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(attackRequest)))
                .andExpect(status().isNotFound());

        // Verify Memory B was NOT modified
        Memory unchanged = memoryRepository.findByIdAndUserId(memoryB.getId(), userB.getId()).orElseThrow();
        assertThat(unchanged.getTitle()).isEqualTo("Original B");
    }

    @Test
    @DisplayName("9. User A cannot delete User B's memory (returns 404 Not Found)")
    void testUserCannotDeleteOtherUsersMemory() throws Exception {
        Memory memoryB = memoryRepository.save(new Memory(userB, "Important B", "Content B", "Work", "b"));

        mockMvc.perform(delete("/api/memories/" + memoryB.getId())
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isNotFound());

        // Verify Memory B still exists in DB for User B
        assertThat(memoryRepository.findByIdAndUserId(memoryB.getId(), userB.getId())).isPresent();
    }

    @Test
    @DisplayName("10. Validation rejects blank title with 400 Bad Request")
    void testValidationRejectsBlankTitle() throws Exception {
        CreateMemoryRequest request = new CreateMemoryRequest("   ", "Valid Content", "Category", "tag");

        mockMvc.perform(post("/api/memories")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.title").exists());
    }

    @Test
    @DisplayName("11. Validation rejects blank content with 400 Bad Request")
    void testValidationRejectsBlankContent() throws Exception {
        CreateMemoryRequest request = new CreateMemoryRequest("Valid Title", "", "Category", "tag");

        mockMvc.perform(post("/api/memories")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.content").exists());
    }

    @Test
    @DisplayName("12. AI processing does NOT modify stored memory fields or updatedAt timestamp")
    void testAiProcessingDoesNotModifyStoredMemory() throws Exception {
        Memory memory = memoryRepository.save(new Memory(userA, "Original Title", "Original Content", "Original Category", "original,tags"));
        Memory beforeProcess = memoryRepository.findByIdAndUserId(memory.getId(), userA.getId()).orElseThrow();
        Instant originalCreatedAt = beforeProcess.getCreatedAt();
        Instant originalUpdatedAt = beforeProcess.getUpdatedAt();

        when(aiService.processMemory(any(Memory.class)))
                .thenReturn(new AiProcessMemoryResponse(
                        "AI Summary for Original Title",
                        "Suggested Category",
                        List.of("tag1", "tag2")
                ));

        mockMvc.perform(post("/api/memories/" + memory.getId() + "/process")
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary", is("AI Summary for Original Title")))
                .andExpect(jsonPath("$.suggestedCategory", is("Suggested Category")))
                .andExpect(jsonPath("$.suggestedTags", hasItems("tag1", "tag2")));

        // Verify stored memory in database is strictly unchanged
        Memory storedMemory = memoryRepository.findByIdAndUserId(memory.getId(), userA.getId()).orElseThrow();
        assertThat(storedMemory.getTitle()).isEqualTo("Original Title");
        assertThat(storedMemory.getContent()).isEqualTo("Original Content");
        assertThat(storedMemory.getCategory()).isEqualTo("Original Category");
        assertThat(storedMemory.getTags()).isEqualTo("original,tags");
        assertThat(storedMemory.getCreatedAt()).isEqualTo(originalCreatedAt);
        assertThat(storedMemory.getUpdatedAt()).isEqualTo(originalUpdatedAt);
    }

    @Test
    @DisplayName("13. User A cannot trigger AI processing on User B's memory (returns 404 Not Found)")
    void testUserCannotProcessOtherUsersMemory() throws Exception {
        Memory memoryB = memoryRepository.save(new Memory(userB, "User B Memory", "Content B", "Category B", "tagB"));

        mockMvc.perform(post("/api/memories/" + memoryB.getId() + "/process")
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status", is(404)))
                .andExpect(jsonPath("$.error", is("Not Found")));
    }

    @Test
    @DisplayName("14. User can accept AI suggested category and tags during memory creation/review")
    void testUserCanAcceptAndPersistAiSuggestedCategoryAndTags() throws Exception {
        Memory memory = memoryRepository.save(new Memory(userA, "First Day at College", "Met friends and attended lectures", null, null));

        when(aiService.processMemory(any(Memory.class)))
                .thenReturn(new AiProcessMemoryResponse("College summary", "College", List.of("college", "friends")));

        // 1. Process memory to get AI suggestions
        mockMvc.perform(post("/api/memories/" + memory.getId() + "/process")
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.suggestedCategory", is("College")))
                .andExpect(jsonPath("$.suggestedTags", hasItems("college", "friends")));

        // 2. User accepts AI suggestions and saves
        UpdateMemoryRequest acceptRequest = new UpdateMemoryRequest("First Day at College", "Met friends and attended lectures", "College", "college,friends");
        mockMvc.perform(put("/api/memories/" + memory.getId())
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(acceptRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.category", is("College")))
                .andExpect(jsonPath("$.tags", is("college,friends")));

        // 3. Verify database persistence
        Memory updated = memoryRepository.findByIdAndUserId(memory.getId(), userA.getId()).orElseThrow();
        assertThat(updated.getCategory()).isEqualTo("College");
        assertThat(updated.getTags()).isEqualTo("college,friends");
    }

    @Test
    @DisplayName("15. User can manually override AI suggested category and tags")
    void testUserCanOverrideAiSuggestedCategoryAndTags() throws Exception {
        Memory memory = memoryRepository.save(new Memory(userA, "First Day at College", "Met friends and attended lectures", null, null));

        when(aiService.processMemory(any(Memory.class)))
                .thenReturn(new AiProcessMemoryResponse("College summary", "College", List.of("college", "friends")));

        // 1. Process memory
        mockMvc.perform(post("/api/memories/" + memory.getId() + "/process")
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk());

        // 2. User overrides with custom values
        UpdateMemoryRequest overrideRequest = new UpdateMemoryRequest("First Day at College", "Met friends and attended lectures", "Custom Campus Life", "mycollege,academics");
        mockMvc.perform(put("/api/memories/" + memory.getId())
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(overrideRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.category", is("Custom Campus Life")))
                .andExpect(jsonPath("$.tags", is("mycollege,academics")));

        // 3. Verify database persistence reflects custom user values
        Memory updated = memoryRepository.findByIdAndUserId(memory.getId(), userA.getId()).orElseThrow();
        assertThat(updated.getCategory()).isEqualTo("Custom Campus Life");
        assertThat(updated.getTags()).isEqualTo("mycollege,academics");
    }

    @Test
    @DisplayName("16. Unauthenticated user cannot access semantic search")
    void testUnauthenticatedSemanticSearchDenied() throws Exception {
        mockMvc.perform(get("/api/memories/search?q=trips"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("17. Authenticated user can perform semantic search")
    void testAuthenticatedSemanticSearchSuccess() throws Exception {
        Memory memA1 = memoryRepository.save(new Memory(userA, "Goa Trip", "Spent 3 days in Goa with friends at Baga Beach watching sunsets.", "Travel", "goa,friends"));

        when(vectorStoreService.search(eq(userA.getId()), eq("trips with friends"), anyInt()))
                .thenReturn(List.of(new VectorStoreService.VectorSearchResult(memA1.getId(), 0.92)));

        mockMvc.perform(get("/api/memories/search?q=trips with friends")
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id", is(memA1.getId().toString())))
                .andExpect(jsonPath("$[0].title", is("Goa Trip")))
                .andExpect(jsonPath("$[0].similarityScore", is(0.92)));
    }

    @Test
    @DisplayName("18. Cross-user isolation: User A search NEVER returns User B memories even if vector store returns User B id")
    void testCrossUserSemanticSearchIsolation() throws Exception {
        Memory memB = memoryRepository.save(new Memory(userB, "User B Secret Trip", "User B went to Hawaii", "Travel", "hawaii"));

        // Simulate vectorStore mistakenly returning User B's memory ID for User A's query
        when(vectorStoreService.search(eq(userA.getId()), anyString(), anyInt()))
                .thenReturn(List.of(new VectorStoreService.VectorSearchResult(memB.getId(), 0.99)));

        mockMvc.perform(get("/api/memories/search?q=Hawaii trip")
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0))); // Strictly filtered out at database level!
    }

    @Test
    @DisplayName("19. Deleted memories are not returned in semantic search")
    void testDeletedMemoriesNotReturnedInSearch() throws Exception {
        Memory memA = memoryRepository.save(new Memory(userA, "Temporary Note", "Will be deleted soon", "Temp", "temp"));
        UUID memId = memA.getId();

        // 1. Delete memory
        mockMvc.perform(delete("/api/memories/" + memId)
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isNoContent());

        // 2. Vector search returning deleted ID
        when(vectorStoreService.search(eq(userA.getId()), anyString(), anyInt()))
                .thenReturn(List.of(new VectorStoreService.VectorSearchResult(memId, 0.85)));

        // 3. Search query should return empty list
        mockMvc.perform(get("/api/memories/search?q=Temporary Note")
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    @DisplayName("20. Authenticated user can trigger reindex of their memories")
    void testReindexUserMemories() throws Exception {
        memoryRepository.save(new Memory(userA, "Mem 1", "Content 1", "Work", "tag1"));
        memoryRepository.save(new Memory(userA, "Mem 2", "Content 2", "Personal", "tag2"));

        mockMvc.perform(post("/api/memories/reindex")
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reindexedCount", is(2)))
                .andExpect(jsonPath("$.message", containsString("Successfully re-indexed 2 memories")));
    }

    @Test
    @DisplayName("21. Relevance Threshold Filtering: Only memories with neural score >= 0.35 are returned ('vacation with friends' query)")
    void testRelevanceScoreThresholdFiltering() throws Exception {
        Memory memA1 = memoryRepository.save(new Memory(userA, "Goa Trip", "Went to Goa with my friends during the summer vacation. We visited beaches and explored local restaurants.", "Travel", "goa,friends,vacation"));
        Memory memA2 = memoryRepository.save(new Memory(userA, "Office Presentation", "Prepared and delivered a presentation for the college/company seminar.", "Work", "presentation,seminar"));
        Memory memA3 = memoryRepository.save(new Memory(userA, "Birthday Dinner", "Had a birthday dinner with my family at a restaurant.", "Family", "birthday,dinner"));

        // Simulate VectorStoreService score threshold filtering (only memA1 has neural score >= 0.35)
        when(vectorStoreService.search(eq(userA.getId()), eq("vacation with friends"), anyInt()))
                .thenReturn(List.of(new VectorStoreService.VectorSearchResult(memA1.getId(), 0.4965)));

        mockMvc.perform(get("/api/memories/search?q=vacation with friends")
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id", is(memA1.getId().toString())))
                .andExpect(jsonPath("$[0].title", is("Goa Trip")))
                .andExpect(jsonPath("$[0].similarityScore", is(0.4965)));
    }

    @Test
    @DisplayName("22. Unrelated concept ('quantum physics') returns empty result set when all neural scores are below threshold 0.35")
    void testQuantumPhysicsQueryReturnsEmptyResult() throws Exception {
        memoryRepository.save(new Memory(userA, "Goa Trip", "Went to Goa with my friends during the summer vacation.", "Travel", "goa"));
        memoryRepository.save(new Memory(userA, "Office Presentation", "Prepared and delivered a presentation for seminar.", "Work", "work"));
        memoryRepository.save(new Memory(userA, "Birthday Dinner", "Had a birthday dinner with family.", "Family", "birthday"));

        // Vector store threshold filters out all 3 memories because neural scores (-0.0147, -0.0432, 0.0357) are all < 0.35
        when(vectorStoreService.search(eq(userA.getId()), eq("quantum physics"), anyInt()))
                .thenReturn(List.of());

        mockMvc.perform(get("/api/memories/search?q=quantum physics")
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }
}
