package com.memoraai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.memoraai.dto.EntityExtractionResponse;
import com.memoraai.dto.GraphEdgeDto;
import com.memoraai.dto.GraphNodeDto;
import com.memoraai.dto.MemoryGraphResponse;
import com.memoraai.entity.Memory;
import com.memoraai.entity.Role;
import com.memoraai.entity.User;
import com.memoraai.exception.GraphServiceException;
import com.memoraai.repository.MemoryRepository;
import com.memoraai.repository.UserRepository;
import com.memoraai.security.JwtService;
import com.memoraai.service.AiService;
import com.memoraai.service.GraphStoreService;
import com.memoraai.service.VectorStoreService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
public class MemoryGraphIntegrationTest {

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

    @MockBean
    private GraphStoreService graphStoreService;

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
    @DisplayName("1. Unauthenticated request to GET /api/memories/graph is rejected")
    void testGetGraphUnauthenticatedRejected() throws Exception {
        mockMvc.perform(get("/api/memories/graph")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());

        verify(graphStoreService, never()).getUserGraph(any(), anyInt());
    }

    @Test
    @DisplayName("2. Authenticated request returns empty graph cleanly when user has no entries")
    void testGetGraphEmpty() throws Exception {
        when(graphStoreService.getUserGraph(eq(userA.getId()), anyInt()))
                .thenReturn(new MemoryGraphResponse(Collections.emptyList(), Collections.emptyList()));

        mockMvc.perform(get("/api/memories/graph")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nodes", hasSize(0)))
                .andExpect(jsonPath("$.edges", hasSize(0)));

        verify(graphStoreService, times(1)).getUserGraph(eq(userA.getId()), eq(100));
    }

    @Test
    @DisplayName("3. Authenticated request returns mapped nodes and edges for the user")
    void testGetGraphMappedNodesAndEdges() throws Exception {
        String memoryId = UUID.randomUUID().toString();
        List<GraphNodeDto> nodes = List.of(
                new GraphNodeDto(memoryId, "Family Reunion", "Memory", Map.of("category", "Family")),
                new GraphNodeDto("category:Family", "Family", "Category"),
                new GraphNodeDto("person:Uncle Bob", "Uncle Bob", "Person"),
                new GraphNodeDto("place:Chicago", "Chicago", "Place"),
                new GraphNodeDto("tag:summer", "summer", "Tag")
        );

        List<GraphEdgeDto> edges = List.of(
                new GraphEdgeDto(memoryId + "->IN_CATEGORY->category:Family", memoryId, "category:Family", "IN_CATEGORY"),
                new GraphEdgeDto(memoryId + "->MENTIONS->person:Uncle Bob", memoryId, "person:Uncle Bob", "MENTIONS"),
                new GraphEdgeDto(memoryId + "->LOCATED_AT->place:Chicago", memoryId, "place:Chicago", "LOCATED_AT"),
                new GraphEdgeDto(memoryId + "->HAS_TAG->tag:summer", memoryId, "tag:summer", "HAS_TAG")
        );

        when(graphStoreService.getUserGraph(eq(userA.getId()), eq(100)))
                .thenReturn(new MemoryGraphResponse(nodes, edges));

        mockMvc.perform(get("/api/memories/graph")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nodes", hasSize(5)))
                .andExpect(jsonPath("$.edges", hasSize(4)))
                .andExpect(jsonPath("$.nodes[0].id").value(memoryId))
                .andExpect(jsonPath("$.nodes[0].label").value("Family Reunion"))
                .andExpect(jsonPath("$.nodes[0].type").value("Memory"))
                .andExpect(jsonPath("$.nodes[0].properties.category").value("Family"))
                .andExpect(jsonPath("$.nodes[1].id").value("category:Family"))
                .andExpect(jsonPath("$.nodes[1].type").value("Category"))
                .andExpect(jsonPath("$.nodes[2].id").value("person:Uncle Bob"))
                .andExpect(jsonPath("$.nodes[2].type").value("Person"))
                .andExpect(jsonPath("$.nodes[3].id").value("place:Chicago"))
                .andExpect(jsonPath("$.nodes[3].type").value("Place"))
                .andExpect(jsonPath("$.nodes[4].id").value("tag:summer"))
                .andExpect(jsonPath("$.nodes[4].type").value("Tag"))
                .andExpect(jsonPath("$.edges[0].type").value("IN_CATEGORY"))
                .andExpect(jsonPath("$.edges[1].type").value("MENTIONS"))
                .andExpect(jsonPath("$.edges[2].type").value("LOCATED_AT"))
                .andExpect(jsonPath("$.edges[3].type").value("HAS_TAG"));
    }

    @Test
    @DisplayName("4. Tenant isolation: User A cannot retrieve User B's graph, and user ID query param is ignored")
    void testTenantIsolationUserCannotAccessOtherUsersGraph() throws Exception {
        when(graphStoreService.getUserGraph(eq(userA.getId()), anyInt()))
                .thenReturn(new MemoryGraphResponse(
                        List.of(new GraphNodeDto("mem-A", "Alpha Memory", "Memory")),
                        Collections.emptyList()
                ));

        when(graphStoreService.getUserGraph(eq(userB.getId()), anyInt()))
                .thenReturn(new MemoryGraphResponse(
                        List.of(new GraphNodeDto("mem-B", "Beta Secret Memory", "Memory")),
                        Collections.emptyList()
                ));

        // User A attempts to request User B's data via spoofed query parameter ?userId=<userB.getId()>
        mockMvc.perform(get("/api/memories/graph?userId=" + userB.getId())
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nodes", hasSize(1)))
                .andExpect(jsonPath("$.nodes[0].id").value("mem-A"))
                .andExpect(jsonPath("$.nodes[0].label").value("Alpha Memory"));

        // Verify GraphStoreService was called with User A's ID, NEVER User B's ID
        verify(graphStoreService, times(1)).getUserGraph(eq(userA.getId()), anyInt());
        verify(graphStoreService, never()).getUserGraph(eq(userB.getId()), anyInt());
    }

    @Test
    @DisplayName("5. Result limit parameter is propagated to graph store service")
    void testLimitParameterEnforced() throws Exception {
        when(graphStoreService.getUserGraph(eq(userA.getId()), eq(25)))
                .thenReturn(new MemoryGraphResponse(Collections.emptyList(), Collections.emptyList()));

        mockMvc.perform(get("/api/memories/graph?limit=25")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());

        verify(graphStoreService, times(1)).getUserGraph(eq(userA.getId()), eq(25));
    }

    @Test
    @DisplayName("6. Neo4j unavailable returns HTTP 503 controlled response without exposing internal error")
    void testNeo4jUnavailableReturnsControlled503() throws Exception {
        when(graphStoreService.getUserGraph(eq(userA.getId()), anyInt()))
                .thenThrow(new GraphServiceException("Connection refused to bolt://neo4j:7687"));

        mockMvc.perform(get("/api/memories/graph")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value(503))
                .andExpect(jsonPath("$.error").value("Service Unavailable"))
                .andExpect(jsonPath("$.message").value("Knowledge graph service is currently unavailable. Please try again later."))
                // Confirm no raw driver connection details or credentials leaked
                .andExpect(jsonPath("$.message", not(containsString("bolt://"))))
                .andExpect(jsonPath("$.message", not(containsString("neo4j"))));
    }

    @Test
    @DisplayName("7. Unauthenticated request to POST /api/memories/graph/reindex is rejected")
    void testReindexGraphUnauthenticatedRejected() throws Exception {
        mockMvc.perform(post("/api/memories/graph/reindex")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());

        verify(graphStoreService, never()).verifyConnectivity();
    }

    @Test
    @DisplayName("8. Authenticated reindex returns 0 processed when user has no memories")
    void testReindexGraphEmptyMemories() throws Exception {
        when(graphStoreService.verifyConnectivity()).thenReturn(true);

        mockMvc.perform(post("/api/memories/graph/reindex")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.processed").value(0))
                .andExpect(jsonPath("$.synchronized").value(0))
                .andExpect(jsonPath("$.failed").value(0))
                .andExpect(jsonPath("$.hasMore").value(false))
                .andExpect(jsonPath("$.totalElements").value(0))
                .andExpect(jsonPath("$.message").value("No memories found to synchronize."));
    }

    @Test
    @DisplayName("9. Authenticated reindex synchronizes user's memories into Neo4j graph")
    void testReindexGraphSuccess() throws Exception {
        when(graphStoreService.verifyConnectivity()).thenReturn(true);
        when(aiService.extractEntities(anyString(), anyString()))
                .thenReturn(new EntityExtractionResponse(List.of("Uncle Bob"), List.of("Paris")));

        memoryRepository.save(new Memory(userA, "Summer Vacation", "Trip to Paris with Uncle Bob", "Travel", "summer,europe"));
        memoryRepository.save(new Memory(userA, "Birthday Party", "Celebrated with Mom", "Family", "birthday"));

        mockMvc.perform(post("/api/memories/graph/reindex")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.processed").value(2))
                .andExpect(jsonPath("$.synchronized").value(2))
                .andExpect(jsonPath("$.failed").value(0))
                .andExpect(jsonPath("$.hasMore").value(false))
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.message", containsString("Successfully synchronized 2 memories")));

        verify(graphStoreService, times(2)).syncMemory(
                eq(userA.getId()),
                any(UUID.class),
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
    @DisplayName("10. Reindex handles pagination and batches correctly")
    void testReindexGraphPagination() throws Exception {
        when(graphStoreService.verifyConnectivity()).thenReturn(true);
        when(aiService.extractEntities(anyString(), anyString()))
                .thenReturn(new EntityExtractionResponse(Collections.emptyList(), Collections.emptyList()));

        memoryRepository.save(new Memory(userA, "Memory 1", "Content 1", "Cat1", "tag1"));
        memoryRepository.save(new Memory(userA, "Memory 2", "Content 2", "Cat2", "tag2"));

        // Page 0 with batchSize 1 -> hasMore = true
        mockMvc.perform(post("/api/memories/graph/reindex?page=0&batchSize=1")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.processed").value(1))
                .andExpect(jsonPath("$.synchronized").value(1))
                .andExpect(jsonPath("$.failed").value(0))
                .andExpect(jsonPath("$.hasMore").value(true))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.batchSize").value(1))
                .andExpect(jsonPath("$.totalElements").value(2));

        // Page 1 with batchSize 1 -> hasMore = false
        mockMvc.perform(post("/api/memories/graph/reindex?page=1&batchSize=1")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.processed").value(1))
                .andExpect(jsonPath("$.synchronized").value(1))
                .andExpect(jsonPath("$.failed").value(0))
                .andExpect(jsonPath("$.hasMore").value(false))
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.batchSize").value(1))
                .andExpect(jsonPath("$.totalElements").value(2));
    }

    @Test
    @DisplayName("11. Reindex is idempotent across multiple executions")
    void testReindexGraphIdempotency() throws Exception {
        when(graphStoreService.verifyConnectivity()).thenReturn(true);
        when(aiService.extractEntities(anyString(), anyString()))
                .thenReturn(new EntityExtractionResponse(Collections.emptyList(), Collections.emptyList()));

        memoryRepository.save(new Memory(userA, "Memory 1", "Content 1", "Cat1", "tag1"));

        // Execution 1
        mockMvc.perform(post("/api/memories/graph/reindex")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.processed").value(1))
                .andExpect(jsonPath("$.synchronized").value(1));

        // Execution 2
        mockMvc.perform(post("/api/memories/graph/reindex")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.processed").value(1))
                .andExpect(jsonPath("$.synchronized").value(1));
    }

    @Test
    @DisplayName("12. Reindex handles partial failures gracefully and reports failed count")
    void testReindexGraphPartialFailure() throws Exception {
        when(graphStoreService.verifyConnectivity()).thenReturn(true);
        when(aiService.extractEntities(anyString(), anyString()))
                .thenReturn(new EntityExtractionResponse(Collections.emptyList(), Collections.emptyList()));

        Memory mem1 = memoryRepository.save(new Memory(userA, "Memory 1", "Content 1", "Cat1", "tag1"));
        Memory mem2 = memoryRepository.save(new Memory(userA, "Memory 2", "Content 2", "Cat2", "tag2"));

        // Force failure on mem1 only
        doThrow(new RuntimeException("Neo4j node lock conflict"))
                .when(graphStoreService).syncMemory(
                        eq(userA.getId()),
                        eq(mem1.getId()),
                        anyString(),
                        any(),
                        anyList(),
                        anyList(),
                        anyList(),
                        any(),
                        any()
                );

        mockMvc.perform(post("/api/memories/graph/reindex")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.processed").value(2))
                .andExpect(jsonPath("$.synchronized").value(1))
                .andExpect(jsonPath("$.failed").value(1))
                .andExpect(jsonPath("$.message", containsString("Partially synchronized 2 memories: 1 succeeded, 1 failed")));
    }

    @Test
    @DisplayName("13. Reindex returns 503 when Neo4j is completely unavailable")
    void testReindexGraphNeo4jUnavailableReturns503() throws Exception {
        when(graphStoreService.verifyConnectivity()).thenReturn(false);

        mockMvc.perform(post("/api/memories/graph/reindex")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value(503))
                .andExpect(jsonPath("$.error").value("Service Unavailable"))
                .andExpect(jsonPath("$.message").value("Knowledge graph service is currently unavailable. Please try again later."));

        verify(graphStoreService, times(1)).verifyConnectivity();
        verify(graphStoreService, never()).syncMemory(any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("14. Tenant isolation: User A's reindex never touches User B's memories")
    void testReindexGraphTenantIsolation() throws Exception {
        when(graphStoreService.verifyConnectivity()).thenReturn(true);

        memoryRepository.save(new Memory(userA, "User A Memory", "Alpha content", "Alpha", "a"));
        Memory memB = memoryRepository.save(new Memory(userB, "User B Memory", "Beta secret content", "Beta", "b"));

        mockMvc.perform(post("/api/memories/graph/reindex")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.processed").value(1))
                .andExpect(jsonPath("$.synchronized").value(1))
                .andExpect(jsonPath("$.totalElements").value(1));

        // Confirm User B's memory was NEVER synchronized during User A's reindex
        verify(graphStoreService, never()).syncMemory(
                eq(userB.getId()),
                any(),
                anyString(),
                any(),
                anyList(),
                anyList(),
                anyList(),
                any(),
                any()
        );
        verify(graphStoreService, never()).syncMemory(
                any(),
                eq(memB.getId()),
                anyString(),
                any(),
                anyList(),
                anyList(),
                anyList(),
                any(),
                any()
        );
    }
}
