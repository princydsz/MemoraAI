package com.memoraai.repository;

import com.memoraai.entity.CareCircleMembership;
import com.memoraai.entity.MembershipStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CareCircleMembershipRepository extends JpaRepository<CareCircleMembership, UUID> {
    Optional<CareCircleMembership> findByPatientUserIdAndFamilyMemberUserId(
            UUID patientUserId,
            UUID familyMemberUserId
    );

    Optional<CareCircleMembership> findByPatientUserIdAndFamilyMemberUserIdAndStatus(
            UUID patientUserId,
            UUID familyMemberUserId,
            MembershipStatus status
    );

    List<CareCircleMembership> findAllByFamilyMemberUserIdAndStatus(
            UUID familyMemberUserId,
            MembershipStatus status
    );

    List<CareCircleMembership> findAllByPatientUserIdAndStatus(
            UUID patientUserId,
            MembershipStatus status
    );
}
