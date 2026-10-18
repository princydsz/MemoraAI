package com.memoraai.service;

import com.memoraai.dto.GraphEdgeDto;
import com.memoraai.dto.GraphNodeDto;
import com.memoraai.dto.MemoryGraphResponse;
import com.memoraai.exception.GraphServiceException;
import org.neo4j.driver.Driver;
import org.neo4j.driver.Session;
import org.neo4j.driver.SessionConfig;
import org.neo4j.driver.TransactionContext;
import org.neo4j.driver.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class GraphStoreService {

    private static final Logger log = LoggerFactory.getLogger(GraphStoreService.class);

    private final Driver driver;

    public GraphStoreService(Driver driver) {
        this.driver = driver;
    }

    /**
     * Verifies that the Neo4j driver can connect to the configured Neo4j instance.
     *
     * @return true if connection is verified, false if unreachable or verification fails.
     */
    public boolean verifyConnectivity() {
        try {
            driver.verifyConnectivity();
            log.info("Neo4j connectivity verified successfully.");
            return true;
        } catch (Exception e) {
            log.warn("Neo4j connectivity verification failed: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Opens a new Neo4j session using default session configuration.
     * Callers must close the session (e.g. using try-with-resources).
     *
     * @return a new Neo4j Session.
     */
    public Session openSession() {
        return driver.session();
    }

    /**
     * Opens a new Neo4j session using specific SessionConfig.
     * Callers must close the session (e.g. using try-with-resources).
     *
     * @param config the session configuration.
     * @return a new Neo4j Session.
     */
    public Session openSession(SessionConfig config) {
        return driver.session(config);
    }

    /**
     * Synchronizes a memory's properties, category, tags, and extracted people/places into Neo4j.
     * All writes are parameterized, idempotent (using MERGE), and strictly scoped to the authenticated userId.
     */
    public void syncMemory(UUID userId, UUID memoryId, String title, String category,
                           List<String> tags, List<String> people, List<String> places,
                           Instant createdAt, Instant updatedAt) {
        if (userId == null || memoryId == null) {
            return;
        }

        try (Session session = openSession()) {
            session.executeWrite(tx -> {
                // 1. Merge User and Memory nodes, set memory properties, and merge OWNS relationship.
                // Clear any stale entity relationships on this memory.
                String upsertMemoryCypher = """
                    MERGE (u:User {id: $userId})
                    MERGE (m:Memory {id: $memoryId})
                    SET m.title = $title,
                        m.category = $category,
                        m.createdAt = $createdAt,
                        m.updatedAt = $updatedAt
                    MERGE (u)-[:OWNS]->(m)
                    WITH m
                    OPTIONAL MATCH (m)-[r:IN_CATEGORY|HAS_TAG|MENTIONS|LOCATED_AT]->()
                    DELETE r
                """;

                Map<String, Object> params = new HashMap<>();
                params.put("userId", userId.toString());
                params.put("memoryId", memoryId.toString());
                params.put("title", title != null ? title.trim() : "");
                params.put("category", category != null ? category.trim() : null);
                params.put("createdAt", createdAt != null ? createdAt.toString() : Instant.now().toString());
                params.put("updatedAt", updatedAt != null ? updatedAt.toString() : Instant.now().toString());

                tx.run(upsertMemoryCypher, params).consume();

                // 2. Link category if present
                if (category != null && !category.trim().isEmpty()) {
                    String linkCategoryCypher = """
                        MATCH (m:Memory {id: $memoryId})
                        MERGE (c:Category {name: $categoryName})
                        MERGE (m)-[:IN_CATEGORY]->(c)
                    """;
                    tx.run(linkCategoryCypher, Map.of(
                            "memoryId", memoryId.toString(),
                            "categoryName", category.trim()
                    )).consume();
                }

                // 3. Link tags if present
                if (tags != null && !tags.isEmpty()) {
                    List<String> cleanTags = tags.stream()
                            .filter(Objects::nonNull)
                            .map(String::trim)
                            .filter(t -> !t.isEmpty())
                            .distinct()
                            .collect(Collectors.toList());

                    if (!cleanTags.isEmpty()) {
                        String linkTagsCypher = """
                            MATCH (m:Memory {id: $memoryId})
                            UNWIND $tags AS tagName
                            MERGE (t:Tag {userId: $userId, name: tagName})
                            MERGE (m)-[:HAS_TAG]->(t)
                        """;
                        tx.run(linkTagsCypher, Map.of(
                                "memoryId", memoryId.toString(),
                                "userId", userId.toString(),
                                "tags", cleanTags
                        )).consume();
                    }
                }

                // 4. Link people if present
                if (people != null && !people.isEmpty()) {
                    List<String> cleanPeople = people.stream()
                            .filter(Objects::nonNull)
                            .map(String::trim)
                            .filter(p -> !p.isEmpty())
                            .distinct()
                            .collect(Collectors.toList());

                    if (!cleanPeople.isEmpty()) {
                        String linkPeopleCypher = """
                            MATCH (m:Memory {id: $memoryId})
                            UNWIND $people AS personName
                            MERGE (p:Person {userId: $userId, name: personName})
                            MERGE (m)-[:MENTIONS]->(p)
                        """;
                        tx.run(linkPeopleCypher, Map.of(
                                "memoryId", memoryId.toString(),
                                "userId", userId.toString(),
                                "people", cleanPeople
                        )).consume();
                    }
                }

                // 5. Link places if present
                if (places != null && !places.isEmpty()) {
                    List<String> cleanPlaces = places.stream()
                            .filter(Objects::nonNull)
                            .map(String::trim)
                            .filter(p -> !p.isEmpty())
                            .distinct()
                            .collect(Collectors.toList());

                    if (!cleanPlaces.isEmpty()) {
                        String linkPlacesCypher = """
                            MATCH (m:Memory {id: $memoryId})
                            UNWIND $places AS placeName
                            MERGE (pl:Place {userId: $userId, name: placeName})
                            MERGE (m)-[:LOCATED_AT]->(pl)
                        """;
                        tx.run(linkPlacesCypher, Map.of(
                                "memoryId", memoryId.toString(),
                                "userId", userId.toString(),
                                "places", cleanPlaces
                        )).consume();
                    }
                }

                // 6. Prune orphaned user-scoped entities (Person, Place, Tag)
                pruneOrphanedUserEntities(tx, userId.toString());

                return null;
            });
            log.info("Synchronized memory {} into Neo4j knowledge graph for user {}", memoryId, userId);
        } catch (Exception e) {
            log.warn("Failed to synchronize memory {} to Neo4j knowledge graph: {}", memoryId, e.getMessage());
            throw e;
        }
    }

    /**
     * Deletes a memory from the Neo4j knowledge graph, strictly scoped to the authenticated user.
     * Also prunes orphaned user-scoped entities (Person, Place, Tag).
     */
    public void deleteMemory(UUID userId, UUID memoryId) {
        if (userId == null || memoryId == null) {
            return;
        }

        try (Session session = openSession()) {
            session.executeWrite(tx -> {
                String deleteMemoryCypher = """
                    MATCH (u:User {id: $userId})-[:OWNS]->(m:Memory {id: $memoryId})
                    DETACH DELETE m
                """;
                tx.run(deleteMemoryCypher, Map.of(
                        "userId", userId.toString(),
                        "memoryId", memoryId.toString()
                )).consume();

                pruneOrphanedUserEntities(tx, userId.toString());
                return null;
            });
            log.info("Deleted memory {} from Neo4j knowledge graph for user {}", memoryId, userId);
        } catch (Exception e) {
            log.warn("Failed to delete memory {} from Neo4j knowledge graph: {}", memoryId, e.getMessage());
            throw e;
        }
    }

    private void pruneOrphanedUserEntities(TransactionContext tx, String userIdStr) {
        String prunePeopleCypher = """
            MATCH (p:Person {userId: $userId}) WHERE NOT (p)<-[:MENTIONS]-() DELETE p
        """;
        tx.run(prunePeopleCypher, Map.of("userId", userIdStr)).consume();

        String prunePlacesCypher = """
            MATCH (pl:Place {userId: $userId}) WHERE NOT (pl)<-[:LOCATED_AT]-() DELETE pl
        """;
        tx.run(prunePlacesCypher, Map.of("userId", userIdStr)).consume();

        String pruneTagsCypher = """
            MATCH (t:Tag {userId: $userId}) WHERE NOT (t)<-[:HAS_TAG]-() DELETE t
        """;
        tx.run(pruneTagsCypher, Map.of("userId", userIdStr)).consume();
    }

    /**
     * Retrieves the knowledge graph for the specified user, anchored through (:User)-[:OWNS]->(:Memory).
     * Returns memory nodes and connected category, tag, person, and place entities and relationships.
     * All queries are parameterized, user-scoped, and bounded by the limit parameter.
     *
     * @param userId the authenticated user's ID
     * @param limit maximum number of memories to return (clamped between 1 and 500)
     * @return strongly typed MemoryGraphResponse containing nodes and edges
     */
    public MemoryGraphResponse getUserGraph(UUID userId, int limit) {
        if (userId == null) {
            return new MemoryGraphResponse(Collections.emptyList(), Collections.emptyList());
        }

        int safeLimit = Math.min(Math.max(limit, 1), 500);

        try (Session session = openSession()) {
            return session.executeRead(tx -> {
                String cypher = """
                    MATCH (u:User {id: $userId})-[:OWNS]->(m:Memory)
                    WITH m ORDER BY m.createdAt DESC LIMIT $limit
                    OPTIONAL MATCH (m)-[r:IN_CATEGORY|HAS_TAG|MENTIONS|LOCATED_AT]->(target)
                    WHERE (target:Category) OR (target.userId = $userId)
                    RETURN m.id AS memoryId,
                           m.title AS memoryTitle,
                           m.category AS memoryCategory,
                           m.createdAt AS memoryCreatedAt,
                           type(r) AS relType,
                           labels(target) AS targetLabels,
                           target.name AS targetName
                """;

                Map<String, Object> params = Map.of(
                        "userId", userId.toString(),
                        "limit", safeLimit
                );

                var result = tx.run(cypher, params);

                Map<String, GraphNodeDto> nodesMap = new LinkedHashMap<>();
                Map<String, GraphEdgeDto> edgesMap = new LinkedHashMap<>();

                while (result.hasNext()) {
                    var record = result.next();
                    var memoryIdVal = record.get("memoryId");
                    if (memoryIdVal.isNull()) {
                        continue;
                    }
                    String memoryId = memoryIdVal.asString();

                    if (!nodesMap.containsKey(memoryId)) {
                        String title = record.get("memoryTitle").isNull() ? "" : record.get("memoryTitle").asString("");
                        String displayTitle = (title != null && !title.trim().isEmpty()) ? title.trim() : "Untitled Memory";

                        Map<String, Object> props = new HashMap<>();
                        if (!record.get("memoryCreatedAt").isNull()) {
                            props.put("createdAt", record.get("memoryCreatedAt").asString());
                        }
                        if (!record.get("memoryCategory").isNull()) {
                            props.put("category", record.get("memoryCategory").asString());
                        }

                        nodesMap.put(memoryId, new GraphNodeDto(memoryId, displayTitle, "Memory", props));
                    }

                    var relTypeVal = record.get("relType");
                    var targetNameVal = record.get("targetName");

                    if (!relTypeVal.isNull() && !targetNameVal.isNull()) {
                        String relType = relTypeVal.asString();
                        String targetName = targetNameVal.asString().trim();

                        if (!targetName.isEmpty()) {
                            List<String> labels = Collections.emptyList();
                            var targetLabelsVal = record.get("targetLabels");
                            if (!targetLabelsVal.isNull()) {
                                labels = targetLabelsVal.asList(Value::asString);
                            }

                            String targetType = resolveTargetType(labels, relType);
                            String targetId = targetType.toLowerCase() + ":" + targetName;

                            if (!nodesMap.containsKey(targetId)) {
                                nodesMap.put(targetId, new GraphNodeDto(targetId, targetName, targetType, Collections.emptyMap()));
                            }

                            String edgeId = memoryId + "->" + relType + "->" + targetId;
                            if (!edgesMap.containsKey(edgeId)) {
                                edgesMap.put(edgeId, new GraphEdgeDto(edgeId, memoryId, targetId, relType));
                            }
                        }
                    }
                }

                return new MemoryGraphResponse(new ArrayList<>(nodesMap.values()), new ArrayList<>(edgesMap.values()));
            });
        } catch (Exception e) {
            log.warn("Failed to query knowledge graph from Neo4j for user {}: {}", userId, e.getMessage());
            throw new GraphServiceException("Failed to query knowledge graph", e);
        }
    }

    public MemoryGraphResponse getUserGraph(UUID userId) {
        return getUserGraph(userId, 100);
    }

    private String resolveTargetType(List<String> labels, String relType) {
        if (labels != null) {
            if (labels.contains("Person")) return "Person";
            if (labels.contains("Place")) return "Place";
            if (labels.contains("Tag")) return "Tag";
            if (labels.contains("Category")) return "Category";
            if (!labels.isEmpty()) return labels.get(0);
        }
        if ("MENTIONS".equalsIgnoreCase(relType)) return "Person";
        if ("LOCATED_AT".equalsIgnoreCase(relType)) return "Place";
        if ("HAS_TAG".equalsIgnoreCase(relType)) return "Tag";
        if ("IN_CATEGORY".equalsIgnoreCase(relType)) return "Category";
        return "Entity";
    }

    /**
     * Returns the underlying Neo4j Driver instance.
     *
     * @return the driver.
     */
    public Driver getDriver() {
        return driver;
    }
}
