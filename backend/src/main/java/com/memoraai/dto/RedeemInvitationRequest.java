package com.memoraai.dto;

import jakarta.validation.constraints.NotBlank;

public class RedeemInvitationRequest {
    @NotBlank(message = "Invitation token is required")
    private String token;

    public String getToken() { return token; }
    public void setToken(String token) { this.token = token; }
}
