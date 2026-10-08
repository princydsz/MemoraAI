package com.memoraai.dto;

import com.memoraai.entity.Memory;

import java.time.Instant;
import java.util.UUID;

public class MemorySearchResponse {
    private UUID id;
    private String title;
    private String content;
    private String category;
    private String tags;
    private Instant createdAt;
    private Instant updatedAt;
    private Double similarityScore;

    public MemorySearchResponse() {
    }

    public MemorySearchResponse(UUID id, String title, String content, String category, String tags,
                                Instant createdAt, Instant updatedAt, Double similarityScore) {
        this.id = id;
        this.title = title;
        this.content = content;
        this.category = category;
        this.tags = tags;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.similarityScore = similarityScore;
    }

    public static MemorySearchResponse fromEntity(Memory memory, Double similarityScore) {
        return new MemorySearchResponse(
                memory.getId(),
                memory.getTitle(),
                memory.getContent(),
                memory.getCategory(),
                memory.getTags(),
                memory.getCreatedAt(),
                memory.getUpdatedAt(),
                similarityScore
        );
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getTags() {
        return tags;
    }

    public void setTags(String tags) {
        this.tags = tags;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    public Double getSimilarityScore() {
        return similarityScore;
    }

    public void setSimilarityScore(Double similarityScore) {
        this.similarityScore = similarityScore;
    }
}
