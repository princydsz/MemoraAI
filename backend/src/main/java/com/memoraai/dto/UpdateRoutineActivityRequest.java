package com.memoraai.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.memoraai.entity.RoutinePeriod;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

import java.time.LocalTime;

public class UpdateRoutineActivityRequest {
    @Size(max = 120, message = "Title must be at most 120 characters")
    private String title;

    @Size(max = 1000, message = "Description must be at most 1000 characters")
    private String description;

    private RoutinePeriod period;

    @JsonFormat(pattern = "HH:mm")
    private LocalTime scheduledTime;

    @Min(value = 0, message = "Sort order must be zero or greater")
    @Max(value = Integer.MAX_VALUE, message = "Sort order is too large")
    private Integer sortOrder;

    private Boolean active;
    private boolean scheduledTimeSet;
    private boolean descriptionSet;

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getDescription() { return description; }
    public void setDescription(String description) {
        this.description = description;
        this.descriptionSet = true;
    }
    public RoutinePeriod getPeriod() { return period; }
    public void setPeriod(RoutinePeriod period) { this.period = period; }
    public LocalTime getScheduledTime() { return scheduledTime; }
    public void setScheduledTime(LocalTime scheduledTime) {
        this.scheduledTime = scheduledTime;
        this.scheduledTimeSet = true;
    }
    public Integer getSortOrder() { return sortOrder; }
    public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }
    public Boolean getActive() { return active; }
    public void setActive(Boolean active) { this.active = active; }
    public boolean isScheduledTimeSet() { return scheduledTimeSet; }
    public boolean isDescriptionSet() { return descriptionSet; }
}
