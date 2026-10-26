package com.memoraai.dto;

import java.util.List;

public class AskMemoryResponse {

    private String answer;
    private List<SourceMemoryResponse> sources;
    private int retrievedCount;
    private String provider;
    private String model;

    public AskMemoryResponse() {
    }

    public AskMemoryResponse(String answer, List<SourceMemoryResponse> sources, int retrievedCount) {
        this(answer, sources, retrievedCount, null, null);
    }

    public AskMemoryResponse(String answer, List<SourceMemoryResponse> sources, int retrievedCount,
                             String provider, String model) {
        this.answer = answer;
        this.sources = sources;
        this.retrievedCount = retrievedCount;
        this.provider = provider;
        this.model = model;
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

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }
}
