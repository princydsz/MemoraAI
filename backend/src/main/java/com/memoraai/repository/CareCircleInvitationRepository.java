package com.memoraai.repository;

import com.memoraai.entity.CareCircleInvitation;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CareCircleInvitationRepository extends JpaRepository<CareCircleInvitation, UUID> {
    Optional<CareCircleInvitation> findByTokenHash(String tokenHash);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from CareCircleInvitation i where i.tokenHash = :tokenHash")
    Optional<CareCircleInvitation> findByTokenHashForUpdate(@Param("tokenHash") String tokenHash);

    List<CareCircleInvitation> findAllByPatientUserIdOrderByCreatedAtDesc(UUID patientUserId);
}
