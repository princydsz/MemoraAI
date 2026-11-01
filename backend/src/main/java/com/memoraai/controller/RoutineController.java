package com.memoraai.controller;

import com.memoraai.dto.*;
import com.memoraai.service.RoutineService;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.UUID;

@RestController
@RequestMapping("/api/family/patients/{patientId}/routines")
public class RoutineController {
    private final RoutineService routineService;

    public RoutineController(RoutineService routineService) {
        this.routineService = routineService;
    }

    @PostMapping({"", "/activities"})
    public ResponseEntity<RoutineActivityResponse> create(
            @PathVariable UUID patientId,
            @Valid @RequestBody CreateRoutineActivityRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.status(HttpStatus.CREATED).body(
                routineService.create(patientId, request, userDetails.getUsername()));
    }

    @GetMapping
    public ResponseEntity<DailyRoutineResponse> getRoutine(
            @PathVariable UUID patientId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(routineService.getRoutine(
                patientId, date, userDetails.getUsername()));
    }

    @GetMapping("/activities/{activityId}")
    public ResponseEntity<RoutineActivityResponse> getActivity(
            @PathVariable UUID patientId,
            @PathVariable UUID activityId,
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(routineService.getActivity(
                patientId, activityId, userDetails.getUsername()));
    }

    @PatchMapping("/activities/{activityId}")
    public ResponseEntity<RoutineActivityResponse> update(
            @PathVariable UUID patientId,
            @PathVariable UUID activityId,
            @Valid @RequestBody UpdateRoutineActivityRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(routineService.update(
                patientId, activityId, request, userDetails.getUsername()));
    }

    @DeleteMapping("/activities/{activityId}")
    public ResponseEntity<RoutineActivityResponse> archive(
            @PathVariable UUID patientId,
            @PathVariable UUID activityId,
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(routineService.archive(
                patientId, activityId, userDetails.getUsername()));
    }

    @PostMapping("/activities/{activityId}/reactivate")
    public ResponseEntity<RoutineActivityResponse> reactivate(
            @PathVariable UUID patientId,
            @PathVariable UUID activityId,
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(routineService.reactivate(
                patientId, activityId, userDetails.getUsername()));
    }

    @PostMapping("/activities/{activityId}/complete")
    public ResponseEntity<CompletionResponse> complete(
            @PathVariable UUID patientId,
            @PathVariable UUID activityId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(routineService.complete(
                patientId, activityId, date, userDetails.getUsername()));
    }

    @DeleteMapping("/activities/{activityId}/complete")
    public ResponseEntity<Void> uncomplete(
            @PathVariable UUID patientId,
            @PathVariable UUID activityId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @AuthenticationPrincipal UserDetails userDetails) {
        routineService.uncomplete(patientId, activityId, date, userDetails.getUsername());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/activities/{activityId}/history")
    public ResponseEntity<CompletionHistoryResponse> history(
            @PathVariable UUID patientId,
            @PathVariable UUID activityId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(routineService.history(
                patientId, activityId, from, to, page, size, userDetails.getUsername()));
    }
}
