package com.memoraai.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.memoraai.entity.AppointmentStatus;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.time.LocalTime;

public class UpdateAppointmentRequest {
    @Size(max = 120, message = "Title must be at most 120 characters")
    private String title;
    private boolean titleSet;

    @Size(max = 1000, message = "Description must be at most 1000 characters")
    private String description;
    private boolean descriptionSet;

    private LocalDate appointmentDate;
    private boolean appointmentDateSet;

    @JsonFormat(pattern = "HH:mm")
    private LocalTime startTime;
    private boolean startTimeSet;

    @JsonFormat(pattern = "HH:mm")
    private LocalTime endTime;
    private boolean endTimeSet;

    @Size(max = 255, message = "Location must be at most 255 characters")
    private String location;
    private boolean locationSet;

    @Size(max = 255, message = "Provider name must be at most 255 characters")
    private String providerName;
    private boolean providerNameSet;

    private AppointmentStatus status;
    private boolean statusSet;

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; titleSet = true; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; descriptionSet = true; }
    public LocalDate getAppointmentDate() { return appointmentDate; }
    public void setAppointmentDate(LocalDate appointmentDate) { this.appointmentDate = appointmentDate; appointmentDateSet = true; }
    public LocalTime getStartTime() { return startTime; }
    public void setStartTime(LocalTime startTime) { this.startTime = startTime; startTimeSet = true; }
    public LocalTime getEndTime() { return endTime; }
    public void setEndTime(LocalTime endTime) { this.endTime = endTime; endTimeSet = true; }
    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; locationSet = true; }
    public String getProviderName() { return providerName; }
    public void setProviderName(String providerName) { this.providerName = providerName; providerNameSet = true; }
    public AppointmentStatus getStatus() { return status; }
    public void setStatus(AppointmentStatus status) { this.status = status; statusSet = true; }

    public boolean isTitleSet() { return titleSet; }
    public boolean isDescriptionSet() { return descriptionSet; }
    public boolean isAppointmentDateSet() { return appointmentDateSet; }
    public boolean isStartTimeSet() { return startTimeSet; }
    public boolean isEndTimeSet() { return endTimeSet; }
    public boolean isLocationSet() { return locationSet; }
    public boolean isProviderNameSet() { return providerNameSet; }
    public boolean isStatusSet() { return statusSet; }
}
