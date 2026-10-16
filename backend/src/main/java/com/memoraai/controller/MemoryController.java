package com.memoraai.controller;



import com.memoraai.dto.AiProcessMemoryResponse;
import com.memoraai.dto.CreateMemoryRequest;
import com.memoraai.dto.MemoryResponse;
import com.memoraai.dto.UpdateMemoryRequest;
import com.memoraai.service.MemoryService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

import com.memoraai.dto.AskMemoryRequest;
import com.memoraai.dto.AskMemoryResponse;
import com.memoraai.dto.MemorySearchResponse;
import com.memoraai.dto.ReindexResponse;


@RestController
@RequestMapping("/api/memories")
public class MemoryController {

    private final MemoryService memoryService;

    public MemoryController(MemoryService memoryService) {
        this.memoryService = memoryService;
    }

    @PostMapping
    public ResponseEntity<MemoryResponse> createMemory(
            @Valid @RequestBody CreateMemoryRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {
        MemoryResponse response = memoryService.createMemory(request, userDetails.getUsername());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    public ResponseEntity<List<MemoryResponse>> getUserMemories(
            @AuthenticationPrincipal UserDetails userDetails) {
        List<MemoryResponse> responses = memoryService.getUserMemories(userDetails.getUsername());
        return ResponseEntity.ok(responses);
    }

    @GetMapping("/search")
    public ResponseEntity<List<MemorySearchResponse>> searchMemories(
            @RequestParam(name = "q", required = false, defaultValue = "") String query,
            @AuthenticationPrincipal UserDetails userDetails) {
        List<MemorySearchResponse> searchResults = memoryService.searchMemories(query, userDetails.getUsername());
        return ResponseEntity.ok(searchResults);
    }

    @PostMapping("/reindex")
    public ResponseEntity<ReindexResponse> reindexMemories(
            @AuthenticationPrincipal UserDetails userDetails) {
        ReindexResponse response = memoryService.reindexUserMemories(userDetails.getUsername());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/ask")
    public ResponseEntity<AskMemoryResponse> askQuestion(
            @Valid @RequestBody AskMemoryRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {
        AskMemoryResponse response = memoryService.askQuestion(request, userDetails.getUsername());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id:[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}}")
    public ResponseEntity<MemoryResponse> getMemory(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserDetails userDetails) {
        MemoryResponse response = memoryService.getMemory(id, userDetails.getUsername());
        return ResponseEntity.ok(response);
    }

    @PutMapping("/{id:[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}}")
    public ResponseEntity<MemoryResponse> updateMemory(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateMemoryRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {
        MemoryResponse response = memoryService.updateMemory(id, request, userDetails.getUsername());
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{id:[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}}")
    public ResponseEntity<Void> deleteMemory(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserDetails userDetails) {
        memoryService.deleteMemory(id, userDetails.getUsername());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id:[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}}/process")
    public ResponseEntity<AiProcessMemoryResponse> processMemory(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserDetails userDetails) {
        AiProcessMemoryResponse response = memoryService.processMemory(id, userDetails.getUsername());
        return ResponseEntity.ok(response);
    }
}



