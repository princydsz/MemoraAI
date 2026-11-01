package com.memoraai.dto;

import com.memoraai.entity.PermissionLevel;
import jakarta.validation.constraints.NotNull;

public class UpdateMemberRequest {
    @NotNull(message = "Permission level is required")
    private PermissionLevel permissionLevel;

    public PermissionLevel getPermissionLevel() { return permissionLevel; }
    public void setPermissionLevel(PermissionLevel permissionLevel) { this.permissionLevel = permissionLevel; }
}
