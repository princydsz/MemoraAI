package com.memoraai.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.memoraai.dto.AiProcessMemoryRequest;
import com.memoraai.dto.AiProcessMemoryResponse;
import com.memoraai.dto.EmbeddingResponse;
import com.memoraai.dto.EntityExtractionRequest;
import com.memoraai.dto.EntityExtractionResponse;
import com.memoraai.dto.RagAnswerRequest;
import com.memoraai.dto.RagAnswerResponse;
import com.memoraai.entity.Memory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class AiService {

    private static final Logger log = LoggerFactory.getLogger(AiService.class);
    private final RestTemplate restTemplate;
    private final String aiServiceUrl;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public AiService(RestTemplate restTemplate, @Value("${ai.service.url}") String aiServiceUrl) {
        this.restTemplate = restTemplate;
        this.aiServiceUrl = aiServiceUrl;
    }

    public AiProcessMemoryResponse processMemory(Memory memory) {
        String url = aiServiceUrl + "/process-memory";
        AiProcessMemoryRequest request = new AiProcessMemoryRequest(
                memory.getTitle(),
                memory.getContent(),
                memory.getCategory(),
                memory.getTags()
        );

        return restTemplate.postForObject(url, request, AiProcessMemoryResponse.class);
    }

    public List<Double> generateEmbedding(String text) {
        if (text == null || text.trim().isEmpty()) {
            return Collections.emptyList();
        }

        String url = aiServiceUrl + "/embeddings";
        Map<String, String> request = new HashMap<>();
        request.put("text", text.trim());

        try {
            EmbeddingResponse response = restTemplate.postForObject(url, request, EmbeddingResponse.class);
            if (response != null && response.getEmbedding() != null) {
                return response.getEmbedding();
            }
        } catch (Exception e) {
            // Fallback for tests or if AI service is temporarily unreachable
        }
        return Collections.emptyList();
    }

    public EntityExtractionResponse extractEntities(String title, String content) {
        if (content == null || content.trim().isEmpty()) {
            return new EntityExtractionResponse(Collections.emptyList(), Collections.emptyList());
        }

        String url = aiServiceUrl + "/extract-entities";
        EntityExtractionRequest request = new EntityExtractionRequest(
                title != null ? title.trim() : "",
                content.trim()
        );

        try {
            EntityExtractionResponse response = restTemplate.postForObject(url, request, EntityExtractionResponse.class);
            if (response != null) {
                return new EntityExtractionResponse(
                        normalizeEntityNames(response.getPeople()),
                        normalizeEntityNames(response.getPlaces())
                );
            }
        } catch (Exception ex) {
            log.warn("AI service entity extraction failed or was unreachable: {}", ex.getMessage());
        }

        return new EntityExtractionResponse(Collections.emptyList(), Collections.emptyList());
    }

    private List<String> normalizeEntityNames(List<String> rawNames) {
        if (rawNames == null || rawNames.isEmpty()) {
            return Collections.emptyList();
        }
        List<String> normalized = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (String name : rawNames) {
            if (name == null) {
                continue;
            }
            String cleaned = name.trim();
            if (!cleaned.isEmpty() && cleaned.length() <= 100 && !cleaned.matches(".*[\\p{Cntrl}].*")) {
                String lower = cleaned.toLowerCase();
                if (!seen.contains(lower)) {
                    seen.add(lower);
                    normalized.add(cleaned);
                }
            }
        }
        return normalized;
    }

    public RagAnswerResponse generateRagAnswer(String question, List<Memory> memories) {
        String url = aiServiceUrl + "/rag/answer";
        List<RagAnswerRequest.RagMemoryItem> memoryItems = new ArrayList<>();
        if (memories != null) {
            for (Memory m : memories) {
                memoryItems.add(new RagAnswerRequest.RagMemoryItem(
                        m.getId(),
                        m.getTitle(),
                        m.getContent(),
                        m.getCategory(),
                        m.getTags()
                ));
            }
        }

        RagAnswerRequest request = new RagAnswerRequest(question, memoryItems);
        try {
            return restTemplate.postForObject(url, request, RagAnswerResponse.class);
        } catch (HttpStatusCodeException ex) {
            String detail = extractDetailFromErrorResponse(ex.getResponseBodyAsString());
            if (detail == null || detail.trim().isEmpty()) {
                detail = "AI service request failed with status code " + ex.getStatusCode().value();
            }
            log.error("AI service error during RAG answer: {}", detail);
            throw new RuntimeException(detail, ex);
        } catch (RestClientException ex) {
            log.error("AI service communication error: {}", ex.getMessage());
            throw new RuntimeException("AI service is currently unreachable: " + ex.getMessage(), ex);
        }
    }

    private String extractDetailFromErrorResponse(String responseBody) {
        if (responseBody == null || responseBody.trim().isEmpty()) {
            return null;
        }
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            if (root.has("detail")) {
                JsonNode detailNode = root.get("detail");
                if (detailNode.isTextual()) {
                    return detailNode.asText();
                } else {
                    return detailNode.toString();
                }
            }
        } catch (Exception ignored) {
        }
        return responseBody.length() > 300 ? responseBody.substring(0, 300) : responseBody;
    }
}

