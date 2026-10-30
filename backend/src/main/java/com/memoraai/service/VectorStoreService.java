package com.memoraai.service;

import com.memoraai.entity.Memory;
import com.memoraai.exception.MemoryRetrievalException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.*;

@Service
public class VectorStoreService {

    private static final Logger logger = LoggerFactory.getLogger(VectorStoreService.class);

    private final RestTemplate restTemplate;
    private final AiService aiService;
    private final String qdrantHost;
    private final int qdrantPort;
    private final String collectionName;
    private final double scoreThreshold;

    public VectorStoreService(RestTemplate restTemplate,
                              AiService aiService,
                              @Value("${qdrant.host:localhost}") String qdrantHost,
                              @Value("${qdrant.port:6333}") int qdrantPort,
                              @Value("${qdrant.collection:memories}") String collectionName,
                              @Value("${qdrant.score-threshold:0.20}") double scoreThreshold) {
        this.restTemplate = restTemplate;
        this.aiService = aiService;
        this.qdrantHost = qdrantHost;
        this.qdrantPort = qdrantPort;
        this.collectionName = collectionName;
        this.scoreThreshold = scoreThreshold;
    }

    public static class VectorSearchResult {
        private final UUID memoryId;
        private final double score;

        public VectorSearchResult(UUID memoryId, double score) {
            this.memoryId = memoryId;
            this.score = score;
        }

        public UUID getMemoryId() {
            return memoryId;
        }

        public double getScore() {
            return score;
        }
    }

    private String getQdrantBaseUrl() {
        return "http://" + qdrantHost + ":" + qdrantPort;
    }

    public void initCollectionIfNeeded() {
        String url = getQdrantBaseUrl() + "/collections/" + collectionName;
        try {
            ResponseEntity<Map> response = restTemplate.getForEntity(url, Map.class);
            if (response.getStatusCode().is2xxSuccessful()) {
                return;
            }
        } catch (Exception ignored) {
            // Collection does not exist or Qdrant offline
        }

        // Create collection
        try {
            Map<String, Object> vectorParams = new HashMap<>();
            vectorParams.put("size", 384);
            vectorParams.put("distance", "Cosine");

            Map<String, Object> body = new HashMap<>();
            body.put("vectors", vectorParams);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);

            restTemplate.exchange(url, HttpMethod.PUT, entity, Map.class);
            logger.info("Initialized Qdrant collection '{}'", collectionName);
        } catch (Exception e) {
            logger.error("Could not initialize Qdrant collection '{}'.", collectionName);
            throw new MemoryRetrievalException("Memory index is unavailable.", e);
        }
    }

    public void indexMemory(Memory memory) {
        if (memory == null || memory.getId() == null) {
            return;
        }

        initCollectionIfNeeded();

        String searchableText = String.format("Title: %s | Content: %s | Category: %s | Tags: %s",
                memory.getTitle(),
                memory.getContent(),
                memory.getCategory() != null ? memory.getCategory() : "",
                memory.getTags() != null ? memory.getTags() : "");

        List<Double> vector = aiService.generateEmbedding(searchableText);
        if (vector.size() != 384) {
            throw new MemoryRetrievalException("Embedding service returned an invalid vector.");
        }

        try {
            String url = getQdrantBaseUrl() + "/collections/" + collectionName + "/points?wait=true";

            Map<String, Object> payload = new HashMap<>();
            payload.put("memory_id", memory.getId().toString());
            payload.put("user_id", memory.getUser().getId().toString());
            payload.put("title", memory.getTitle());
            payload.put("category", memory.getCategory());
            payload.put("tags", memory.getTags());

            Map<String, Object> point = new HashMap<>();
            point.put("id", memory.getId().toString());
            point.put("vector", vector);
            point.put("payload", payload);

            Map<String, Object> body = new HashMap<>();
            body.put("points", Collections.singletonList(point));

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);

            restTemplate.exchange(url, HttpMethod.PUT, entity, Map.class);
            logger.info("Indexed memory {} in Qdrant", memory.getId());
        } catch (Exception e) {
            logger.error("Failed to index memory {} in Qdrant.", memory.getId());
            throw new MemoryRetrievalException("Memory could not be indexed.", e);
        }
    }

    public void deleteMemoryIndex(UUID memoryId) {
        if (memoryId == null) {
            return;
        }

        try {
            String url = getQdrantBaseUrl() + "/collections/" + collectionName + "/points/delete?wait=true";

            Map<String, Object> body = new HashMap<>();
            body.put("points", Collections.singletonList(memoryId.toString()));

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);

            restTemplate.postForEntity(url, entity, Map.class);
            logger.info("Deleted Qdrant vector point for memory {}", memoryId);
        } catch (Exception e) {
            logger.error("Failed to delete memory {} from Qdrant.", memoryId);
            throw new MemoryRetrievalException("Memory index could not be updated.", e);
        }
    }

    public List<VectorSearchResult> search(UUID userId, String queryText, int limit) {
        if (userId == null || queryText == null || queryText.trim().isEmpty()) {
            return Collections.emptyList();
        }

        initCollectionIfNeeded();

        List<Double> queryVector = aiService.generateEmbedding(queryText.trim());
        if (queryVector.size() != 384) {
            throw new MemoryRetrievalException("Embedding service returned an invalid vector.");
        }

        try {
            String url = getQdrantBaseUrl() + "/collections/" + collectionName + "/points/search";

            Map<String, Object> userMatch = new HashMap<>();
            userMatch.put("value", userId.toString());

            Map<String, Object> userFilter = new HashMap<>();
            userFilter.put("key", "user_id");
            userFilter.put("match", userMatch);

            Map<String, Object> filter = new HashMap<>();
            filter.put("must", Collections.singletonList(userFilter));

            Map<String, Object> body = new HashMap<>();
            body.put("vector", queryVector);
            body.put("limit", limit > 0 ? limit : 20);
            body.put("with_payload", true);
            body.put("filter", filter);
            body.put("score_threshold", scoreThreshold);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);

            ResponseEntity<Map> response = restTemplate.postForEntity(url, entity, Map.class);
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                List<Map<String, Object>> resultList = (List<Map<String, Object>>) response.getBody().get("result");
                if (resultList != null) {
                    List<VectorSearchResult> searchResults = new ArrayList<>();
                    for (Map<String, Object> res : resultList) {
                        Map<String, Object> payload = (Map<String, Object>) res.get("payload");
                        Number scoreNum = (Number) res.get("score");
                        if (payload != null && payload.containsKey("memory_id")) {
                            String memIdStr = (String) payload.get("memory_id");
                            double score = scoreNum != null ? scoreNum.doubleValue() : 0.0;
                            if (score >= scoreThreshold) {
                                searchResults.add(new VectorSearchResult(UUID.fromString(memIdStr), score));
                            }
                        }
                    }
                    return searchResults;
                }
            }
            throw new MemoryRetrievalException("Memory search returned an invalid response.");
        } catch (MemoryRetrievalException ex) {
            throw ex;
        } catch (Exception e) {
            logger.error("Qdrant semantic search failed for user {}.", userId);
            throw new MemoryRetrievalException("Memory search is temporarily unavailable.", e);
        }
    }
}
