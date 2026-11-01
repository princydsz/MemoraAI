package com.memoraai.dto;

import com.memoraai.entity.*;

import java.time.Instant;
import java.util.UUID;

public class InvitationResponse {
    private UUID id;
    private UUID patientId;
    private UUID inviterId;
    private String intendedEmail;
    private PermissionLevel permissionLevel;
    private RelationshipType relationshipType;
    private InvitationStatus status;
    private Instant createdAt;
    private Instant expiresAt;
    private Instant redeemedAt;
    private Instant revokedAt;
    private String token;

    public static InvitationResponse fromEntity(CareCircleInvitation invitation, Instant now, String token) {
        InvitationResponse response = new InvitationResponse();
        response.id = invitation.getId();
        response.patientId = invitation.getPatientUserId();
        response.inviterId = invitation.getInviterUserId();
        response.intendedEmail = invitation.getIntendedEmail();
        response.permissionLevel = invitation.getPermissionLevel();
        response.relationshipType = invitation.getRelationshipType();
        response.status = invitation.effectiveStatus(now);
        response.createdAt = invitation.getCreatedAt();
        response.expiresAt = invitation.getExpiresAt();
        response.redeemedAt = invitation.getRedeemedAt();
        response.revokedAt = invitation.getRevokedAt();
        response.token = token;
        return response;
    }

    public UUID getId() { return id; }
    public UUID getPatientId() { return patientId; }
    public UUID getInviterId() { return inviterId; }
    public String getIntendedEmail() { return intendedEmail; }
    public PermissionLevel getPermissionLevel() { return permissionLevel; }
    public RelationshipType getRelationshipType() { return relationshipType; }
    public InvitationStatus getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getExpiresAt() { return expiresAt; }
    public Instant getRedeemedAt() { return redeemedAt; }
    public Instant getRevokedAt() { return revokedAt; }
    public String getToken() { return token; }
}
