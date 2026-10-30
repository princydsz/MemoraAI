package com.memoraai.service;

import com.memoraai.dto.MemoryMediaResponse;
import com.memoraai.entity.Memory;
import com.memoraai.entity.MemoryMedia;
import com.memoraai.entity.User;
import com.memoraai.exception.InvalidMediaException;
import com.memoraai.exception.ResourceNotFoundException;
import com.memoraai.repository.MemoryMediaRepository;
import com.memoraai.repository.MemoryRepository;
import com.memoraai.repository.UserRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.PersistenceContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Dedicated application service for media attachment operations.
 * Isolates transaction boundaries, manages MinIO upload compensation, and enforces
 * concurrency-safe attachment limits.
 */
@Service
public class MediaService {

    private static final Logger log = LoggerFactory.getLogger(MediaService.class);
    public static final int MAX_MEDIA_PER_MEMORY = 5;

    private final MemoryRepository memoryRepository;
    private final MemoryMediaRepository memoryMediaRepository;
    private final UserRepository userRepository;
    private final StorageService storageService;

    @PersistenceContext
    private EntityManager entityManager;

    public MediaService(
            MemoryRepository memoryRepository,
            MemoryMediaRepository memoryMediaRepository,
            UserRepository userRepository,
            StorageService storageService) {
        this.memoryRepository = memoryRepository;
        this.memoryMediaRepository = memoryMediaRepository;
        this.userRepository = userRepository;
        this.storageService = storageService;
    }

    private User getAuthenticatedUser(String email) {
        return userRepository.findByEmail(email.toLowerCase().trim())
                .orElseThrow(() -> new UsernameNotFoundException("User not found with email: " + email));
    }

    /**
     * Uploads a batch of images to MinIO and persists metadata in PostgreSQL.
     * Uses pessimistic write locking on the parent memory record to protect against concurrent upload races.
     * Implements all-or-nothing semantics with automated object rollback if any step fails.
     */
    @Transactional
    public List<MemoryMediaResponse> uploadMedia(UUID memoryId, List<MultipartFile> files, String userEmail) {
        User user = getAuthenticatedUser(userEmail);

        if (files == null || files.isEmpty()) {
            throw new InvalidMediaException("No image files provided for upload.");
        }

        // Concurrency-safe lock: Acquire a row lock on the parent memory row
        Memory memory = entityManager.find(Memory.class, memoryId, LockModeType.PESSIMISTIC_WRITE);
        if (memory == null || !memory.getUser().getId().equals(user.getId())) {
            throw new ResourceNotFoundException("Memory not found with id: " + memoryId);
        }

        long currentCount = memoryMediaRepository.countByMemoryId(memoryId);
        if (currentCount + files.size() > MAX_MEDIA_PER_MEMORY) {
            throw new InvalidMediaException(String.format(
                    "Upload exceeds maximum limit of %d images per memory. Current: %d, Attempted: %d.",
                    MAX_MEDIA_PER_MEMORY, currentCount, files.size()));
        }

        // Pre-validate all media files (content-type magic bytes and size) before storing anything in MinIO
        for (MultipartFile file : files) {
            storageService.validateMedia(file);
        }

        // Upload to MinIO with all-or-nothing rollback compensation
        List<StorageService.UploadResult> uploadedResults = new ArrayList<>();

        // Register transaction synchronization to guarantee MinIO cleanup whenever the database
        // transaction rolls back (including when a failure occurs during the commit phase after this method returns).
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCompletion(int status) {
                    if (status == TransactionSynchronization.STATUS_ROLLED_BACK) {
                        log.info("Transaction rolled back. Cleaning up {} MinIO object(s)...", uploadedResults.size());
                        for (StorageService.UploadResult res : uploadedResults) {
                            try {
                                storageService.deleteObject(res.s3Key());
                            } catch (Exception cleanupEx) {
                                log.error("Failed to clean up MinIO object '{}' after transaction rollback: {}",
                                        res.s3Key(), cleanupEx.getMessage());
                            }
                        }
                    }
                }
            });
        }

        try {
            for (MultipartFile file : files) {
                StorageService.UploadResult uploadResult = storageService.uploadMedia(user.getId(), memoryId, file);
                uploadedResults.add(uploadResult);
            }

            List<MemoryMedia> savedEntities = new ArrayList<>();
            for (StorageService.UploadResult res : uploadedResults) {
                MemoryMedia media = new MemoryMedia(
                        memory,
                        user,
                        res.fileName(),
                        res.contentType(),
                        res.fileSize(),
                        res.s3Key()
                );
                savedEntities.add(memoryMediaRepository.save(media));
            }

            return savedEntities.stream()
                    .map(MemoryMediaResponse::fromEntity)
                    .collect(Collectors.toList());
        } catch (Exception e) {
            // Immediate compensation fallback for environments where transaction synchronization is inactive
            if (!TransactionSynchronizationManager.isSynchronizationActive()) {
                for (StorageService.UploadResult res : uploadedResults) {
                    try {
                        storageService.deleteObject(res.s3Key());
                    } catch (Exception cleanupEx) {
                        log.error("Failed to clean up MinIO object '{}' after failed upload batch: {}",
                                res.s3Key(), cleanupEx.getMessage());
                    }
                }
            }
            throw e;
        }
    }

    /**
     * Retrieves media metadata for attachments belonging to a memory owned by the authenticated user.
     */
    @Transactional(readOnly = true)
    public List<MemoryMediaResponse> getMediaForMemory(UUID memoryId, String userEmail) {
        User user = getAuthenticatedUser(userEmail);

        memoryRepository.findByIdAndUserId(memoryId, user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Memory not found with id: " + memoryId));

        return memoryMediaRepository.findAllByMemoryIdAndUserIdOrderByCreatedAtAsc(memoryId, user.getId())
                .stream()
                .map(MemoryMediaResponse::fromEntity)
                .collect(Collectors.toList());
    }

    /**
     * Retrieves media entity for streaming download, verifying memory and user ownership.
     */
    @Transactional(readOnly = true)
    public MemoryMedia getMediaForDownload(UUID memoryId, UUID mediaId, String userEmail) {
        User user = getAuthenticatedUser(userEmail);

        return memoryMediaRepository.findByIdAndMemoryIdAndUserId(mediaId, memoryId, user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Media not found with id: " + mediaId));
    }

    /**
     * Deletes a single media attachment.
     * Strategy: Remove the MinIO object first. If MinIO fails, the exception aborts the transaction
     * and metadata remains in PostgreSQL so the image is not left as an untracked orphan.
     */
    @Transactional
    public void deleteMedia(UUID memoryId, UUID mediaId, String userEmail) {
        User user = getAuthenticatedUser(userEmail);

        MemoryMedia media = memoryMediaRepository.findByIdAndMemoryIdAndUserId(mediaId, memoryId, user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Media not found with id: " + mediaId));

        storageService.deleteObject(media.getS3Key());
        memoryMediaRepository.delete(media);
    }
}
