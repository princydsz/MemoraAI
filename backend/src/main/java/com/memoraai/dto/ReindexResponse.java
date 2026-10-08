package com.memoraai.dto;

public class ReindexResponse {
    private String message;
    private int reindexedCount;

    public ReindexResponse() {
    }

    public ReindexResponse(String message, int reindexedCount) {
        this.message = message;
        this.reindexedCount = reindexedCount;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public int getReindexedCount() {
        return reindexedCount;
    }

    public void setReindexedCount(int reindexedCount) {
        this.reindexedCount = reindexedCount;
    }
}
