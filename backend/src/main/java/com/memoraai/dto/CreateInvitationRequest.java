package com.memoraai.dto;

import com.memoraai.entity.PermissionLevel;
import com.memoraai.entity.RelationshipType;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotNull;

public class CreateInvitationRequest {
    @NotNull(message = "Relationship type is required")
    private RelationshipType relationshipType;
    @NotNull(message = "Permission level is required")
    private PermissionLevel permissionLevel;
    @Email(message = "Intended email must be valid")
    private String intendedEmail;

    public RelationshipType getRelationshipType() { return relationshipType; }
    public void setRelationshipType(RelationshipType relationshipType) { this.relationshipType = relationshipType; }
    public PermissionLevel getPermissionLevel() { return permissionLevel; }
    public void setPermissionLevel(PermissionLevel permissionLevel) { this.permissionLevel = permissionLevel; }
    public String getIntendedEmail() { return intendedEmail; }
    public void setIntendedEmail(String intendedEmail) { this.intendedEmail = intendedEmail; }
}
