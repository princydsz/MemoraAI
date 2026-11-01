package com.memoraai.dto;

import java.util.List;

public class MemoryPageResponse {
    private List<MemoryResponse> content;
    private int pageNumber;
    private int pageSize;
    private long totalElements;
    private int totalPages;

    public MemoryPageResponse(List<MemoryResponse> content, int pageNumber, int pageSize,
                              long totalElements, int totalPages) {
        this.content = content;
        this.pageNumber = pageNumber;
        this.pageSize = pageSize;
        this.totalElements = totalElements;
        this.totalPages = totalPages;
    }

    public List<MemoryResponse> getContent() { return content; }
    public int getPageNumber() { return pageNumber; }
    public int getPageSize() { return pageSize; }
    public long getTotalElements() { return totalElements; }
    public int getTotalPages() { return totalPages; }
}
