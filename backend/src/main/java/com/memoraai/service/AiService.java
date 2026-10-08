package com.memoraai.service;

import com.memoraai.dto.AiProcessMemoryRequest;
import com.memoraai.dto.AiProcessMemoryResponse;
import com.memoraai.entity.Memory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import com.memoraai.dto.EmbeddingResponse;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class AiService {

    private final RestTemplate restTemplate;
    private final String aiServiceUrl;

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
}
