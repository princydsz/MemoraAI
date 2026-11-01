package com.memoraai;

import com.memoraai.entity.*;
import com.memoraai.repository.*;
import com.memoraai.service.ReminderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.*;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class ReminderIntegrationTest {
    @Autowired ReminderService reminderService;
    @Autowired UserRepository userRepository;
    @Autowired CareCircleMembershipRepository membershipRepository;
    @Autowired RoutineActivityRepository routineActivityRepository;
    @Autowired RoutineActivityCompletionRepository completionRepository;
    @Autowired AppointmentRepository appointmentRepository;
    @Autowired ReminderDeliveryRepository reminderDeliveryRepository;
    @Autowired NotificationRepository notificationRepository;
    @Autowired PasswordEncoder passwordEncoder;

    private User patient;
    private User activeMember;
    private User revokedMember;
    private User admin;

    @BeforeEach
    void setUp() {
        reminderDeliveryRepository.deleteAll();
        notificationRepository.deleteAll();
        completionRepository.deleteAll();
        appointmentRepository.deleteAll();
        routineActivityRepository.deleteAll();
        membershipRepository.deleteAll();
        userRepository.deleteAll();

        patient = user("Patient", "reminder-patient@example.com", Role.USER, "Asia/Kolkata");
        activeMember = user("Active", "reminder-active@example.com", Role.USER, null);
        revokedMember = user("Revoked", "reminder-revoked@example.com", Role.USER, null);
        admin = user("Admin", "reminder-admin@example.com", Role.ADMIN, null);

        membership(patient, activeMember, PermissionLevel.CONTRIBUTE, MembershipStatus.ACTIVE);
        membership(patient, revokedMember, PermissionLevel.FULL_ACCESS, MembershipStatus.REVOKED);
    }

    private User user(String name, String email, Role role, String timezone) {
        User result = new User(name, email,
                passwordEncoder.encode("Password123!"), role);
        result.setTimezone(timezone);
        return userRepository.save(result);
    }

    private void membership(User patientUser, User member, PermissionLevel level,
                            MembershipStatus status) {
        membershipRepository.save(new CareCircleMembership(
                patientUser.getId(), member.getId(), RelationshipType.CAREGIVER,
                level, status));
    }

    @Test
    void routineReminderUsesPatientTimezoneAndPreventsDuplicates() {
        RoutineActivity activity = new RoutineActivity(
                patient.getId(), "Morning medication", "Take prescribed dose",
                RoutinePeriod.MORNING, LocalTime.of(9, 0), 1, patient.getId());
        routineActivityRepository.save(activity);

        Instant now = LocalDate.of(2026, 11, 1).atTime(9, 0)
                .atZone(ZoneId.of("Asia/Kolkata")).toInstant();
        reminderService.generateAt(now);
        reminderService.generateAt(now.plusSeconds(30));

        assertThat(reminderDeliveryRepository.count()).isEqualTo(2);
        assertThat(notificationRepository.count()).isEqualTo(2);
        assertThat(reminderDeliveryRepository.findAll())
                .allMatch(delivery -> delivery.getStatus() == ReminderDeliveryStatus.DELIVERED);
        assertThat(reminderDeliveryRepository.findAll())
                .noneMatch(delivery -> delivery.getRecipientUserId().equals(revokedMember.getId()));
    }

    @Test
    void archivedAndCompletedRoutinesDoNotGenerateReminders() {
        RoutineActivity completed = new RoutineActivity(
                patient.getId(), "Completed", null, RoutinePeriod.MORNING,
                LocalTime.of(8, 0), 0, patient.getId());
        completed.setActive(true);
        completed = routineActivityRepository.save(completed);
        completionRepository.save(new RoutineActivityCompletion(
                completed.getId(), LocalDate.of(2026, 11, 1), patient.getId()));

        RoutineActivity archived = new RoutineActivity(
                patient.getId(), "Archived", null, RoutinePeriod.EVENING,
                LocalTime.of(8, 0), 0, patient.getId());
        archived.setActive(false);
        routineActivityRepository.save(archived);

        Instant now = LocalDate.of(2026, 11, 1).atTime(8, 0)
                .atZone(ZoneId.of("Asia/Kolkata")).toInstant();
        reminderService.generateAt(now);

        assertThat(reminderDeliveryRepository.count()).isZero();
        assertThat(notificationRepository.count()).isZero();
    }

    @Test
    void appointmentReminderSupportsIntervalsAndDoesNotNotifyCancelledAppointment() {
        PatientAppointment scheduled = new PatientAppointment(
                patient.getId(), "Cardiology visit", null,
                LocalDate.of(2026, 11, 2), LocalTime.of(9, 30), LocalTime.of(10, 30),
                "Clinic", "Dr Rao", AppointmentStatus.SCHEDULED, patient.getId());
        appointmentRepository.save(scheduled);

        PatientAppointment cancelled = new PatientAppointment(
                patient.getId(), "Cancelled visit", null,
                LocalDate.of(2026, 11, 2), LocalTime.of(9, 30), null,
                null, null, AppointmentStatus.CANCELLED, patient.getId());
        appointmentRepository.save(cancelled);

        Instant oneHourBefore = LocalDate.of(2026, 11, 2).atTime(8, 30)
                .atZone(ZoneId.of("Asia/Kolkata")).toInstant();
        reminderService.generateAt(oneHourBefore);

        assertThat(reminderDeliveryRepository.count()).isEqualTo(2);
        assertThat(notificationRepository.count()).isEqualTo(2);
        assertThat(reminderDeliveryRepository.findAll())
                .allMatch(delivery -> delivery.getReminderKey().equals("APPOINTMENT:60M"));
    }

    @Test
    void appointmentReminderIsIdempotentAcrossRepeatedRuns() {
        PatientAppointment appointment = new PatientAppointment(
                patient.getId(), "Dental visit", null,
                LocalDate.of(2026, 11, 2), LocalTime.of(9, 30), null,
                null, null, AppointmentStatus.SCHEDULED, patient.getId());
        appointment = appointmentRepository.save(appointment);
        UUID appointmentId = appointment.getId();

        Instant trigger = LocalDate.of(2026, 11, 1).atTime(9, 30)
                .atZone(ZoneId.of("Asia/Kolkata")).toInstant();
        reminderService.generateAt(trigger);
        reminderService.generateAt(trigger.plusSeconds(30));

        assertThat(reminderDeliveryRepository.findAll())
                .allMatch(delivery -> delivery.getSourceId().equals(appointmentId));
        assertThat(reminderDeliveryRepository.count()).isEqualTo(2);
        assertThat(notificationRepository.count()).isEqualTo(2);
    }

    @Test
    void failedDeliveryCanRetryWithoutCreatingDuplicates() {
        ReminderDelivery delivery = new ReminderDelivery(
                "ROUTINE", UUID.randomUUID(), patient.getId(), patient.getId(),
                "ROUTINE:2026-11-01", Instant.now(),
                NotificationType.ROUTINE_REMINDER, "Routine reminder", "Take medication");
        delivery.recordAttemptFailure("Transient failure");
        reminderDeliveryRepository.save(delivery);

        reminderService.retryPendingDeliveries();

        assertThat(reminderDeliveryRepository.findAll()).hasSize(1);
        ReminderDelivery retried = reminderDeliveryRepository.findAll().get(0);
        assertThat(retried.getStatus()).isEqualTo(ReminderDeliveryStatus.DELIVERED);
        assertThat(notificationRepository.count()).isEqualTo(1);

        reminderService.retryPendingDeliveries();
        assertThat(notificationRepository.count()).isEqualTo(1);
    }
}
