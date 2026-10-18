package com.memoraai;

import com.memoraai.dto.CreateMemoryRequest;
import com.memoraai.dto.EntityExtractionResponse;
import com.memoraai.dto.MemoryResponse;
import com.memoraai.dto.UpdateMemoryRequest;
import com.memoraai.entity.Role;
import com.memoraai.entity.User;
import com.memoraai.repository.MemoryRepository;
import com.memoraai.repository.UserRepository;
import com.memoraai.service.AiService;
import com.memoraai.service.GraphStoreService;
import com.memoraai.service.MemoryService;
import com.memoraai.service.VectorStoreService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@SpringBootTest
public class MemoryServiceGraphSyncTest {

    @Autowired
    private MemoryService memoryService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private MemoryRepository memoryRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @MockBean
    private AiService aiService;

    @MockBean
    private VectorStoreService vectorStoreService;

    @MockBean
    private GraphStoreService graphStoreService;

    private User testUser;

    @BeforeEach
    void setUp() {
        memoryRepository.deleteAll();
        userRepository.deleteAll();

        testUser = userRepository.save(new User("Sync User", "sync@example.com", passwordEncoder.encode("Pass123!"), Role.USER));
        reset(aiService, vectorStoreService, graphStoreService);
    }

    @Test
    @DisplayName("1. createMemory triggers graphStoreService.syncMemory with extracted entities")
    void testCreateMemoryTriggersGraphSync() {
        when(aiService.extractEntities(anyString(), anyString()))
                .thenReturn(new EntityExtractionResponse(List.of("Sarah", "Mom"), List.of("Paris")));

        CreateMemoryRequest request = new CreateMemoryRequest(
                "Trip to Paris",
                "Had dinner with Sarah in Paris. Mom visited us.",
                "Travel",
                "vacation, summer"
        );

        MemoryResponse response = memoryService.createMemory(request, testUser.getEmail());

        assertThat(response).isNotNull();
        assertThat(response.getId()).isNotNull();

        ArgumentCaptor<UUID> userCaptor = ArgumentCaptor.forClass(UUID.class);
        ArgumentCaptor<UUID> memoryCaptor = ArgumentCaptor.forClass(UUID.class);
        ArgumentCaptor<String> titleCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> categoryCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<List<String>> tagsCaptor = ArgumentCaptor.forClass(List.class);
        ArgumentCaptor<List<String>> peopleCaptor = ArgumentCaptor.forClass(List.class);
        ArgumentCaptor<List<String>> placesCaptor = ArgumentCaptor.forClass(List.class);

        verify(graphStoreService, times(1)).syncMemory(
                userCaptor.capture(),
                memoryCaptor.capture(),
                titleCaptor.capture(),
                categoryCaptor.capture(),
                tagsCaptor.capture(),
                peopleCaptor.capture(),
                placesCaptor.capture(),
                any(Instant.class),
                any(Instant.class)
        );

        assertThat(userCaptor.getValue()).isEqualTo(testUser.getId());
        assertThat(memoryCaptor.getValue()).isEqualTo(response.getId());
        assertThat(titleCaptor.getValue()).isEqualTo("Trip to Paris");
        assertThat(categoryCaptor.getValue()).isEqualTo("Travel");
        assertThat(tagsCaptor.getValue()).containsExactlyInAnyOrder("vacation", "summer");
        assertThat(peopleCaptor.getValue()).containsExactlyInAnyOrder("Sarah", "Mom");
        assertThat(placesCaptor.getValue()).containsExactlyInAnyOrder("Paris");
    }

    @Test
    @DisplayName("2. updateMemory triggers graphStoreService.syncMemory with updated entities")
    void testUpdateMemoryTriggersGraphSync() {
        when(aiService.extractEntities(anyString(), anyString()))
                .thenReturn(new EntityExtractionResponse(List.of("Sarah"), List.of("Paris")))
                .thenReturn(new EntityExtractionResponse(List.of("Uncle Bob"), List.of("Rome")));

        CreateMemoryRequest createReq = new CreateMemoryRequest(
                "Initial Memory",
                "Met Sarah in Paris.",
                "Travel",
                "europe"
        );
        MemoryResponse created = memoryService.createMemory(createReq, testUser.getEmail());

        UpdateMemoryRequest updateReq = new UpdateMemoryRequest(
                "Updated Memory in Rome",
                "Met Uncle Bob in Rome.",
                "Vacation",
                "italy, summer"
        );
        MemoryResponse updated = memoryService.updateMemory(created.getId(), updateReq, testUser.getEmail());

        assertThat(updated).isNotNull();
        assertThat(updated.getTitle()).isEqualTo("Updated Memory in Rome");

        verify(graphStoreService, times(2)).syncMemory(
                eq(testUser.getId()),
                eq(created.getId()),
                anyString(),
                any(),
                anyList(),
                anyList(),
                anyList(),
                any(),
                any()
        );
    }

    @Test
    @DisplayName("3. deleteMemory triggers graphStoreService.deleteMemory scoped to user")
    void testDeleteMemoryTriggersGraphDelete() {
        CreateMemoryRequest createReq = new CreateMemoryRequest(
                "Memory to Delete",
                "Content here.",
                "Notes",
                "temp"
        );
        MemoryResponse created = memoryService.createMemory(createReq, testUser.getEmail());

        memoryService.deleteMemory(created.getId(), testUser.getEmail());

        verify(graphStoreService, times(1)).deleteMemory(testUser.getId(), created.getId());
    }

    @Test
    @DisplayName("4. AI entity extraction failure does not fail memory creation")
    void testExtractionFailureAllowsMemoryCreation() {
        when(aiService.extractEntities(anyString(), anyString()))
                .thenThrow(new RuntimeException("AI service unavailable"));

        CreateMemoryRequest request = new CreateMemoryRequest(
                "Safe Memory",
                "Content even if AI service is down.",
                "Personal",
                "offline"
        );

        MemoryResponse response = memoryService.createMemory(request, testUser.getEmail());

        assertThat(response).isNotNull();
        assertThat(response.getTitle()).isEqualTo("Safe Memory");
    }

    @Test
    @DisplayName("5. Neo4j graph failure after commit does not fail memory creation")
    void testGraphFailureDoesNotFailMemoryCreation() {
        doThrow(new RuntimeException("Neo4j database down"))
                .when(graphStoreService)
                .syncMemory(any(), any(), any(), any(), any(), any(), any(), any(), any());

        CreateMemoryRequest request = new CreateMemoryRequest(
                "Resilient Memory",
                "Primary PostgreSQL persistence must succeed even if graph is down.",
                "Resilience",
                "database"
        );

        MemoryResponse response = memoryService.createMemory(request, testUser.getEmail());

        assertThat(response).isNotNull();
        assertThat(response.getId()).isNotNull();
        assertThat(memoryRepository.findById(response.getId())).isPresent();
    }
}
