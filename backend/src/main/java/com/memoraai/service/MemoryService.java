package com.memoraai.service;

import com.memoraai.dto.AiProcessMemoryResponse;
import com.memoraai.dto.AskMemoryRequest;
import com.memoraai.dto.AskMemoryResponse;
import com.memoraai.dto.CreateMemoryRequest;
import com.memoraai.dto.EntityExtractionResponse;
import com.memoraai.dto.GraphReindexResponse;
import com.memoraai.dto.MemoryGraphResponse;
import com.memoraai.dto.MemoryResponse;
import com.memoraai.dto.MemoryPageResponse;
import com.memoraai.dto.MemorySearchResponse;
import com.memoraai.dto.RagAnswerResponse;
import com.memoraai.dto.ReindexResponse;
import com.memoraai.dto.SourceMemoryResponse;
import com.memoraai.dto.UpdateMemoryRequest;
import com.memoraai.entity.Memory;
import com.memoraai.entity.MemoryMedia;
import com.memoraai.entity.User;
import com.memoraai.entity.CareCircleCapability;
import com.memoraai.exception.GraphServiceException;
import com.memoraai.exception.ResourceNotFoundException;
import com.memoraai.repository.MemoryMediaRepository;
import com.memoraai.repository.MemoryRepository;
import com.memoraai.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

@Service
public class MemoryService {

    private static final Logger log = LoggerFactory.getLogger(MemoryService.class);

    private final MemoryRepository memoryRepository;
    private final UserRepository userRepository;
    private final AiService aiService;
    private final VectorStoreService vectorStoreService;
    private final MemoryMediaRepository memoryMediaRepository;
    private final StorageService storageService;
    private final GraphStoreService graphStoreService;
    private final CareCircleAuthorizationService authorizationService;
    private final ExecutorService backgroundTaskExecutor = Executors.newFixedThreadPool(4);

    public MemoryService(MemoryRepository memoryRepository,
                         UserRepository userRepository,
                         AiService aiService,
                         VectorStoreService vectorStoreService,
                         MemoryMediaRepository memoryMediaRepository,
                         StorageService storageService,
                         GraphStoreService graphStoreService,
                         CareCircleAuthorizationService authorizationService) {
        this.memoryRepository = memoryRepository;
        this.userRepository = userRepository;
        this.aiService = aiService;
        this.vectorStoreService = vectorStoreService;
        this.memoryMediaRepository = memoryMediaRepository;
        this.storageService = storageService;
        this.graphStoreService = graphStoreService;
        this.authorizationService = authorizationService;
    }

    private User getAuthenticatedUser(String email) {
        return userRepository.findByEmail(email.toLowerCase().trim())
                .orElseThrow(() -> new UsernameNotFoundException("User not found with email: " + email));
    }

    @Transactional
    public MemoryResponse createMemory(CreateMemoryRequest request, String userEmail) {
        long tStart = System.currentTimeMillis();
        User user = getAuthenticatedUser(userEmail);

        Memory memory = new Memory(
                user,
                request.getTitle().trim(),
                request.getContent().trim(),
                request.getCategory() != null ? request.getCategory().trim() : null,
                request.getTags() != null ? request.getTags().trim() : null
        );

        long tDbStart = System.currentTimeMillis();
        Memory savedMemory = memoryRepository.save(memory);
        long tDb = System.currentTimeMillis() - tDbStart;

        long tVectorStart = System.currentTimeMillis();
        try {
            vectorStoreService.indexMemory(savedMemory);
        } catch (RuntimeException ex) {
            log.warn("Memory {} was created but vector indexing failed: {}",
                    savedMemory.getId(), ex.getMessage());
        }
        long tVector = System.currentTimeMillis() - tVectorStart;

        registerAfterCommitSync(
                user.getId(),
                savedMemory.getId(),
                savedMemory.getTitle(),
                savedMemory.getCategory(),
                savedMemory.getTags(),
                savedMemory.getContent(),
                savedMemory.getCreatedAt(),
                savedMemory.getUpdatedAt()
        );

        long tTotal = System.currentTimeMillis() - tStart;
        log.info("[TIMING] Memory {} created in {}ms (db={}ms, vectorIndex={}ms, graphSync=async)",
                savedMemory.getId(), tTotal, tDb, tVector);

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

    private User requirePatient(UUID patientId, User actor, CareCircleCapability capability) {
        authorizationService.requirePatientAccess(actor.getId(), patientId);
        if (!actor.getId().equals(patientId)) {
            authorizationService.requireCapability(actor.getId(), patientId, capability);
        }
        return userRepository.findById(patientId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        CareCircleAuthorizationService.PATIENT_ACCESS_NOT_FOUND));
    }

    private Memory requirePatientMemory(UUID patientId, UUID memoryId) {
        return memoryRepository.findByIdAndUserId(memoryId, patientId)
                .orElseThrow(() -> new ResourceNotFoundException("Memory not found with id: " + memoryId));
    }

    private void requireFamilyContributionOwner(Memory memory, User actor) {
        if (actor.getId().equals(memory.getUser().getId())) {
            return;
        }
        if (memory.getContributedBy() == null
                || !memory.getContributedBy().getId().equals(actor.getId())) {
            throw new AccessDeniedException(
                    CareCircleAuthorizationService.INSUFFICIENT_PERMISSION);
        }
    }

    @Transactional
    public MemoryResponse createForPatient(UUID patientId, CreateMemoryRequest request, String actorEmail) {
        User actor = getAuthenticatedUser(actorEmail);
        User patient = requirePatient(patientId, actor, CareCircleCapability.CONTRIBUTE_MEMORIES);

        Memory memory = actor.getId().equals(patientId)
                ? new Memory(patient, request.getTitle().trim(), request.getContent().trim(),
                        request.getCategory() != null ? request.getCategory().trim() : null,
                        request.getTags() != null ? request.getTags().trim() : null)
                : new Memory(patient, actor, request.getTitle().trim(), request.getContent().trim(),
                        request.getCategory() != null ? request.getCategory().trim() : null,
                        request.getTags() != null ? request.getTags().trim() : null);

        Memory saved = memoryRepository.save(memory);
        try {
            vectorStoreService.indexMemory(saved);
        } catch (RuntimeException ex) {
            log.warn("Memory {} was contributed but vector indexing failed: {}",
                    saved.getId(), ex.getMessage());
        }
        registerAfterCommitSync(
                patient.getId(), saved.getId(), saved.getTitle(), saved.getCategory(),
                saved.getTags(), saved.getContent(), saved.getCreatedAt(), saved.getUpdatedAt());
        return MemoryResponse.fromEntity(saved, actor.getId().equals(patientId) ? null : actor);
    }

    @Transactional(readOnly = true)
    public MemoryPageResponse listForPatient(UUID patientId, int page, int size, String actorEmail) {
        User actor = getAuthenticatedUser(actorEmail);
        requirePatient(patientId, actor, CareCircleCapability.VIEW_MEMORIES);
        if (page < 0) {
            throw new IllegalArgumentException("Page number must not be negative");
        }
        if (size < 1 || size > 100) {
            throw new IllegalArgumentException("Page size must be between 1 and 100");
        }

        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<Memory> result = memoryRepository.findAllByUserIdOrderByCreatedAtDesc(patientId, pageable);
        Map<UUID, User> contributorIds = new HashMap<>();
        Set<UUID> ids = new HashSet<>();
        result.getContent().forEach(memory -> {
            if (memory.getContributedBy() != null) {
                ids.add(memory.getContributedBy().getId());
            }
        });
        if (!ids.isEmpty()) {
            userRepository.findAllById(ids).forEach(user -> contributorIds.put(user.getId(), user));
        }

        List<MemoryResponse> content = result.getContent().stream()
                .map(memory -> {
                    User contributor = memory.getContributedBy() == null ? null
                            : contributorIds.get(memory.getContributedBy().getId());
                    return MemoryResponse.fromEntity(memory, contributor);
                })
                .toList();
        return new MemoryPageResponse(content, result.getNumber(), result.getSize(),
                result.getTotalElements(), result.getTotalPages());
    }

    @Transactional(readOnly = true)
    public MemoryResponse getForPatient(UUID patientId, UUID memoryId, String actorEmail) {
        User actor = getAuthenticatedUser(actorEmail);
        requirePatient(patientId, actor, CareCircleCapability.VIEW_MEMORIES);
        Memory memory = requirePatientMemory(patientId, memoryId);
        User contributor = memory.getContributedBy() == null ? null
                : userRepository.findById(memory.getContributedBy().getId()).orElse(null);
        return MemoryResponse.fromEntity(memory, contributor);
    }

    @Transactional
    public MemoryResponse updateForPatient(UUID patientId, UUID memoryId,
                                           UpdateMemoryRequest request, String actorEmail) {
        User actor = getAuthenticatedUser(actorEmail);
        requirePatient(patientId, actor, CareCircleCapability.CONTRIBUTE_MEMORIES);
        Memory memory = requirePatientMemory(patientId, memoryId);
        requireFamilyContributionOwner(memory, actor);

        memory.setTitle(request.getTitle().trim());
        memory.setContent(request.getContent().trim());
        memory.setCategory(request.getCategory() != null ? request.getCategory().trim() : null);
        memory.setTags(request.getTags() != null ? request.getTags().trim() : null);
        Memory updated = memoryRepository.save(memory);
        try {
            vectorStoreService.indexMemory(updated);
        } catch (RuntimeException ex) {
            log.warn("Memory {} was updated but vector indexing failed: {}",
                    updated.getId(), ex.getMessage());
        }
        registerAfterCommitSync(
                updated.getUser().getId(), updated.getId(), updated.getTitle(),
                updated.getCategory(), updated.getTags(), updated.getContent(),
                updated.getCreatedAt(), updated.getUpdatedAt());
        User contributor = updated.getContributedBy() == null ? null : updated.getContributedBy();
        return MemoryResponse.fromEntity(updated, contributor);
    }

    @Transactional
    public void deleteForPatient(UUID patientId, UUID memoryId, String actorEmail) {
        User actor = getAuthenticatedUser(actorEmail);
        requirePatient(patientId, actor, CareCircleCapability.CONTRIBUTE_MEMORIES);
        Memory memory = requirePatientMemory(patientId, memoryId);
        requireFamilyContributionOwner(memory, actor);

        for (MemoryMedia media : memoryMediaRepository.findAllByMemoryId(memoryId)) {
            storageService.deleteObject(media.getS3Key());
        }
        memoryRepository.delete(memory);
        try {
            vectorStoreService.deleteMemoryIndex(memoryId);
        } catch (RuntimeException ex) {
            log.warn("Memory {} was deleted but vector index cleanup failed: {}",
                    memoryId, ex.getMessage());
        }
        registerAfterCommitDelete(patientId, memoryId);
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
        try {
            vectorStoreService.indexMemory(updatedMemory);
        } catch (RuntimeException ex) {
            log.warn("Memory {} was updated but vector indexing failed: {}",
                    updatedMemory.getId(), ex.getMessage());
        }
        registerAfterCommitSync(
                user.getId(),
                updatedMemory.getId(),
                updatedMemory.getTitle(),
                updatedMemory.getCategory(),
                updatedMemory.getTags(),
                updatedMemory.getContent(),
                updatedMemory.getCreatedAt(),
                updatedMemory.getUpdatedAt()
        );
        return MemoryResponse.fromEntity(updatedMemory);
    }

    @Transactional
    public void deleteMemory(UUID memoryId, String userEmail) {
        User user = getAuthenticatedUser(userEmail);

        Memory memory = memoryRepository.findByIdAndUserId(memoryId, user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Memory not found with id: " + memoryId));

        UUID memoryIdToDelete = memory.getId();
        UUID userId = user.getId();

        // Delete all associated MinIO objects before cascading metadata delete in PostgreSQL.
        // If MinIO deletion fails, the exception aborts the transaction and preserves metadata in PostgreSQL.
        List<MemoryMedia> mediaList = memoryMediaRepository.findAllByMemoryId(memoryId);
        for (MemoryMedia media : mediaList) {
            storageService.deleteObject(media.getS3Key());
        }

        memoryRepository.delete(memory);
        try {
            vectorStoreService.deleteMemoryIndex(memoryId);
        } catch (RuntimeException ex) {
            log.warn("Memory {} was deleted but vector index cleanup failed: {}",
                    memoryId, ex.getMessage());
        }
        registerAfterCommitDelete(userId, memoryIdToDelete);
    }

    private void registerAfterCommitSync(UUID userId, UUID memoryId, String title, String category,
                                         String tags, String content, Instant createdAt, Instant updatedAt) {
        Runnable syncTask = () -> {
            try {
                EntityExtractionResponse extraction = aiService.extractEntities(title, content);
                List<String> parsedTags = parseTags(tags);
                List<String> people = extraction != null && extraction.getPeople() != null
                        ? extraction.getPeople() : Collections.emptyList();
                List<String> places = extraction != null && extraction.getPlaces() != null
                        ? extraction.getPlaces() : Collections.emptyList();

                graphStoreService.syncMemory(
                        userId,
                        memoryId,
                        title,
                        category,
                        parsedTags,
                        people,
                        places,
                        createdAt,
                        updatedAt
                );
            } catch (Exception e) {
                log.warn("Failed to synchronize memory {} to Neo4j knowledge graph after commit: {}. Reconciliation may be required.", memoryId, e.getMessage());
            }
        };

        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    backgroundTaskExecutor.submit(syncTask);
                }
            });
        } else {
            backgroundTaskExecutor.submit(syncTask);
        }
    }

    private void registerAfterCommitDelete(UUID userId, UUID memoryId) {
        Runnable deleteTask = () -> {
            try {
                graphStoreService.deleteMemory(userId, memoryId);
            } catch (Exception e) {
                log.warn("Failed to delete memory {} from Neo4j knowledge graph after commit: {}. Reconciliation may be required.", memoryId, e.getMessage());
            }
        };

        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    backgroundTaskExecutor.submit(deleteTask);
                }
            });
        } else {
            backgroundTaskExecutor.submit(deleteTask);
        }
    }

    private List<String> parseTags(String rawTags) {
        if (rawTags == null || rawTags.trim().isEmpty()) {
            return Collections.emptyList();
        }
        return Arrays.stream(rawTags.split(","))
                .map(String::trim)
                .filter(t -> !t.isEmpty())
                .distinct()
                .collect(Collectors.toList());
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
        long tStart = System.currentTimeMillis();
        User user = getAuthenticatedUser(userEmail);
        if (request == null || request.getQuestion() == null || request.getQuestion().trim().isEmpty()) {
            return new AskMemoryResponse("I don't have any memory recorded about that.", Collections.emptyList(), 0);
        }

        String question = request.getQuestion().trim();
        long tSearchStart = System.currentTimeMillis();
        List<VectorStoreService.VectorSearchResult> vectorResults = vectorStoreService.search(user.getId(), question, 5);
        long tSearch = System.currentTimeMillis() - tSearchStart;

        long tHydrateStart = System.currentTimeMillis();
        List<UUID> memoryIds = vectorResults.stream()
                .map(VectorStoreService.VectorSearchResult::getMemoryId)
                .collect(Collectors.toList());

        List<Memory> memories = memoryIds.isEmpty()
                ? Collections.emptyList()
                : memoryRepository.findAllByIdInAndUserId(memoryIds, user.getId());
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
        long tHydrate = System.currentTimeMillis() - tHydrateStart;

        long tRagStart = System.currentTimeMillis();
        RagAnswerResponse ragResponse = aiService.generateRagAnswer(question, validatedMemories);
        long tRag = System.currentTimeMillis() - tRagStart;

        long tTotal = System.currentTimeMillis() - tStart;
        log.info("[TIMING] Ask AI completed in {}ms (vectorSearch={}ms, hydration={}ms, ragAnswer={}ms, sources={})",
                tTotal, tSearch, tHydrate, tRag, sources.size());

        return new AskMemoryResponse(
                ragResponse.getAnswer(),
                sources,
                sources.size(),
                ragResponse.getProvider(),
                ragResponse.getModel()
        );
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

    @Transactional(readOnly = true)
    public MemoryGraphResponse getGraph(String userEmail, int limit) {
        User user = getAuthenticatedUser(userEmail);
        return graphStoreService.getUserGraph(user.getId(), limit);
    }

    @Transactional(readOnly = true)
    public GraphReindexResponse reindexUserGraph(String userEmail, int page, int batchSize) {
        User user = getAuthenticatedUser(userEmail);

        if (!graphStoreService.verifyConnectivity()) {
            log.warn("Knowledge graph reindex aborted: Neo4j is unreachable");
            throw new GraphServiceException("Knowledge graph service is unreachable");
        }

        int safePage = Math.max(page, 0);
        int safeBatchSize = Math.min(Math.max(batchSize, 1), 100);

        Pageable pageable = PageRequest.of(safePage, safeBatchSize);
        Page<Memory> memoryPage = memoryRepository.findAllByUserIdOrderByCreatedAtDesc(user.getId(), pageable);

        int processed = 0;
        int synced = 0;
        int failed = 0;

        for (Memory memory : memoryPage.getContent()) {
            processed++;
            try {
                List<String> people = Collections.emptyList();
                List<String> places = Collections.emptyList();
                try {
                    EntityExtractionResponse extraction = aiService.extractEntities(memory.getTitle(), memory.getContent());
                    if (extraction != null) {
                        people = extraction.getPeople();
                        places = extraction.getPlaces();
                    }
                } catch (Exception e) {
                    log.warn("Entity extraction non-fatal warning during graph reindex for memory {}: {}", memory.getId(), e.getMessage());
                }

                List<String> tagsList = memory.getTags() != null && !memory.getTags().trim().isEmpty()
                        ? Arrays.stream(memory.getTags().split(","))
                                .map(String::trim)
                                .filter(t -> !t.isEmpty())
                                .collect(Collectors.toList())
                        : Collections.emptyList();

                graphStoreService.syncMemory(
                        user.getId(),
                        memory.getId(),
                        memory.getTitle(),
                        memory.getCategory(),
                        tagsList,
                        people,
                        places,
                        memory.getCreatedAt(),
                        memory.getUpdatedAt()
                );
                synced++;
            } catch (Exception e) {
                failed++;
                log.warn("Failed to synchronize memory {} to Neo4j during graph reindex: {}", memory.getId(), e.getMessage());
            }
        }

        boolean hasMore = memoryPage.hasNext();
        String message;
        if (processed == 0) {
            message = "No memories found to synchronize.";
        } else if (failed == 0) {
            message = String.format("Successfully synchronized %d memories into knowledge graph (batch %d, total %d).",
                    synced, safePage, memoryPage.getTotalElements());
        } else {
            message = String.format("Partially synchronized %d memories: %d succeeded, %d failed. Retry failed batch on page %d.",
                    processed, synced, failed, safePage);
        }

        return new GraphReindexResponse(
                processed,
                synced,
                failed,
                hasMore,
                safePage,
                safeBatchSize,
                memoryPage.getTotalElements(),
                message
        );
    }
}
