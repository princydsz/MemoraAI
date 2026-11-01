package com.memoraai.dto;

import com.memoraai.entity.Notification;
import com.memoraai.entity.NotificationType;

import java.time.Instant;
import java.util.UUID;

public class NotificationResponse {
    private UUID id;
    private NotificationType type;
    private String title;
    private String message;
    private UUID patientId;
    private UUID actorId;
    private UUID relatedEntityId;
    private String relatedEntityType;
    private Instant readAt;
    private Instant createdAt;

    public static NotificationResponse fromEntity(Notification notification) {
        NotificationResponse response = new NotificationResponse();
        response.id = notification.getId();
        response.type = notification.getType();
        response.title = notification.getTitle();
        response.message = notification.getMessage();
        response.patientId = notification.getPatientUserId();
        response.actorId = notification.getActorUserId();
        response.relatedEntityId = notification.getRelatedEntityId();
        response.relatedEntityType = notification.getRelatedEntityType();
        response.readAt = notification.getReadAt();
        response.createdAt = notification.getCreatedAt();
        return response;
    }

    public UUID getId() { return id; }
    public NotificationType getType() { return type; }
    public String getTitle() { return title; }
    public String getMessage() { return message; }
    public UUID getPatientId() { return patientId; }
    public UUID getActorId() { return actorId; }
    public UUID getRelatedEntityId() { return relatedEntityId; }
    public String getRelatedEntityType() { return relatedEntityType; }
    public Instant getReadAt() { return readAt; }
    public Instant getCreatedAt() { return createdAt; }
}
