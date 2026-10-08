package com.memoraai.dto;

import java.util.List;

public class AiProcessMemoryResponse {

    private String summary;
    private String suggestedCategory;
    private List<String> suggestedTags;

    public AiProcessMemoryResponse() {
    }

    public AiProcessMemoryResponse(String summary, String suggestedCategory, List<String> suggestedTags) {
        this.summary = summary;
        this.suggestedCategory = suggestedCategory;
        this.suggestedTags = suggestedTags;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public String getSuggestedCategory() {
        return suggestedCategory;
    }

    public void setSuggestedCategory(String suggestedCategory) {
        this.suggestedCategory = suggestedCategory;
    }

    public List<String> getSuggestedTags() {
        return suggestedTags;
    }

    public void setSuggestedTags(List<String> suggestedTags) {
        this.suggestedTags = suggestedTags;
    }
}
