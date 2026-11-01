package com.memoraai.controller;

import com.memoraai.dto.*;
import com.memoraai.entity.AppointmentStatus;
import com.memoraai.service.AppointmentService;
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
@RequestMapping("/api/family/patients/{patientId}/appointments")
public class AppointmentController {
    private final AppointmentService appointmentService;

    public AppointmentController(AppointmentService appointmentService) {
        this.appointmentService = appointmentService;
    }

    @PostMapping({"", "/"})
    public ResponseEntity<AppointmentResponse> create(
            @PathVariable UUID patientId,
            @Valid @RequestBody CreateAppointmentRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.status(HttpStatus.CREATED).body(
                appointmentService.create(patientId, request, userDetails.getUsername()));
    }

    @GetMapping({"", "/", "/history"})
    public ResponseEntity<AppointmentPageResponse> list(
            @PathVariable UUID patientId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) AppointmentStatus status,
            @RequestParam(required = false) Boolean upcoming,
            @RequestParam(required = false) Boolean past,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(appointmentService.list(
                patientId, from, to, status, upcoming, past, page, size, userDetails.getUsername()));
    }

    @GetMapping("/upcoming")
    public ResponseEntity<AppointmentPageResponse> upcoming(
            @PathVariable UUID patientId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) AppointmentStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(appointmentService.list(
                patientId, from, to, status, true, false, page, size, userDetails.getUsername()));
    }

    @GetMapping("/past")
    public ResponseEntity<AppointmentPageResponse> past(
            @PathVariable UUID patientId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) AppointmentStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(appointmentService.list(
                patientId, from, to, status, false, true, page, size, userDetails.getUsername()));
    }

    @GetMapping("/{appointmentId}")
    public ResponseEntity<AppointmentResponse> get(
            @PathVariable UUID patientId,
            @PathVariable UUID appointmentId,
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(appointmentService.get(
                patientId, appointmentId, userDetails.getUsername()));
    }

    @PatchMapping("/{appointmentId}")
    public ResponseEntity<AppointmentResponse> update(
            @PathVariable UUID patientId,
            @PathVariable UUID appointmentId,
            @Valid @RequestBody UpdateAppointmentRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(appointmentService.update(
                patientId, appointmentId, request, userDetails.getUsername()));
    }

    @PostMapping("/{appointmentId}/cancel")
    public ResponseEntity<AppointmentResponse> cancelPost(
            @PathVariable UUID patientId,
            @PathVariable UUID appointmentId,
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(appointmentService.cancel(
                patientId, appointmentId, userDetails.getUsername()));
    }

    @PatchMapping("/{appointmentId}/cancel")
    public ResponseEntity<AppointmentResponse> cancelPatch(
            @PathVariable UUID patientId,
            @PathVariable UUID appointmentId,
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(appointmentService.cancel(
                patientId, appointmentId, userDetails.getUsername()));
    }

    @DeleteMapping("/{appointmentId}")
    public ResponseEntity<AppointmentResponse> cancelDelete(
            @PathVariable UUID patientId,
            @PathVariable UUID appointmentId,
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(appointmentService.cancel(
                patientId, appointmentId, userDetails.getUsername()));
    }
}
