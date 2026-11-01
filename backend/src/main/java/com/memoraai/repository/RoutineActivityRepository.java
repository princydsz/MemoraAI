package com.memoraai.repository;

import com.memoraai.entity.RoutineActivity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RoutineActivityRepository extends JpaRepository<RoutineActivity, UUID> {
    List<RoutineActivity> findByPatientUserIdAndActiveTrueOrderByPeriodAscSortOrderAscScheduledTimeAsc(
            UUID patientUserId);
    Optional<RoutineActivity> findByIdAndPatientUserId(UUID id, UUID patientUserId);

    List<RoutineActivity> findByActiveTrue();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from RoutineActivity a where a.id = :id and a.patientUserId = :patientUserId")
    Optional<RoutineActivity> findByIdAndPatientUserIdForUpdate(
            @Param("id") UUID id, @Param("patientUserId") UUID patientUserId);
}
