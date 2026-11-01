package com.memoraai.dto;

import com.memoraai.entity.RoutineActivityCompletion;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public class CompletionResponse {
    private UUID id;
    private UUID activityId;
    private LocalDate completionDate;
    private Instant completedAt;
    private CompletedByResponse completedBy;

    public CompletionResponse(RoutineActivityCompletion completion, CompletedByResponse completedBy) {
        this.id = completion.getId();
        this.activityId = completion.getActivityId();
        this.completionDate = completion.getCompletionDate();
        this.completedAt = completion.getCompletedAt();
        this.completedBy = completedBy;
    }

    public UUID getId() { return id; }
    public UUID getActivityId() { return activityId; }
    public LocalDate getCompletionDate() { return completionDate; }
    public Instant getCompletedAt() { return completedAt; }
    public CompletedByResponse getCompletedBy() { return completedBy; }
}
