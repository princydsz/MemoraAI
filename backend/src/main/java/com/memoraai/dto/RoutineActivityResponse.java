package com.memoraai.dto;

import com.memoraai.entity.RoutineActivity;
import com.memoraai.entity.RoutinePeriod;

import java.time.Instant;
import java.time.LocalTime;
import java.util.UUID;

public class RoutineActivityResponse {
    private UUID id;
    private String title;
    private String description;
    private RoutinePeriod period;
    private LocalTime scheduledTime;
    private int sortOrder;
    private boolean active;
    private UUID createdByUserId;
    private Instant createdAt;
    private Instant updatedAt;

    public RoutineActivityResponse() {}

    public RoutineActivityResponse(RoutineActivity activity) {
        this.id = activity.getId();
        this.title = activity.getTitle();
        this.description = activity.getDescription();
        this.period = activity.getPeriod();
        this.scheduledTime = activity.getScheduledTime();
        this.sortOrder = activity.getSortOrder();
        this.active = activity.isActive();
        this.createdByUserId = activity.getCreatedByUserId();
        this.createdAt = activity.getCreatedAt();
        this.updatedAt = activity.getUpdatedAt();
    }

    public UUID getId() { return id; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public RoutinePeriod getPeriod() { return period; }
    public LocalTime getScheduledTime() { return scheduledTime; }
    public int getSortOrder() { return sortOrder; }
    public boolean isActive() { return active; }
    public UUID getCreatedByUserId() { return createdByUserId; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
