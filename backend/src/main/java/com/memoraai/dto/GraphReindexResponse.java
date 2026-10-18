package com.memoraai.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public class GraphReindexResponse {

    private int processed;

    @JsonProperty("synchronized")
    private int synchronizedCount;

    private int failed;
    private boolean hasMore;
    private int page;
    private int batchSize;
    private long totalElements;
    private String message;

    public GraphReindexResponse() {
    }

    public GraphReindexResponse(int processed, int synchronizedCount, int failed, boolean hasMore,
                                int page, int batchSize, long totalElements, String message) {
        this.processed = processed;
        this.synchronizedCount = synchronizedCount;
        this.failed = failed;
        this.hasMore = hasMore;
        this.page = page;
        this.batchSize = batchSize;
        this.totalElements = totalElements;
        this.message = message;
    }

    public int getProcessed() {
        return processed;
    }

    public void setProcessed(int processed) {
        this.processed = processed;
    }

    public int getSynchronizedCount() {
        return synchronizedCount;
    }

    public void setSynchronizedCount(int synchronizedCount) {
        this.synchronizedCount = synchronizedCount;
    }

    public int getSynced() {
        return synchronizedCount;
    }

    public int getFailed() {
        return failed;
    }

    public void setFailed(int failed) {
        this.failed = failed;
    }

    public boolean isHasMore() {
        return hasMore;
    }

    public void setHasMore(boolean hasMore) {
        this.hasMore = hasMore;
    }

    public int getPage() {
        return page;
    }

    public void setPage(int page) {
        this.page = page;
    }

    public int getBatchSize() {
        return batchSize;
    }

    public void setBatchSize(int batchSize) {
        this.batchSize = batchSize;
    }

    public long getTotalElements() {
        return totalElements;
    }

    public void setTotalElements(long totalElements) {
        this.totalElements = totalElements;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
