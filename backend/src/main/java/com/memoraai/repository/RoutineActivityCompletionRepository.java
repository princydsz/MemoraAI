package com.memoraai.repository;

import com.memoraai.entity.RoutineActivityCompletion;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RoutineActivityCompletionRepository extends JpaRepository<RoutineActivityCompletion, UUID> {
    Optional<RoutineActivityCompletion> findByActivityIdAndCompletionDate(UUID activityId, LocalDate completionDate);
    List<RoutineActivityCompletion> findByActivityIdInAndCompletionDate(
            List<UUID> activityIds, LocalDate completionDate);
    void deleteByActivityIdAndCompletionDate(UUID activityId, LocalDate completionDate);
    Page<RoutineActivityCompletion> findByActivityIdAndCompletionDateBetween(
            UUID activityId, LocalDate from, LocalDate to, Pageable pageable);
    Page<RoutineActivityCompletion> findByActivityIdAndCompletionDateGreaterThanEqual(
            UUID activityId, LocalDate from, Pageable pageable);
    Page<RoutineActivityCompletion> findByActivityIdAndCompletionDateLessThanEqual(
            UUID activityId, LocalDate to, Pageable pageable);
    Page<RoutineActivityCompletion> findByActivityId(UUID activityId, Pageable pageable);
}
