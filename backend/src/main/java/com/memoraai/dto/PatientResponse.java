package com.memoraai.dto;

import com.memoraai.entity.CareCircleMembership;
import com.memoraai.entity.MembershipStatus;
import com.memoraai.entity.PermissionLevel;
import com.memoraai.entity.RelationshipType;
import com.memoraai.entity.User;

import java.util.UUID;

public class PatientResponse {

    private UUID id;
    private String name;
    private RelationshipType relationshipType;
    private PermissionLevel permissionLevel;
    private MembershipStatus status;

    public PatientResponse() {
    }

    public PatientResponse(
            UUID id,
            String name,
            RelationshipType relationshipType,
            PermissionLevel permissionLevel,
            MembershipStatus status) {
        this.id = id;
        this.name = name;
        this.relationshipType = relationshipType;
        this.permissionLevel = permissionLevel;
        this.status = status;
    }

    public static PatientResponse fromEntity(User patient, CareCircleMembership membership) {
        return new PatientResponse(
                patient.getId(),
                patient.getName(),
                membership.getRelationshipType(),
                membership.getPermissionLevel(),
                membership.getStatus()
        );
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public RelationshipType getRelationshipType() {
        return relationshipType;
    }

    public void setRelationshipType(RelationshipType relationshipType) {
        this.relationshipType = relationshipType;
    }

    public PermissionLevel getPermissionLevel() {
        return permissionLevel;
    }

    public void setPermissionLevel(PermissionLevel permissionLevel) {
        this.permissionLevel = permissionLevel;
    }

    public MembershipStatus getStatus() {
        return status;
    }

    public void setStatus(MembershipStatus status) {
        this.status = status;
    }
}
