package com.memoraai.service;


import com.memoraai.dto.AiProcessMemoryResponse;

import com.memoraai.dto.AskMemoryRequest;
import com.memoraai.dto.AskMemoryResponse;
import com.memoraai.dto.CreateMemoryRequest;
import com.memoraai.dto.MemoryResponse;
import com.memoraai.dto.MemorySearchResponse;
import com.memoraai.dto.RagAnswerResponse;
import com.memoraai.dto.ReindexResponse;
import com.memoraai.dto.SourceMemoryResponse;
import com.memoraai.dto.UpdateMemoryRequest;
import com.memoraai.entity.Memory;
import com.memoraai.entity.User;
import com.memoraai.exception.ResourceNotFoundException;
import com.memoraai.repository.MemoryRepository;
import com.memoraai.repository.UserRepository;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service

public class MemoryService {

    private final MemoryRepository memoryRepository;
    private final UserRepository userRepository;
    private final AiService aiService;
    private final VectorStoreService vectorStoreService;

    public MemoryService(MemoryRepository memoryRepository,
                         UserRepository userRepository,
                         AiService aiService,
                         VectorStoreService vectorStoreService) {
        this.memoryRepository = memoryRepository;
        this.userRepository = userRepository;
        this.aiService = aiService;
        this.vectorStoreService = vectorStoreService;
    }

    private User getAuthenticatedUser(String email) {
        return userRepository.findByEmail(email.toLowerCase().trim())
                .orElseThrow(() -> new UsernameNotFoundException("User not found with email: " + email));
    }

    @Transactional
    public MemoryResponse createMemory(CreateMemoryRequest request, String userEmail) {
        User user = getAuthenticatedUser(userEmail);

        Memory memory = new Memory(
                user,
                request.getTitle().trim(),
                request.getContent().trim(),
                request.getCategory() != null ? request.getCategory().trim() : null,
                request.getTags() != null ? request.getTags().trim() : null
        );

        Memory savedMemory = memoryRepository.save(memory);
        vectorStoreService.indexMemory(savedMemory);
        return MemoryResponse.fromEntity(savedMemory);
    }

    @Transactional(readOnly = true)
    public List<MemoryResponse> getUserMemories(String userEmail) {
        User user = getAuthenticatedUser(userEmail);

        return memoryRepository.findAllByUserIdOrderByCreatedAtDesc(user.getId())
                .stream()
                .map(MemoryResponse::fromEntity)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public MemoryResponse getMemory(UUID memoryId, String userEmail) {
        User user = getAuthenticatedUser(userEmail);

        Memory memory = memoryRepository.findByIdAndUserId(memoryId, user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Memory not found with id: " + memoryId));

        return MemoryResponse.fromEntity(memory);
    }

    @Transactional
    public MemoryResponse updateMemory(UUID memoryId, UpdateMemoryRequest request, String userEmail) {
        User user = getAuthenticatedUser(userEmail);

        Memory memory = memoryRepository.findByIdAndUserId(memoryId, user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Memory not found with id: " + memoryId));

        memory.setTitle(request.getTitle().trim());
        memory.setContent(request.getContent().trim());
        memory.setCategory(request.getCategory() != null ? request.getCategory().trim() : null);
        memory.setTags(request.getTags() != null ? request.getTags().trim() : null);

        Memory updatedMemory = memoryRepository.save(memory);
        vectorStoreService.indexMemory(updatedMemory);
        return MemoryResponse.fromEntity(updatedMemory);
    }

    @Transactional
    public void deleteMemory(UUID memoryId, String userEmail) {
        User user = getAuthenticatedUser(userEmail);

        Memory memory = memoryRepository.findByIdAndUserId(memoryId, user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Memory not found with id: " + memoryId));

        memoryRepository.delete(memory);
        vectorStoreService.deleteMemoryIndex(memoryId);
    }

    @Transactional(readOnly = true)
    public AiProcessMemoryResponse processMemory(UUID memoryId, String userEmail) {
        User user = getAuthenticatedUser(userEmail);

        Memory memory = memoryRepository.findByIdAndUserId(memoryId, user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Memory not found with id: " + memoryId));

        return aiService.processMemory(memory);
    }

    @Transactional(readOnly = true)
    public List<MemorySearchResponse> searchMemories(String query, String userEmail) {
        User user = getAuthenticatedUser(userEmail);
        if (query == null || query.trim().isEmpty()) {
            return Collections.emptyList();
        }

        List<VectorStoreService.VectorSearchResult> vectorResults = vectorStoreService.search(user.getId(), query.trim(), 20);
        if (vectorResults.isEmpty()) {
            return Collections.emptyList();
        }

        List<UUID> memoryIds = vectorResults.stream()
                .map(VectorStoreService.VectorSearchResult::getMemoryId)
                .collect(Collectors.toList());

        List<Memory> memories = memoryRepository.findAllByIdInAndUserId(memoryIds, user.getId());
        Map<UUID, Memory> memoryMap = memories.stream()
                .collect(Collectors.toMap(Memory::getId, m -> m));

        List<MemorySearchResponse> searchResponses = new ArrayList<>();
        for (VectorStoreService.VectorSearchResult res : vectorResults) {
            Memory memory = memoryMap.get(res.getMemoryId());
            if (memory != null) {
                searchResponses.add(MemorySearchResponse.fromEntity(memory, res.getScore()));
            }
        }
        return searchResponses;
    }

    @Transactional(readOnly = true)
    public AskMemoryResponse askQuestion(AskMemoryRequest request, String userEmail) {

        User user = getAuthenticatedUser(userEmail);
        if (request == null || request.getQuestion() == null || request.getQuestion().trim().isEmpty()) {
            return new AskMemoryResponse("I don't have any memory recorded about that.", Collections.emptyList(), 0);
        }

        String question = request.getQuestion().trim();
        List<VectorStoreService.VectorSearchResult> vectorResults = vectorStoreService.search(user.getId(), question, 20);
        if (vectorResults.isEmpty()) {
            return new AskMemoryResponse("I don't have any memory recorded about that.", Collections.emptyList(), 0);
        }

        List<UUID> memoryIds = vectorResults.stream()
                .map(VectorStoreService.VectorSearchResult::getMemoryId)
                .collect(Collectors.toList());

        List<Memory> memories = memoryRepository.findAllByIdInAndUserId(memoryIds, user.getId());
        if (memories.isEmpty()) {
            return new AskMemoryResponse("I don't have any memory recorded about that.", Collections.emptyList(), 0);
        }

        Map<UUID, Memory> memoryMap = memories.stream()
                .collect(Collectors.toMap(Memory::getId, m -> m));

        List<Memory> validatedMemories = new ArrayList<>();
        List<SourceMemoryResponse> sources = new ArrayList<>();

        for (VectorStoreService.VectorSearchResult res : vectorResults) {
            Memory memory = memoryMap.get(res.getMemoryId());
            if (memory != null) {
                validatedMemories.add(memory);
                sources.add(SourceMemoryResponse.fromEntity(memory, res.getScore()));
            }
        }

        if (validatedMemories.isEmpty()) {
            return new AskMemoryResponse("I don't have any memory recorded about that.", Collections.emptyList(), 0);
        }

        String answer;
        try {
            RagAnswerResponse ragResponse = aiService.generateRagAnswer(question, validatedMemories);
            answer = (ragResponse != null && ragResponse.getAnswer() != null)
                    ? ragResponse.getAnswer()
                    : "I don't have any memory recorded about that.";
        } catch (Exception ex) {
            String errorDetail = ex.getMessage();
            if (errorDetail == null || errorDetail.trim().isEmpty()) {
                errorDetail = "An error occurred while communicating with the AI service.";
            }
            answer = "AI generation unavailable: " + errorDetail;
        }

        return new AskMemoryResponse(answer, sources, sources.size());
    }

    @Transactional(readOnly = true)
    public ReindexResponse reindexUserMemories(String userEmail) {
        User user = getAuthenticatedUser(userEmail);
        List<Memory> memories = memoryRepository.findAllByUserIdOrderByCreatedAtDesc(user.getId());
        int count = 0;
        for (Memory memory : memories) {
            vectorStoreService.indexMemory(memory);
            count++;
        }
        return new ReindexResponse("Successfully re-indexed " + count + " memories into Qdrant", count);
    }
}

