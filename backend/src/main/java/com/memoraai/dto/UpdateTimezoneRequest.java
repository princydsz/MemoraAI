package com.memoraai.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class UpdateTimezoneRequest {
    @NotBlank(message = "Timezone is required")
    @Size(max = 100, message = "Timezone must not exceed 100 characters")
    private String timezone;

    public String getTimezone() {
        return timezone;
    }

    public void setTimezone(String timezone) {
        this.timezone = timezone;
    }
}
