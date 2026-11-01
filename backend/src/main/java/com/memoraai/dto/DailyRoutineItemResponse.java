package com.memoraai.dto;

import com.memoraai.entity.RoutineActivity;
import com.memoraai.entity.RoutinePeriod;

import java.time.Instant;
import java.time.LocalTime;
import java.util.UUID;

public class DailyRoutineItemResponse {
    private UUID id;
    private String title;
    private String description;
    private RoutinePeriod period;
    private LocalTime scheduledTime;
    private int sortOrder;
    private boolean completed;
    private Instant completedAt;
    private CompletedByResponse completedBy;

    public DailyRoutineItemResponse(RoutineActivity activity, boolean completed,
                                    Instant completedAt, CompletedByResponse completedBy) {
        this.id = activity.getId();
        this.title = activity.getTitle();
        this.description = activity.getDescription();
        this.period = activity.getPeriod();
        this.scheduledTime = activity.getScheduledTime();
        this.sortOrder = activity.getSortOrder();
        this.completed = completed;
        this.completedAt = completedAt;
        this.completedBy = completedBy;
    }

    public UUID getId() { return id; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public RoutinePeriod getPeriod() { return period; }
    public LocalTime getScheduledTime() { return scheduledTime; }
    public int getSortOrder() { return sortOrder; }
    public boolean isCompleted() { return completed; }
    public Instant getCompletedAt() { return completedAt; }
    public CompletedByResponse getCompletedBy() { return completedBy; }
}
