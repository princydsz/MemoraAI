package com.memoraai.service;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpEntity;
import org.springframework.web.client.RestTemplate;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class VectorStoreServiceTest {

    private static final String COLLECTION_URL = "http://qdrant:6333/collections/memories_test";
    private static final String SEARCH_URL = COLLECTION_URL + "/points/search";

    @Test
    void searchReturnsNaturalLanguageMatchAboveCalibratedThreshold() {
        RestTemplate restTemplate = mock(RestTemplate.class);
        AiService aiService = mock(AiService.class);
        UUID userId = UUID.randomUUID();
        UUID memoryId = UUID.randomUUID();

        when(restTemplate.getForEntity(COLLECTION_URL, Map.class))
                .thenReturn(ResponseEntity.ok(Map.of()));
        when(aiService.generateEmbedding("What do I remember about travel?"))
                .thenReturn(Collections.nCopies(384, 0.1));
        when(restTemplate.postForEntity(eq(SEARCH_URL), any(HttpEntity.class), eq(Map.class)))
                .thenReturn(ResponseEntity.ok(Map.of("result", List.of(Map.of(
                        "payload", Map.of("memory_id", memoryId.toString()),
                        "score", 0.30580837
                )))));

        VectorStoreService service = new VectorStoreService(
                restTemplate, aiService, "qdrant", 6333, "memories_test", 0.20);

        List<VectorStoreService.VectorSearchResult> results =
                service.search(userId, "What do I remember about travel?", 5);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).getMemoryId()).isEqualTo(memoryId);
        assertThat(results.get(0).getScore()).isEqualTo(0.30580837);

        ArgumentCaptor<HttpEntity> requestCaptor = ArgumentCaptor.forClass(HttpEntity.class);
        verify(restTemplate).postForEntity(eq(SEARCH_URL), requestCaptor.capture(), eq(Map.class));
        Map<?, ?> requestBody = (Map<?, ?>) requestCaptor.getValue().getBody();
        assertThat(requestBody.get("score_threshold")).isEqualTo(0.20);
        Map<?, ?> filter = (Map<?, ?>) requestBody.get("filter");
        Map<?, ?> userCondition = (Map<?, ?>) ((List<?>) filter.get("must")).get(0);
        Map<?, ?> userMatch = (Map<?, ?>) userCondition.get("match");
        assertThat(userMatch.get("value")).isEqualTo(userId.toString());
    }

    @Test
    void searchDoesNotReturnScoresBelowCalibratedThreshold() {
        RestTemplate restTemplate = mock(RestTemplate.class);
        AiService aiService = mock(AiService.class);
        UUID userId = UUID.randomUUID();
        UUID memoryId = UUID.randomUUID();

        when(restTemplate.getForEntity(COLLECTION_URL, Map.class))
                .thenReturn(ResponseEntity.ok(Map.of()));
        when(aiService.generateEmbedding("quantum physics"))
                .thenReturn(Collections.nCopies(384, 0.1));
        when(restTemplate.postForEntity(eq(SEARCH_URL), any(HttpEntity.class), eq(Map.class)))
                .thenReturn(ResponseEntity.ok(Map.of("result", List.of(Map.of(
                        "payload", Map.of("memory_id", memoryId.toString()),
                        "score", 0.023454905
                )))));

        VectorStoreService service = new VectorStoreService(
                restTemplate, aiService, "qdrant", 6333, "memories_test", 0.20);

        assertThat(service.search(userId, "quantum physics", 5)).isEmpty();
    }
}
