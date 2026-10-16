package com.memoraai.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class AskMemoryRequest {

    @NotBlank(message = "Question cannot be blank")
    @Size(max = 1000, message = "Question must not exceed 1000 characters")
    private String question;

    public AskMemoryRequest() {
    }

    public AskMemoryRequest(String question) {
        this.question = question;
    }

    public String getQuestion() {
        return question;
    }

    public void setQuestion(String question) {
        this.question = question;
    }
}
