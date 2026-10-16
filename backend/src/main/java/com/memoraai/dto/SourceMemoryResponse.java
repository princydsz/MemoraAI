package com.memoraai.dto;

import com.memoraai.entity.Memory;

import java.time.Instant;
import java.util.UUID;

public class SourceMemoryResponse {

    private UUID id;
    private String title;
    private String category;
    private String tags;
    private Instant createdAt;
    private Double similarityScore;

    public SourceMemoryResponse() {
    }

    public SourceMemoryResponse(UUID id, String title, String category, String tags, Instant createdAt, Double similarityScore) {
        this.id = id;
        this.title = title;
        this.category = category;
        this.tags = tags;
        this.createdAt = createdAt;
        this.similarityScore = similarityScore;
    }

    public static SourceMemoryResponse fromEntity(Memory memory, Double similarityScore) {
        return new SourceMemoryResponse(
                memory.getId(),
                memory.getTitle(),
                memory.getCategory(),
                memory.getTags(),
                memory.getCreatedAt(),
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

    public Double getSimilarityScore() {
        return similarityScore;
    }

    public void setSimilarityScore(Double similarityScore) {
        this.similarityScore = similarityScore;
    }
}
