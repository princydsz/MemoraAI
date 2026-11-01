package com.memoraai.dto;

import com.memoraai.entity.User;

import java.util.UUID;

public class CompletedByResponse {
    private UUID id;
    private String name;

    public CompletedByResponse(User user) {
        this.id = user.getId();
        this.name = user.getName();
    }

    public UUID getId() { return id; }
    public String getName() { return name; }
}
