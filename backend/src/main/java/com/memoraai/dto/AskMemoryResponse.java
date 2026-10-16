package com.memoraai.dto;

import java.util.List;

public class AskMemoryResponse {

    private String answer;
    private List<SourceMemoryResponse> sources;
    private int retrievedCount;

    public AskMemoryResponse() {
    }

    public AskMemoryResponse(String answer, List<SourceMemoryResponse> sources, int retrievedCount) {
        this.answer = answer;
        this.sources = sources;
        this.retrievedCount = retrievedCount;
    }

    public String getAnswer() {
        return answer;
    }

    public void setAnswer(String answer) {
        this.answer = answer;
    }

    public List<SourceMemoryResponse> getSources() {
        return sources;
    }

    public void setSources(List<SourceMemoryResponse> sources) {
        this.sources = sources;
    }

    public int getRetrievedCount() {
        return retrievedCount;
    }

    public void setRetrievedCount(int retrievedCount) {
        this.retrievedCount = retrievedCount;
    }
}
