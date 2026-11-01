package com.memoraai.dto;

import java.util.UUID;

public class MemoryContributorResponse {
    private UUID id;
    private String name;
    private String email;

    public MemoryContributorResponse() {
    }

    public MemoryContributorResponse(UUID id, String name, String email) {
        this.id = id;
        this.name = name;
        this.email = email;
    }

    public UUID getId() { return id; }
    public String getName() { return name; }
    public String getEmail() { return email; }
}
