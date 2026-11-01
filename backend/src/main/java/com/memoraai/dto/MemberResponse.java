package com.memoraai.dto;

import com.memoraai.entity.*;

import java.time.Instant;
import java.util.UUID;

public class MemberResponse {
    private UUID memberId;
    private String name;
    private RelationshipType relationshipType;
    private PermissionLevel permissionLevel;
    private MembershipStatus status;
    private Instant createdAt;
    private Instant updatedAt;

    public static MemberResponse fromEntity(User user, CareCircleMembership membership) {
        MemberResponse response = new MemberResponse();
        response.memberId = user.getId();
        response.name = user.getName();
        response.relationshipType = membership.getRelationshipType();
        response.permissionLevel = membership.getPermissionLevel();
        response.status = membership.getStatus();
        response.createdAt = membership.getCreatedAt();
        response.updatedAt = membership.getUpdatedAt();
        return response;
    }

    public UUID getMemberId() { return memberId; }
    public String getName() { return name; }
    public RelationshipType getRelationshipType() { return relationshipType; }
    public PermissionLevel getPermissionLevel() { return permissionLevel; }
    public MembershipStatus getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
