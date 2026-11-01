package com.memoraai.entity;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
        name = "reminder_deliveries",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_reminder_delivery",
                columnNames = {"source_type", "source_id", "recipient_user_id", "reminder_key"}
        )
)
public class ReminderDelivery {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "source_type", nullable = false, length = 30)
    private String sourceType;

    @Column(name = "source_id", nullable = false)
    private UUID sourceId;

    @Column(name = "recipient_user_id", nullable = false)
    private UUID recipientUserId;

    @Column(name = "patient_user_id", nullable = false)
    private UUID patientUserId;

    @Column(name = "reminder_key", nullable = false, length = 255)
    private String reminderKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ReminderDeliveryStatus status = ReminderDeliveryStatus.PENDING;

    @Column(name = "scheduled_for", nullable = false)
    private Instant scheduledFor;

    @Enumerated(EnumType.STRING)
    @Column(name = "notification_type", nullable = false, length = 50)
    private NotificationType notificationType;

    @Column(nullable = false, length = 255)
    private String title;

    @Column(nullable = false, length = 1000)
    private String message;

    @Column(name = "notification_id")
    private UUID notificationId;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    @Column(name = "last_error", length = 1000)
    private String lastError;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "delivered_at")
    private Instant deliveredAt;

    protected ReminderDelivery() {}

    public ReminderDelivery(String sourceType, UUID sourceId, UUID recipientUserId,
                            UUID patientUserId, String reminderKey, Instant scheduledFor,
                            NotificationType notificationType, String title, String message) {
        this.sourceType = sourceType;
        this.sourceId = sourceId;
        this.recipientUserId = recipientUserId;
        this.patientUserId = patientUserId;
        this.reminderKey = reminderKey;
        this.scheduledFor = scheduledFor;
        this.notificationType = notificationType;
        this.title = title;
        this.message = message;
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public String getSourceType() { return sourceType; }
    public UUID getSourceId() { return sourceId; }
    public UUID getRecipientUserId() { return recipientUserId; }
    public UUID getPatientUserId() { return patientUserId; }
    public String getReminderKey() { return reminderKey; }
    public ReminderDeliveryStatus getStatus() { return status; }
    public void markPending() { status = ReminderDeliveryStatus.PENDING; lastError = null; }
    public Instant getScheduledFor() { return scheduledFor; }
    public NotificationType getNotificationType() { return notificationType; }
    public String getTitle() { return title; }
    public String getMessage() { return message; }
    public UUID getNotificationId() { return notificationId; }
    public int getAttemptCount() { return attemptCount; }
    public String getLastError() { return lastError; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Instant getDeliveredAt() { return deliveredAt; }

    public void recordAttemptFailure(String error) {
        status = ReminderDeliveryStatus.FAILED;
        attemptCount++;
        lastError = error == null ? "Unknown delivery failure" : error.substring(0, Math.min(error.length(), 1000));
    }

    public void recordDelivered(UUID notificationId) {
        status = ReminderDeliveryStatus.DELIVERED;
        attemptCount++;
        lastError = null;
        this.notificationId = notificationId;
        deliveredAt = Instant.now();
    }
}
