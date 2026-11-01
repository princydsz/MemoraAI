package com.memoraai.service;

import com.memoraai.dto.*;
import com.memoraai.entity.*;
import com.memoraai.exception.ResourceNotFoundException;
import com.memoraai.repository.AppointmentRepository;
import com.memoraai.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.*;

@Service
public class AppointmentService {
    private final AppointmentRepository appointmentRepository;
    private final UserRepository userRepository;
    private final CareCircleAuthorizationService authorizationService;
    private final ZoneId applicationZone;

    public AppointmentService(AppointmentRepository appointmentRepository,
                              UserRepository userRepository,
                              CareCircleAuthorizationService authorizationService,
                              @Value("${memora.application.zone-id:UTC}") String zoneId) {
        this.appointmentRepository = appointmentRepository;
        this.userRepository = userRepository;
        this.authorizationService = authorizationService;
        this.applicationZone = ZoneId.of(zoneId);
    }

    private User authenticatedUser(String email) {
        return userRepository.findByEmail(email.toLowerCase().trim())
                .orElseThrow(() -> new UsernameNotFoundException("User not found with email: " + email));
    }

    private User requirePatient(UUID patientId, User actor, CareCircleCapability capability) {
        authorizationService.requirePatientAccess(actor.getId(), patientId);
        if (!actor.getId().equals(patientId)) {
            authorizationService.requireCapability(actor.getId(), patientId, capability);
        }
        return userRepository.findById(patientId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        CareCircleAuthorizationService.PATIENT_ACCESS_NOT_FOUND));
    }

    private PatientAppointment requireAppointment(UUID patientId, UUID appointmentId) {
        return appointmentRepository.findByIdAndPatientUserId(appointmentId, patientId)
                .orElseThrow(() -> new ResourceNotFoundException("Appointment not found"));
    }

    private void validateTime(LocalTime startTime, LocalTime endTime) {
        if (startTime == null) {
            throw new IllegalArgumentException("Start time is required");
        }
        if (endTime != null && !endTime.isAfter(startTime)) {
            throw new IllegalArgumentException("End time must be after start time");
        }
    }

    private void validateText(String title) {
        if (title == null || title.trim().isBlank()) {
            throw new IllegalArgumentException("Title must not be blank");
        }
        if (title.trim().length() > 120) {
            throw new IllegalArgumentException("Title must be at most 120 characters");
        }
    }

    private String normalizeOptional(String value) {
        return value == null || value.trim().isEmpty() ? null : value.trim();
    }

    private AppointmentResponse withCreator(PatientAppointment appointment, Map<UUID, User> creators) {
        return AppointmentResponse.fromEntity(
                appointment, creators.get(appointment.getCreatedByUserId()));
    }

    @Transactional
    public AppointmentResponse create(UUID patientId, CreateAppointmentRequest request,
                                      String actorEmail) {
        User actor = authenticatedUser(actorEmail);
        requirePatient(patientId, actor, CareCircleCapability.MANAGE_APPOINTMENTS);
        validateText(request.getTitle());
        if (request.getAppointmentDate() == null) {
            throw new IllegalArgumentException("Appointment date is required");
        }
        validateTime(request.getStartTime(), request.getEndTime());

        PatientAppointment appointment = new PatientAppointment(
                patientId,
                request.getTitle().trim(),
                normalizeOptional(request.getDescription()),
                request.getAppointmentDate(),
                request.getStartTime(),
                request.getEndTime(),
                normalizeOptional(request.getLocation()),
                normalizeOptional(request.getProviderName()),
                request.getStatus(),
                actor.getId());
        PatientAppointment saved = appointmentRepository.save(appointment);
        return AppointmentResponse.fromEntity(saved, actor);
    }

    @Transactional(readOnly = true)
    public AppointmentResponse get(UUID patientId, UUID appointmentId, String actorEmail) {
        User actor = authenticatedUser(actorEmail);
        requirePatient(patientId, actor, CareCircleCapability.VIEW_APPOINTMENTS);
        PatientAppointment appointment = requireAppointment(patientId, appointmentId);
        User creator = userRepository.findById(appointment.getCreatedByUserId()).orElse(null);
        return AppointmentResponse.fromEntity(appointment, creator);
    }

    @Transactional(readOnly = true)
    public AppointmentPageResponse list(UUID patientId, LocalDate from, LocalDate to,
                                        AppointmentStatus status, Boolean upcoming, Boolean past,
                                        int page, int size, String actorEmail) {
        User actor = authenticatedUser(actorEmail);
        requirePatient(patientId, actor, CareCircleCapability.VIEW_APPOINTMENTS);
        if (page < 0) throw new IllegalArgumentException("Page number must not be negative");
        if (size < 1 || size > 100) throw new IllegalArgumentException("Page size must be between 1 and 100");
        if (from != null && to != null && from.isAfter(to)) {
            throw new IllegalArgumentException("From date must not be after to date");
        }
        boolean upcomingFilter = Boolean.TRUE.equals(upcoming);
        boolean pastFilter = Boolean.TRUE.equals(past);
        if (upcomingFilter && pastFilter) {
            throw new IllegalArgumentException("Upcoming and past filters cannot be combined");
        }
        LocalDate today = LocalDate.now(applicationZone);
        PageRequest pageable = PageRequest.of(page, size,
                Sort.by(
                        new Sort.Order(Sort.Direction.ASC, "appointmentDate"),
                        new Sort.Order(Sort.Direction.ASC, "startTime")));
        Page<PatientAppointment> result = appointmentRepository.findFiltered(
                patientId, from, to, status, upcomingFilter, pastFilter, today, pageable);

        Set<UUID> creatorIds = new HashSet<>();
        result.getContent().forEach(a -> creatorIds.add(a.getCreatedByUserId()));
        Map<UUID, User> creators = new HashMap<>();
        if (!creatorIds.isEmpty()) {
            userRepository.findAllById(creatorIds).forEach(user -> creators.put(user.getId(), user));
        }
        List<AppointmentResponse> content = result.getContent().stream()
                .map(a -> withCreator(a, creators)).toList();
        return new AppointmentPageResponse(content, result.getNumber(), result.getSize(),
                result.getTotalElements(), result.getTotalPages());
    }

    @Transactional
    public AppointmentResponse update(UUID patientId, UUID appointmentId,
                                      UpdateAppointmentRequest request, String actorEmail) {
        User actor = authenticatedUser(actorEmail);
        requirePatient(patientId, actor, CareCircleCapability.MANAGE_APPOINTMENTS);
        PatientAppointment appointment = requireAppointment(patientId, appointmentId);

        if (request.isTitleSet()) {
            validateText(request.getTitle());
            appointment.setTitle(request.getTitle().trim());
        }
        if (request.isDescriptionSet()) appointment.setDescription(normalizeOptional(request.getDescription()));
        if (request.isAppointmentDateSet()) {
            if (request.getAppointmentDate() == null) {
                throw new IllegalArgumentException("Appointment date must not be null");
            }
            appointment.setAppointmentDate(request.getAppointmentDate());
        }
        if (request.isStartTimeSet()) {
            if (request.getStartTime() == null) {
                throw new IllegalArgumentException("Start time must not be null");
            }
            appointment.setStartTime(request.getStartTime());
        }
        if (request.isEndTimeSet()) appointment.setEndTime(request.getEndTime());
        if (request.isLocationSet()) appointment.setLocation(normalizeOptional(request.getLocation()));
        if (request.isProviderNameSet()) appointment.setProviderName(normalizeOptional(request.getProviderName()));

        validateTime(appointment.getStartTime(), appointment.getEndTime());
        if (request.isStatusSet()) {
            validateStatusTransition(appointment.getStatus(), request.getStatus());
            appointment.setStatus(request.getStatus());
        }
        return AppointmentResponse.fromEntity(
                appointmentRepository.save(appointment),
                userRepository.findById(actor.getId()).orElse(actor));
    }

    private void validateStatusTransition(AppointmentStatus current, AppointmentStatus requested) {
        if (requested == null) {
            throw new IllegalArgumentException("Appointment status must not be null");
        }
        if (current == requested) return;
        if (current == AppointmentStatus.COMPLETED || current == AppointmentStatus.CANCELLED) {
            throw new IllegalArgumentException("Terminal appointment status cannot be changed");
        }
        if (current == AppointmentStatus.SCHEDULED
                && (requested == AppointmentStatus.COMPLETED || requested == AppointmentStatus.CANCELLED)) {
            return;
        }
        throw new IllegalArgumentException("Invalid appointment status transition");
    }

    @Transactional
    public AppointmentResponse cancel(UUID patientId, UUID appointmentId, String actorEmail) {
        User actor = authenticatedUser(actorEmail);
        requirePatient(patientId, actor, CareCircleCapability.MANAGE_APPOINTMENTS);
        PatientAppointment appointment = requireAppointment(patientId, appointmentId);
        if (appointment.getStatus() == AppointmentStatus.CANCELLED) {
            User creator = userRepository.findById(appointment.getCreatedByUserId()).orElse(null);
            return AppointmentResponse.fromEntity(appointment, creator);
        }
        validateStatusTransition(appointment.getStatus(), AppointmentStatus.CANCELLED);
        appointment.setStatus(AppointmentStatus.CANCELLED);
        User creator = userRepository.findById(appointment.getCreatedByUserId()).orElse(null);
        return AppointmentResponse.fromEntity(appointmentRepository.save(appointment), creator);
    }
}
