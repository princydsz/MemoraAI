package com.memoraai.entity;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "notifications")
public class Notification {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "recipient_user_id", nullable = false)
    private UUID recipientUserId;

    @Column(name = "patient_user_id")
    private UUID patientUserId;

    @Column(name = "actor_user_id")
    private UUID actorUserId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private NotificationType type;

    @Column(nullable = false, length = 255)
    private String title;

    @Column(nullable = false, length = 1000)
    private String message;

    @Column(name = "related_entity_id")
    private UUID relatedEntityId;

    @Column(name = "related_entity_type", length = 100)
    private String relatedEntityType;

    @Column(name = "dedupe_key", length = 255)
    private String dedupeKey;

    @Column(name = "read_at")
    private Instant readAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Notification() {
    }

    public Notification(UUID recipientUserId, UUID patientUserId, UUID actorUserId,
                        NotificationType type, String title, String message,
                        UUID relatedEntityId, String relatedEntityType, String dedupeKey) {
        this.recipientUserId = recipientUserId;
        this.patientUserId = patientUserId;
        this.actorUserId = actorUserId;
        this.type = type;
        this.title = title;
        this.message = message;
        this.relatedEntityId = relatedEntityId;
        this.relatedEntityType = relatedEntityType;
        this.dedupeKey = dedupeKey;
    }

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    public UUID getId() { return id; }
    public UUID getRecipientUserId() { return recipientUserId; }
    public UUID getPatientUserId() { return patientUserId; }
    public UUID getActorUserId() { return actorUserId; }
    public NotificationType getType() { return type; }
    public String getTitle() { return title; }
    public String getMessage() { return message; }
    public UUID getRelatedEntityId() { return relatedEntityId; }
    public String getRelatedEntityType() { return relatedEntityType; }
    public String getDedupeKey() { return dedupeKey; }
    public Instant getReadAt() { return readAt; }
    public Instant getCreatedAt() { return createdAt; }
}
