package com.memoraai.service;

import com.memoraai.dto.*;
import com.memoraai.entity.*;
import com.memoraai.exception.ResourceNotFoundException;
import com.memoraai.service.CareCircleAuthorizationService;
import com.memoraai.repository.RoutineActivityCompletionRepository;
import com.memoraai.repository.RoutineActivityRepository;
import com.memoraai.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class RoutineService {
    private final RoutineActivityRepository activityRepository;
    private final RoutineActivityCompletionRepository completionRepository;
    private final UserRepository userRepository;
    private final CareCircleAuthorizationService authorizationService;
    private final ZoneId applicationZone;

    public RoutineService(RoutineActivityRepository activityRepository,
                          RoutineActivityCompletionRepository completionRepository,
                          UserRepository userRepository,
                          CareCircleAuthorizationService authorizationService,
                          @Value("${memora.application.zone-id:UTC}") String zoneId) {
        this.activityRepository = activityRepository;
        this.completionRepository = completionRepository;
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

    private RoutineActivity requireActivity(UUID patientId, UUID activityId) {
        return activityRepository.findByIdAndPatientUserId(activityId, patientId)
                .orElseThrow(() -> new ResourceNotFoundException("Routine activity not found"));
    }

    private RoutineActivity requireActivityForUpdate(UUID patientId, UUID activityId) {
        return activityRepository.findByIdAndPatientUserIdForUpdate(activityId, patientId)
                .orElseThrow(() -> new ResourceNotFoundException("Routine activity not found"));
    }

    private LocalDate defaultCompletionDate() {
        return LocalDate.now(applicationZone);
    }

    private void validateText(UpdateRoutineActivityRequest request) {
        if (request.getTitle() != null && request.getTitle().trim().isBlank()) {
            throw new IllegalArgumentException("Title must not be blank");
        }
        if (request.getSortOrder() != null && request.getSortOrder() < 0) {
            throw new IllegalArgumentException("Sort order must be zero or greater");
        }
    }

    @Transactional
    public RoutineActivityResponse create(UUID patientId, CreateRoutineActivityRequest request,
                                          String actorEmail) {
        User actor = authenticatedUser(actorEmail);
        requirePatient(patientId, actor, CareCircleCapability.MANAGE_ROUTINES);
        String title = request.getTitle().trim();
        String description = request.getDescription() == null ? null : request.getDescription().trim();
        if (description != null && description.isEmpty()) {
            description = null;
        }
        int sortOrder = request.getSortOrder() == null ? 0 : request.getSortOrder();
        RoutineActivity activity = new RoutineActivity(
                patientId, title, description, request.getPeriod(),
                request.getScheduledTime(), sortOrder, actor.getId());
        return new RoutineActivityResponse(activityRepository.save(activity));
    }

    @Transactional(readOnly = true)
    public DailyRoutineResponse getRoutine(UUID patientId, LocalDate date, String actorEmail) {
        User actor = authenticatedUser(actorEmail);
        requirePatient(patientId, actor, CareCircleCapability.VIEW_ROUTINES);
        LocalDate requestedDate = date == null ? defaultCompletionDate() : date;

        List<RoutineActivity> activities = activityRepository
                .findByPatientUserIdAndActiveTrueOrderByPeriodAscSortOrderAscScheduledTimeAsc(patientId);
        List<DailyRoutineItemResponse> morning = new ArrayList<>();
        List<DailyRoutineItemResponse> afternoon = new ArrayList<>();
        List<DailyRoutineItemResponse> evening = new ArrayList<>();

        Map<UUID, RoutineActivityCompletion> completionsByActivityId = new HashMap<>();
        if (!activities.isEmpty()) {
            List<UUID> activityIds = activities.stream().map(RoutineActivity::getId).toList();
            completionRepository.findByActivityIdInAndCompletionDate(activityIds, requestedDate)
                    .forEach(completion -> completionsByActivityId.put(completion.getActivityId(), completion));
        }

        Set<UUID> completingUserIds = completionsByActivityId.values().stream()
                .map(RoutineActivityCompletion::getCompletedByUserId)
                .collect(java.util.stream.Collectors.toSet());
        Map<UUID, User> completingUsers = new HashMap<>();
        if (!completingUserIds.isEmpty()) {
            userRepository.findAllById(completingUserIds)
                    .forEach(user -> completingUsers.put(user.getId(), user));
        }

        for (RoutineActivity activity : activities) {
            if (!activity.getPatientUserId().equals(patientId)) continue;
            RoutineActivityCompletion completion = completionsByActivityId.get(activity.getId());
            DailyRoutineItemResponse item = completion == null
                    ? new DailyRoutineItemResponse(activity, false, null, null)
                    : new DailyRoutineItemResponse(activity, true, completion.getCompletedAt(),
                            new CompletedByResponse(completingUsers.get(completion.getCompletedByUserId())));
            switch (activity.getPeriod()) {
                case MORNING -> morning.add(item);
                case AFTERNOON -> afternoon.add(item);
                case EVENING -> evening.add(item);
            }
        }
        morning.sort(Comparator.comparingInt(DailyRoutineItemResponse::getSortOrder)
                .thenComparing((a, b) -> {
                    if (a.getScheduledTime() == null && b.getScheduledTime() == null) return 0;
                    if (a.getScheduledTime() == null) return 1;
                    if (b.getScheduledTime() == null) return -1;
                    return a.getScheduledTime().compareTo(b.getScheduledTime());
                }));
        afternoon.sort(Comparator.comparingInt(DailyRoutineItemResponse::getSortOrder)
                .thenComparing((a, b) -> compareScheduled(a.getScheduledTime(), b.getScheduledTime())));
        evening.sort(Comparator.comparingInt(DailyRoutineItemResponse::getSortOrder)
                .thenComparing((a, b) -> compareScheduled(a.getScheduledTime(), b.getScheduledTime())));
        return new DailyRoutineResponse(requestedDate, morning, afternoon, evening);
    }

    private int compareScheduled(java.time.LocalTime a, java.time.LocalTime b) {
        if (a == null && b == null) return 0;
        if (a == null) return 1;
        if (b == null) return -1;
        return a.compareTo(b);
    }

    @Transactional(readOnly = true)
    public RoutineActivityResponse getActivity(UUID patientId, UUID activityId, String actorEmail) {
        User actor = authenticatedUser(actorEmail);
        requirePatient(patientId, actor, CareCircleCapability.VIEW_ROUTINES);
        return new RoutineActivityResponse(requireActivity(patientId, activityId));
    }

    @Transactional
    public RoutineActivityResponse update(UUID patientId, UUID activityId,
                                          UpdateRoutineActivityRequest request, String actorEmail) {
        User actor = authenticatedUser(actorEmail);
        requirePatient(patientId, actor, CareCircleCapability.MANAGE_ROUTINES);
        validateText(request);
        RoutineActivity activity = requireActivity(patientId, activityId);
        if (request.getTitle() != null) activity.setTitle(request.getTitle().trim());
        if (request.isDescriptionSet()) {
            activity.setDescription(request.getDescription() == null || request.getDescription().trim().isEmpty()
                    ? null : request.getDescription().trim());
        }
        if (request.getPeriod() != null) activity.setPeriod(request.getPeriod());
        if (request.isScheduledTimeSet()) activity.setScheduledTime(request.getScheduledTime());
        if (request.getSortOrder() != null) activity.setSortOrder(request.getSortOrder());
        if (request.getActive() != null) activity.setActive(request.getActive());
        return new RoutineActivityResponse(activityRepository.save(activity));
    }

    @Transactional
    public RoutineActivityResponse archive(UUID patientId, UUID activityId, String actorEmail) {
        User actor = authenticatedUser(actorEmail);
        requirePatient(patientId, actor, CareCircleCapability.MANAGE_ROUTINES);
        RoutineActivity activity = requireActivity(patientId, activityId);
        activity.setActive(false);
        return new RoutineActivityResponse(activityRepository.save(activity));
    }

    @Transactional
    public RoutineActivityResponse reactivate(UUID patientId, UUID activityId, String actorEmail) {
        User actor = authenticatedUser(actorEmail);
        requirePatient(patientId, actor, CareCircleCapability.MANAGE_ROUTINES);
        RoutineActivity activity = requireActivity(patientId, activityId);
        activity.setActive(true);
        return new RoutineActivityResponse(activityRepository.save(activity));
    }

    @Transactional
    public CompletionResponse complete(UUID patientId, UUID activityId, LocalDate date,
                                       String actorEmail) {
        User actor = authenticatedUser(actorEmail);
        requirePatient(patientId, actor, CareCircleCapability.COMPLETE_ROUTINES);
        RoutineActivity activity = requireActivityForUpdate(patientId, activityId);
        if (!activity.isActive()) {
            throw new IllegalArgumentException("Archived activities cannot be completed");
        }
        LocalDate completionDate = date == null ? defaultCompletionDate() : date;
        RoutineActivityCompletion existing =
                completionRepository.findByActivityIdAndCompletionDate(activityId, completionDate).orElse(null);
        if (existing != null) {
            return toCompletionResponse(existing);
        }
        RoutineActivityCompletion saved = completionRepository.saveAndFlush(
                new RoutineActivityCompletion(activityId, completionDate, actor.getId()));
        return toCompletionResponse(saved);
    }

    @Transactional
    public void uncomplete(UUID patientId, UUID activityId, LocalDate date, String actorEmail) {
        User actor = authenticatedUser(actorEmail);
        requirePatient(patientId, actor, CareCircleCapability.COMPLETE_ROUTINES);
        requireActivity(patientId, activityId);
        LocalDate completionDate = date == null ? defaultCompletionDate() : date;
        completionRepository.deleteByActivityIdAndCompletionDate(activityId, completionDate);
    }

    @Transactional(readOnly = true)
    public CompletionHistoryResponse history(UUID patientId, UUID activityId,
                                             LocalDate from, LocalDate to,
                                             int page, int size, String actorEmail) {
        User actor = authenticatedUser(actorEmail);
        requirePatient(patientId, actor, CareCircleCapability.VIEW_ROUTINES);
        requireActivity(patientId, activityId);
        if (page < 0) throw new IllegalArgumentException("Page number must not be negative");
        if (size < 1 || size > 100) throw new IllegalArgumentException("Page size must be between 1 and 100");
        if (from != null && to != null && from.isAfter(to)) {
            throw new IllegalArgumentException("From date must not be after to date");
        }
        PageRequest pageable = PageRequest.of(page, size,
                Sort.by(Sort.Direction.DESC, "completionDate"));
        Page<RoutineActivityCompletion> result;
        if (from != null && to != null) {
            result = completionRepository.findByActivityIdAndCompletionDateBetween(
                    activityId, from, to, pageable);
        } else if (from != null) {
            result = completionRepository.findByActivityIdAndCompletionDateGreaterThanEqual(
                    activityId, from, pageable);
        } else if (to != null) {
            result = completionRepository.findByActivityIdAndCompletionDateLessThanEqual(
                    activityId, to, pageable);
        } else {
            result = completionRepository.findByActivityId(activityId, pageable);
        }
        List<RoutineActivityCompletion> completions = result.getContent();
        Set<UUID> completingUserIds = completions.stream()
                .map(RoutineActivityCompletion::getCompletedByUserId)
                .collect(java.util.stream.Collectors.toSet());
        Map<UUID, User> completingUsers = new HashMap<>();
        if (!completingUserIds.isEmpty()) {
            userRepository.findAllById(completingUserIds)
                    .forEach(user -> completingUsers.put(user.getId(), user));
        }
        List<CompletionResponse> content = completions.stream()
                .map(completion -> new CompletionResponse(
                        completion,
                        new CompletedByResponse(completingUsers.get(completion.getCompletedByUserId()))))
                .toList();
        return new CompletionHistoryResponse(content, result.getNumber(), result.getSize(),
                result.getTotalElements(), result.getTotalPages());
    }

    private CompletionResponse toCompletionResponse(RoutineActivityCompletion completion) {
        User completedBy = userRepository.findById(completion.getCompletedByUserId())
                .orElseThrow(() -> new ResourceNotFoundException("Completion user not found"));
        return new CompletionResponse(completion, new CompletedByResponse(completedBy));
    }
}
