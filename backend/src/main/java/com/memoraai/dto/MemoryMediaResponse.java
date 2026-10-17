package com.memoraai.dto;

import com.memoraai.entity.MemoryMedia;

import java.time.Instant;
import java.util.UUID;

public class MemoryMediaResponse {

    private UUID id;
    private UUID memoryId;
    private String fileName;
    private String contentType;
    private Long fileSize;
    private Instant createdAt;

    public MemoryMediaResponse() {
    }

    public MemoryMediaResponse(UUID id, UUID memoryId, String fileName, String contentType, Long fileSize, Instant createdAt) {
        this.id = id;
        this.memoryId = memoryId;
        this.fileName = fileName;
        this.contentType = contentType;
        this.fileSize = fileSize;
        this.createdAt = createdAt;
    }

    public static MemoryMediaResponse fromEntity(MemoryMedia media) {
        if (media == null) {
            return null;
        }
        return new MemoryMediaResponse(
                media.getId(),
                media.getMemory() != null ? media.getMemory().getId() : null,
                media.getFileName(),
                media.getContentType(),
                media.getFileSize(),
                media.getCreatedAt()
        );
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getMemoryId() {
        return memoryId;
    }

    public void setMemoryId(UUID memoryId) {
        this.memoryId = memoryId;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public String getContentType() {
        return contentType;
    }

    public void setContentType(String contentType) {
        this.contentType = contentType;
    }

    public Long getFileSize() {
        return fileSize;
    }

    public void setFileSize(Long fileSize) {
        this.fileSize = fileSize;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
