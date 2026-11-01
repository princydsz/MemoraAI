package com.memoraai.dto;

import com.memoraai.entity.RelationshipType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public class CreatePatientRequest {

    @NotBlank(message = "Patient name is required")
    @Size(min = 2, max = 100, message = "Patient name must be between 2 and 100 characters")
    private String name;

    @NotNull(message = "Relationship type is required")
    private RelationshipType relationshipType;

    public CreatePatientRequest() {
    }

    public CreatePatientRequest(String name, RelationshipType relationshipType) {
        this.name = name;
        this.relationshipType = relationshipType;
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
}
