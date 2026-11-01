package com.memoraai.entity;

import jakarta.persistence.*;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(
        name = "routine_activity_completions",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_routine_completion_activity_date",
                columnNames = {"activity_id", "completion_date"}
        )
)
public class RoutineActivityCompletion {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "activity_id", nullable = false)
    private UUID activityId;

    @Column(name = "completion_date", nullable = false)
    private LocalDate completionDate;

    @Column(name = "completed_at", nullable = false, updatable = false)
    private Instant completedAt;

    @Column(name = "completed_by_user_id", nullable = false, updatable = false)
    private UUID completedByUserId;

    protected RoutineActivityCompletion() {}

    public RoutineActivityCompletion(UUID activityId, LocalDate completionDate,
                                     UUID completedByUserId) {
        this.activityId = activityId;
        this.completionDate = completionDate;
        this.completedByUserId = completedByUserId;
    }

    @PrePersist
    void onCreate() {
        completedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getActivityId() { return activityId; }
    public LocalDate getCompletionDate() { return completionDate; }
    public Instant getCompletedAt() { return completedAt; }
    public UUID getCompletedByUserId() { return completedByUserId; }
}
