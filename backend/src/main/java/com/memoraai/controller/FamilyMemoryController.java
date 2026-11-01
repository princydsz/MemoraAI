package com.memoraai.controller;

import com.memoraai.dto.CreateMemoryRequest;
import com.memoraai.dto.MemoryPageResponse;
import com.memoraai.dto.MemoryResponse;
import com.memoraai.dto.UpdateMemoryRequest;
import com.memoraai.service.MemoryService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/family/patients/{patientId}/memories")
public class FamilyMemoryController {
    private final MemoryService memoryService;

    public FamilyMemoryController(MemoryService memoryService) {
        this.memoryService = memoryService;
    }

    @PostMapping({"", "/"})
    public ResponseEntity<MemoryResponse> create(
            @PathVariable UUID patientId,
            @Valid @RequestBody CreateMemoryRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.status(HttpStatus.CREATED).body(
                memoryService.createForPatient(patientId, request, userDetails.getUsername()));
    }

    @GetMapping({"", "/"})
    public ResponseEntity<MemoryPageResponse> list(
            @PathVariable UUID patientId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(
                memoryService.listForPatient(patientId, page, size, userDetails.getUsername()));
    }

    @GetMapping("/{memoryId}")
    public ResponseEntity<MemoryResponse> get(
            @PathVariable UUID patientId,
            @PathVariable UUID memoryId,
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(
                memoryService.getForPatient(patientId, memoryId, userDetails.getUsername()));
    }

    @PutMapping("/{memoryId}")
    public ResponseEntity<MemoryResponse> update(
            @PathVariable UUID patientId,
            @PathVariable UUID memoryId,
            @Valid @RequestBody UpdateMemoryRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(
                memoryService.updateForPatient(patientId, memoryId, request, userDetails.getUsername()));
    }

    @PatchMapping("/{memoryId}")
    public ResponseEntity<MemoryResponse> patch(
            @PathVariable UUID patientId,
            @PathVariable UUID memoryId,
            @Valid @RequestBody UpdateMemoryRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(
                memoryService.updateForPatient(patientId, memoryId, request, userDetails.getUsername()));
    }

    @DeleteMapping("/{memoryId}")
    public ResponseEntity<Void> delete(
            @PathVariable UUID patientId,
            @PathVariable UUID memoryId,
            @AuthenticationPrincipal UserDetails userDetails) {
        memoryService.deleteForPatient(patientId, memoryId, userDetails.getUsername());
        return ResponseEntity.noContent().build();
    }
}
