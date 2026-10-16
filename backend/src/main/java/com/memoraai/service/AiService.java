package com.memoraai.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.memoraai.dto.AiProcessMemoryRequest;
import com.memoraai.dto.AiProcessMemoryResponse;
import com.memoraai.dto.EmbeddingResponse;
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
import java.util.List;
import java.util.Map;

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

