package com.memoraai;

import com.memoraai.dto.MemoryGraphResponse;
import com.memoraai.exception.GraphServiceException;
import com.memoraai.service.GraphStoreService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.neo4j.driver.*;
import org.neo4j.driver.Record;
import org.neo4j.driver.exceptions.ServiceUnavailableException;
import org.neo4j.driver.summary.ResultSummary;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class GraphStoreServiceTest {

    @Mock
    private Driver driver;

    @Mock
    private Session session;

    @Mock
    private TransactionContext transactionContext;

    @Mock
    private Result result;

    @Mock
    private ResultSummary resultSummary;

    @InjectMocks
    private GraphStoreService graphStoreService;

    @Test
    @DisplayName("1. verifyConnectivity returns true when driver succeeds")
    void testVerifyConnectivitySuccess() {
        doNothing().when(driver).verifyConnectivity();

        boolean result = graphStoreService.verifyConnectivity();

        assertThat(result).isTrue();
        verify(driver, times(1)).verifyConnectivity();
    }

    @Test
    @DisplayName("2. verifyConnectivity returns false and catches exception when driver fails")
    void testVerifyConnectivityFailure() {
        doThrow(new ServiceUnavailableException("Unable to connect to Neo4j"))
                .when(driver).verifyConnectivity();

        boolean result = graphStoreService.verifyConnectivity();

        assertThat(result).isFalse();
        verify(driver, times(1)).verifyConnectivity();
    }

    @Test
    @DisplayName("3. openSession returns a session from driver")
    void testOpenSessionDefault() {
        when(driver.session()).thenReturn(session);

        Session obtainedSession = graphStoreService.openSession();

        assertThat(obtainedSession).isSameAs(session);
        verify(driver, times(1)).session();
    }

    @Test
    @DisplayName("4. openSession with SessionConfig returns configured session")
    void testOpenSessionWithConfig() {
        SessionConfig config = SessionConfig.defaultConfig();
        when(driver.session(config)).thenReturn(session);

        Session obtainedSession = graphStoreService.openSession(config);

        assertThat(obtainedSession).isSameAs(session);
        verify(driver, times(1)).session(config);
    }

    @Test
    @DisplayName("5. getDriver returns underlying Driver instance")
    void testGetDriver() {
        assertThat(graphStoreService.getDriver()).isSameAs(driver);
    }

    @Test
    @DisplayName("6. syncMemory executes upsert, relationships, and closes session")
    @SuppressWarnings("unchecked")
    void testSyncMemorySuccess() {
        UUID userId = UUID.randomUUID();
        UUID memoryId = UUID.randomUUID();
        when(driver.session()).thenReturn(session);
        when(session.executeWrite(any(TransactionCallback.class))).thenAnswer(invocation -> {
            TransactionCallback<?> cb = invocation.getArgument(0);
            return cb.execute(transactionContext);
        });
        when(transactionContext.run(anyString(), anyMap())).thenReturn(result);
        when(result.consume()).thenReturn(resultSummary);

        graphStoreService.syncMemory(
                userId,
                memoryId,
                "Trip to Paris",
                "Travel",
                List.of("vacation", "summer"),
                List.of("Sarah", "Mom"),
                List.of("Paris", "Lake Tahoe"),
                Instant.now(),
                Instant.now()
        );

        verify(driver, times(1)).session();
        verify(session, times(1)).executeWrite(any(TransactionCallback.class));
        verify(session, times(1)).close();

        // 1 upsert memory + 1 category + 1 tags + 1 people + 1 places + 3 orphan prunings = 8 queries
        verify(transactionContext, atLeast(5)).run(anyString(), anyMap());
    }

    @Test
    @DisplayName("7. syncMemory with null IDs is a no-op")
    void testSyncMemoryNullIds() {
        graphStoreService.syncMemory(null, UUID.randomUUID(), "Title", null, null, null, null, null, null);
        graphStoreService.syncMemory(UUID.randomUUID(), null, "Title", null, null, null, null, null, null);

        verify(driver, never()).session();
    }

    @Test
    @DisplayName("8. deleteMemory executes DETACH DELETE scoped to user and prunes orphans")
    @SuppressWarnings("unchecked")
    void testDeleteMemorySuccess() {
        UUID userId = UUID.randomUUID();
        UUID memoryId = UUID.randomUUID();
        when(driver.session()).thenReturn(session);
        when(session.executeWrite(any(TransactionCallback.class))).thenAnswer(invocation -> {
            TransactionCallback<?> cb = invocation.getArgument(0);
            return cb.execute(transactionContext);
        });
        when(transactionContext.run(anyString(), anyMap())).thenReturn(result);
        when(result.consume()).thenReturn(resultSummary);

        graphStoreService.deleteMemory(userId, memoryId);

        verify(driver, times(1)).session();
        verify(session, times(1)).executeWrite(any(TransactionCallback.class));
        verify(session, times(1)).close();

        ArgumentCaptor<String> cypherCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Map<String, Object>> mapCaptor = ArgumentCaptor.forClass(Map.class);
        verify(transactionContext, atLeast(1)).run(cypherCaptor.capture(), mapCaptor.capture());

        assertThat(cypherCaptor.getAllValues().get(0)).contains("DETACH DELETE m");
        assertThat(mapCaptor.getAllValues().get(0)).containsEntry("userId", userId.toString());
        assertThat(mapCaptor.getAllValues().get(0)).containsEntry("memoryId", memoryId.toString());
    }

    @Test
    @DisplayName("9. deleteMemory with null IDs is a no-op")
    void testDeleteMemoryNullIds() {
        graphStoreService.deleteMemory(null, UUID.randomUUID());
        graphStoreService.deleteMemory(UUID.randomUUID(), null);

        verify(driver, never()).session();
    }

    @Test
    @DisplayName("10. getUserGraph with null userId returns empty graph response")
    void testGetUserGraphNullUserId() {
        MemoryGraphResponse response = graphStoreService.getUserGraph(null, 100);

        assertThat(response).isNotNull();
        assertThat(response.getNodes()).isEmpty();
        assertThat(response.getEdges()).isEmpty();
        verify(driver, never()).session();
    }

    @Test
    @DisplayName("11. getUserGraph returns empty graph when Neo4j has no records")
    @SuppressWarnings("unchecked")
    void testGetUserGraphEmptyResult() {
        UUID userId = UUID.randomUUID();
        when(driver.session()).thenReturn(session);
        when(session.executeRead(any(TransactionCallback.class))).thenAnswer(invocation -> {
            TransactionCallback<?> cb = invocation.getArgument(0);
            return cb.execute(transactionContext);
        });
        when(transactionContext.run(anyString(), anyMap())).thenReturn(result);
        when(result.hasNext()).thenReturn(false);

        MemoryGraphResponse response = graphStoreService.getUserGraph(userId, 50);

        assertThat(response).isNotNull();
        assertThat(response.getNodes()).isEmpty();
        assertThat(response.getEdges()).isEmpty();
        verify(session, times(1)).close();
    }

    @Test
    @DisplayName("12. getUserGraph correctly maps memories, categories, tags, people, and places")
    @SuppressWarnings("unchecked")
    void testGetUserGraphSuccessMapping() {
        UUID userId = UUID.randomUUID();
        String mem1Id = UUID.randomUUID().toString();

        Record r1 = mock(Record.class);
        when(r1.get("memoryId")).thenReturn(Values.value(mem1Id));
        when(r1.get("memoryTitle")).thenReturn(Values.value("Trip to Paris"));
        when(r1.get("memoryCategory")).thenReturn(Values.value("Travel"));
        when(r1.get("memoryCreatedAt")).thenReturn(Values.value("2026-06-01T10:00:00Z"));
        when(r1.get("relType")).thenReturn(Values.value("IN_CATEGORY"));
        when(r1.get("targetName")).thenReturn(Values.value("Travel"));
        when(r1.get("targetLabels")).thenReturn(Values.value(List.of("Category")));

        Record r2 = mock(Record.class);
        when(r2.get("memoryId")).thenReturn(Values.value(mem1Id));
        when(r2.get("relType")).thenReturn(Values.value("MENTIONS"));
        when(r2.get("targetName")).thenReturn(Values.value("Uncle Bob"));
        when(r2.get("targetLabels")).thenReturn(Values.value(List.of("Person")));

        Record r3 = mock(Record.class);
        when(r3.get("memoryId")).thenReturn(Values.value(mem1Id));
        when(r3.get("relType")).thenReturn(Values.value("LOCATED_AT"));
        when(r3.get("targetName")).thenReturn(Values.value("Paris"));
        when(r3.get("targetLabels")).thenReturn(Values.value(List.of("Place")));

        Record r4 = mock(Record.class);
        when(r4.get("memoryId")).thenReturn(Values.value(mem1Id));
        when(r4.get("relType")).thenReturn(Values.value("HAS_TAG"));
        when(r4.get("targetName")).thenReturn(Values.value("summer"));
        when(r4.get("targetLabels")).thenReturn(Values.value(List.of("Tag")));

        when(driver.session()).thenReturn(session);
        when(session.executeRead(any(TransactionCallback.class))).thenAnswer(invocation -> {
            TransactionCallback<?> cb = invocation.getArgument(0);
            return cb.execute(transactionContext);
        });
        when(transactionContext.run(anyString(), anyMap())).thenReturn(result);
        when(result.hasNext()).thenReturn(true, true, true, true, false);
        when(result.next()).thenReturn(r1, r2, r3, r4);

        MemoryGraphResponse response = graphStoreService.getUserGraph(userId, 100);

        assertThat(response).isNotNull();
        assertThat(response.getNodes()).hasSize(5); // Memory, Category, Person, Place, Tag
        assertThat(response.getEdges()).hasSize(4);

        // Verify Memory Node
        var memoryNode = response.getNodes().stream().filter(n -> "Memory".equals(n.getType())).findFirst().orElseThrow();
        assertThat(memoryNode.getId()).isEqualTo(mem1Id);
        assertThat(memoryNode.getLabel()).isEqualTo("Trip to Paris");
        assertThat(memoryNode.getProperties()).containsEntry("category", "Travel");

        // Verify Person Node & Edge
        var personNode = response.getNodes().stream().filter(n -> "Person".equals(n.getType())).findFirst().orElseThrow();
        assertThat(personNode.getId()).isEqualTo("person:Uncle Bob");
        assertThat(personNode.getLabel()).isEqualTo("Uncle Bob");

        var mentionsEdge = response.getEdges().stream().filter(e -> "MENTIONS".equals(e.getType())).findFirst().orElseThrow();
        assertThat(mentionsEdge.getSource()).isEqualTo(mem1Id);
        assertThat(mentionsEdge.getTarget()).isEqualTo("person:Uncle Bob");

        // Verify Place Node & Edge
        var placeNode = response.getNodes().stream().filter(n -> "Place".equals(n.getType())).findFirst().orElseThrow();
        assertThat(placeNode.getId()).isEqualTo("place:Paris");
        assertThat(placeNode.getLabel()).isEqualTo("Paris");

        var locatedEdge = response.getEdges().stream().filter(e -> "LOCATED_AT".equals(e.getType())).findFirst().orElseThrow();
        assertThat(locatedEdge.getSource()).isEqualTo(mem1Id);
        assertThat(locatedEdge.getTarget()).isEqualTo("place:Paris");

        // Verify Category Node & Edge
        var categoryNode = response.getNodes().stream().filter(n -> "Category".equals(n.getType())).findFirst().orElseThrow();
        assertThat(categoryNode.getId()).isEqualTo("category:Travel");
        assertThat(categoryNode.getLabel()).isEqualTo("Travel");

        // Verify Tag Node & Edge
        var tagNode = response.getNodes().stream().filter(n -> "Tag".equals(n.getType())).findFirst().orElseThrow();
        assertThat(tagNode.getId()).isEqualTo("tag:summer");
        assertThat(tagNode.getLabel()).isEqualTo("summer");
    }

    @Test
    @DisplayName("13. getUserGraph clamps limit between 1 and 500")
    @SuppressWarnings("unchecked")
    void testGetUserGraphEnforcesLimitClamping() {
        UUID userId = UUID.randomUUID();
        when(driver.session()).thenReturn(session);
        when(session.executeRead(any(TransactionCallback.class))).thenAnswer(invocation -> {
            TransactionCallback<?> cb = invocation.getArgument(0);
            return cb.execute(transactionContext);
        });
        when(transactionContext.run(anyString(), anyMap())).thenReturn(result);
        when(result.hasNext()).thenReturn(false);

        // Test over max limit (1000 clamped to 500)
        graphStoreService.getUserGraph(userId, 1000);

        ArgumentCaptor<Map<String, Object>> mapCaptor = ArgumentCaptor.forClass(Map.class);
        verify(transactionContext, times(1)).run(anyString(), mapCaptor.capture());
        assertThat(mapCaptor.getValue()).containsEntry("limit", 500);

        // Test under min limit (0 clamped to 1)
        graphStoreService.getUserGraph(userId, 0);
        verify(transactionContext, times(2)).run(anyString(), mapCaptor.capture());
        assertThat(mapCaptor.getValue()).containsEntry("limit", 1);
    }

    @Test
    @DisplayName("14. getUserGraph wraps Neo4j exceptions into GraphServiceException")
    @SuppressWarnings("unchecked")
    void testGetUserGraphNeo4jFailureThrowsGraphServiceException() {
        UUID userId = UUID.randomUUID();
        when(driver.session()).thenReturn(session);
        when(session.executeRead(any(TransactionCallback.class))).thenThrow(new ServiceUnavailableException("Neo4j down"));

        assertThatThrownBy(() -> graphStoreService.getUserGraph(userId, 100))
                .isInstanceOf(GraphServiceException.class)
                .hasMessageContaining("Failed to query knowledge graph");
    }
}
