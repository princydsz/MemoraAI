package com.memoraai.service;

import com.memoraai.entity.*;
import com.memoraai.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.util.*;

@Service
public class ReminderService {
    private static final Logger log = LoggerFactory.getLogger(ReminderService.class);

    private final RoutineActivityRepository routineActivityRepository;
    private final AppointmentRepository appointmentRepository;
    private final RoutineActivityCompletionRepository completionRepository;
    private final CareCircleMembershipRepository membershipRepository;
    private final ReminderDeliveryRepository reminderDeliveryRepository;
    private final NotificationService notificationService;
    private final UserRepository userRepository;
    private final ZoneId applicationZone;
    private final Clock clock;
    private final long routineWindowSeconds;
    private final long appointmentWindowSeconds;
    private final int maxAttempts;
    private final List<Integer> appointmentIntervalsMinutes;

    public ReminderService(RoutineActivityRepository routineActivityRepository,
                           AppointmentRepository appointmentRepository,
                           RoutineActivityCompletionRepository completionRepository,
                           CareCircleMembershipRepository membershipRepository,
                           ReminderDeliveryRepository reminderDeliveryRepository,
                           NotificationService notificationService,
                           UserRepository userRepository,
                           Clock clock,
                           @Value("${memora.application.zone-id:UTC}") String zoneId,
                           @Value("${memora.reminders.routine-window-seconds:300}") long routineWindowSeconds,
                           @Value("${memora.reminders.appointment-window-seconds:300}") long appointmentWindowSeconds,
                           @Value("${memora.reminders.max-retry-attempts:5}") int maxAttempts,
                           @Value("${memora.reminders.appointment-intervals-minutes:1440,60}") String appointmentIntervals) {
        this.routineActivityRepository = routineActivityRepository;
        this.appointmentRepository = appointmentRepository;
        this.completionRepository = completionRepository;
        this.membershipRepository = membershipRepository;
        this.reminderDeliveryRepository = reminderDeliveryRepository;
        this.notificationService = notificationService;
        this.userRepository = userRepository;
        this.clock = clock;
        this.applicationZone = ZoneId.of(zoneId);
        this.routineWindowSeconds = Math.max(1, routineWindowSeconds);
        this.appointmentWindowSeconds = Math.max(1, appointmentWindowSeconds);
        this.maxAttempts = Math.max(1, maxAttempts);
        this.appointmentIntervalsMinutes = parseIntervals(appointmentIntervals);
    }

    private List<Integer> parseIntervals(String value) {
        List<Integer> result = new ArrayList<>();
        if (value != null) {
            for (String part : value.split(",")) {
                try {
                    int minutes = Integer.parseInt(part.trim());
                    if (minutes >= 0 && !result.contains(minutes)) result.add(minutes);
                } catch (NumberFormatException ignored) {
                    log.warn("Ignoring invalid appointment reminder interval: {}", part);
                }
            }
        }
        if (result.isEmpty()) result.addAll(List.of(1440, 60));
        result.sort(Comparator.reverseOrder());
        return List.copyOf(result);
    }

    private ZoneId patientZone(UUID patientId) {
        return userRepository.findById(patientId)
                .map(User::getTimezone)
                .map(value -> {
                    if (value == null || value.trim().isEmpty()) return applicationZone;
                    try {
                        return ZoneId.of(value.trim());
                    } catch (DateTimeException ex) {
                        log.warn("Invalid timezone {} for patient {}; using application timezone",
                                value, patientId);
                        return applicationZone;
                    }
                })
                .orElse(applicationZone);
    }

    private List<UUID> recipients(UUID patientId) {
        LinkedHashSet<UUID> ids = new LinkedHashSet<>();
        ids.add(patientId);
        membershipRepository.findAllByPatientUserIdAndStatus(patientId, MembershipStatus.ACTIVE)
                .forEach(membership -> ids.add(membership.getFamilyMemberUserId()));
        return List.copyOf(ids);
    }

    private String sourceDedupe(String sourceType, UUID sourceId, UUID recipientId,
                                String reminderKey) {
        return sourceType + ":" + sourceId + ":" + recipientId + ":" + reminderKey;
    }

    @Transactional
    protected void reserveAndDeliver(String sourceType, UUID sourceId, UUID recipientId,
                                     UUID patientId, String reminderKey, Instant scheduledFor,
                                     NotificationType notificationType, String title, String message) {
        String dedupe = sourceDedupe(sourceType, sourceId, recipientId, reminderKey);
        ReminderDelivery delivery = reminderDeliveryRepository
                .findBySourceTypeAndSourceIdAndRecipientUserIdAndReminderKey(
                        sourceType, sourceId, recipientId, reminderKey)
                .orElseGet(() -> reminderDeliveryRepository.save(
                        new ReminderDelivery(sourceType, sourceId, recipientId, patientId,
                                reminderKey, scheduledFor, notificationType, title, message)));

        if (delivery.getStatus() == ReminderDeliveryStatus.DELIVERED) return;

        try {
            Notification notification = notificationService.createNotification(
                    recipientId, patientId, null, notificationType, title, message,
                    sourceId, sourceType, dedupe);
            delivery.recordDelivered(notification.getId());
            reminderDeliveryRepository.save(delivery);
        } catch (RuntimeException ex) {
            delivery.recordAttemptFailure(ex.getMessage());
            reminderDeliveryRepository.save(delivery);
            log.error("Reminder delivery failed for {} {}", sourceType, sourceId, ex);
        }
    }

    @Scheduled(cron = "${memora.reminders.cron:0 * * * * *}")
    public void scheduledRun() {
        generateAt(Instant.now(clock));
    }

    @Transactional
    public void generateAt(Instant now) {
        retryPendingDeliveries();

        for (RoutineActivity activity : routineActivityRepository.findByActiveTrue()) {
            if (activity.getScheduledTime() == null) continue;
            ZoneId zone = patientZone(activity.getPatientUserId());
            LocalDate date = now.atZone(zone).toLocalDate();
            if (completionRepository.findByActivityIdAndCompletionDate(
                    activity.getId(), date).isPresent()) continue;
            Instant scheduled = date.atTime(activity.getScheduledTime()).atZone(zone).toInstant();
            long seconds = Math.abs(Duration.between(scheduled, now).getSeconds());
            if (seconds > routineWindowSeconds) continue;
            String reminderKey = "ROUTINE:" + date;
            String title = "Routine reminder";
            String message = "It's time for \"" + activity.getTitle() + "\".";
            for (UUID recipientId : recipients(activity.getPatientUserId())) {
                reserveAndDeliver("ROUTINE", activity.getId(), recipientId,
                        activity.getPatientUserId(), reminderKey, scheduled,
                        NotificationType.ROUTINE_REMINDER, title, message);
            }
        }

        for (PatientAppointment appointment : appointmentRepository.findByStatus(AppointmentStatus.SCHEDULED)) {
            ZoneId zone = patientZone(appointment.getPatientUserId());
            Instant appointmentInstant = appointment.getAppointmentDate().atTime(appointment.getStartTime())
                    .atZone(zone).toInstant();
            if (!appointmentInstant.isAfter(now)) continue;

            for (int intervalMinutes : appointmentIntervalsMinutes) {
                Instant target = appointmentInstant.minusSeconds(intervalMinutes * 60L);
                long seconds = Math.abs(Duration.between(target, now).getSeconds());
                if (seconds > appointmentWindowSeconds) continue;

                String reminderKey = "APPOINTMENT:" + intervalMinutes + "M";
                String title = "Appointment reminder";
                String message = "Upcoming appointment: \"" + appointment.getTitle()
                        + "\" in " + intervalMinutes + " minute(s).";
                for (UUID recipientId : recipients(appointment.getPatientUserId())) {
                    reserveAndDeliver("APPOINTMENT", appointment.getId(), recipientId,
                            appointment.getPatientUserId(), reminderKey, target,
                            NotificationType.APPOINTMENT_REMINDER, title, message);
                }
            }
        }
    }

    @Transactional
    public void retryPendingDeliveries() {
        List<ReminderDelivery> deliveries = reminderDeliveryRepository
                .findByStatusInAndAttemptCountLessThan(
                        List.of(ReminderDeliveryStatus.PENDING, ReminderDeliveryStatus.FAILED),
                        maxAttempts);
        for (ReminderDelivery delivery : deliveries) {
            if (delivery.getStatus() == ReminderDeliveryStatus.DELIVERED) continue;
            try {
                Notification notification = notificationService.createNotification(
                        delivery.getRecipientUserId(), delivery.getPatientUserId(), null,
                        delivery.getNotificationType(), delivery.getTitle(), delivery.getMessage(),
                        delivery.getSourceId(), delivery.getSourceType(),
                        sourceDedupe(delivery.getSourceType(), delivery.getSourceId(),
                                delivery.getRecipientUserId(), delivery.getReminderKey()));
                delivery.recordDelivered(notification.getId());
            } catch (RuntimeException ex) {
                delivery.recordAttemptFailure(ex.getMessage());
                log.error("Retrying reminder delivery failed for delivery {}", delivery.getId(), ex);
            }
            reminderDeliveryRepository.save(delivery);
        }
    }
}
