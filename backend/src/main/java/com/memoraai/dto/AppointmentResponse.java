package com.memoraai.dto;

import com.memoraai.entity.AppointmentStatus;
import com.memoraai.entity.PatientAppointment;
import com.memoraai.entity.User;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

public class AppointmentResponse {
    private UUID id;
    private UUID patientId;
    private String title;
    private String description;
    private LocalDate appointmentDate;
    private LocalTime startTime;
    private LocalTime endTime;
    private String location;
    private String providerName;
    private AppointmentStatus status;
    private CompletedByResponse createdBy;
    private UUID createdByUserId;
    private Instant createdAt;
    private Instant updatedAt;

    public static AppointmentResponse fromEntity(PatientAppointment appointment, User creator) {
        AppointmentResponse response = new AppointmentResponse();
        response.id = appointment.getId();
        response.patientId = appointment.getPatientUserId();
        response.title = appointment.getTitle();
        response.description = appointment.getDescription();
        response.appointmentDate = appointment.getAppointmentDate();
        response.startTime = appointment.getStartTime();
        response.endTime = appointment.getEndTime();
        response.location = appointment.getLocation();
        response.providerName = appointment.getProviderName();
        response.status = appointment.getStatus();
        response.createdBy = creator == null ? null : new CompletedByResponse(creator);
        response.createdByUserId = appointment.getCreatedByUserId();
        response.createdAt = appointment.getCreatedAt();
        response.updatedAt = appointment.getUpdatedAt();
        return response;
    }

    public UUID getId() { return id; }
    public UUID getPatientId() { return patientId; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public LocalDate getAppointmentDate() { return appointmentDate; }
    public LocalTime getStartTime() { return startTime; }
    public LocalTime getEndTime() { return endTime; }
    public String getLocation() { return location; }
    public String getProviderName() { return providerName; }
    public AppointmentStatus getStatus() { return status; }
    public CompletedByResponse getCreatedBy() { return createdBy; }
    public UUID getCreatedByUserId() { return createdByUserId; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
