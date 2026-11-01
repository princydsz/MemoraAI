package com.memoraai;

import com.memoraai.entity.CareCircleCapability;
import com.memoraai.entity.CareCircleMembership;
import com.memoraai.entity.MembershipStatus;
import com.memoraai.entity.PermissionLevel;
import com.memoraai.entity.RelationshipType;
import com.memoraai.exception.ResourceNotFoundException;
import com.memoraai.repository.CareCircleMembershipRepository;
import com.memoraai.service.CareCircleAuthorizationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CareCircleAuthorizationServiceTest {

    @Mock
    private CareCircleMembershipRepository membershipRepository;

    private CareCircleAuthorizationService authorizationService;

    @BeforeEach
    void setUp() {
        authorizationService = new CareCircleAuthorizationService(membershipRepository);
    }

    private CareCircleMembership membership(PermissionLevel permissionLevel, MembershipStatus status) {
        return new CareCircleMembership(
                UUID.randomUUID(),
                UUID.randomUUID(),
                RelationshipType.CAREGIVER,
                permissionLevel,
                status
        );
    }

    @Test
    void patientHasSelfAccessWithoutMembership() {
        UUID patientId = UUID.randomUUID();

        assertThat(authorizationService.hasCapability(
                patientId,
                patientId,
                CareCircleCapability.MANAGE_MEMBERSHIPS
        )).isTrue();
    }

    @Test
    void viewOnlyHasReadCapabilitiesOnly() {
        assertThat(authorizationService.hasCapability(
                PermissionLevel.VIEW_ONLY,
                CareCircleCapability.VIEW_MEMORIES
        )).isTrue();
        assertThat(authorizationService.hasCapability(
                PermissionLevel.VIEW_ONLY,
                CareCircleCapability.CONTRIBUTE_MEMORIES
        )).isFalse();
        assertThat(authorizationService.hasCapability(
                PermissionLevel.VIEW_ONLY,
                CareCircleCapability.MANAGE_ROUTINES
        )).isFalse();
    }

    @Test
    void contributeHasContributionAndRoutineCompletionCapabilities() {
        assertThat(authorizationService.hasCapability(
                PermissionLevel.CONTRIBUTE,
                CareCircleCapability.CONTRIBUTE_MEMORIES
        )).isTrue();
        assertThat(authorizationService.hasCapability(
                PermissionLevel.CONTRIBUTE,
                CareCircleCapability.UPLOAD_OWN_ATTACHMENTS
        )).isTrue();
        assertThat(authorizationService.hasCapability(
                PermissionLevel.CONTRIBUTE,
                CareCircleCapability.COMPLETE_ROUTINES
        )).isTrue();
        assertThat(authorizationService.hasCapability(
                PermissionLevel.CONTRIBUTE,
                CareCircleCapability.UPLOAD_ANY_ATTACHMENTS
        )).isFalse();
        assertThat(authorizationService.hasCapability(
                PermissionLevel.CONTRIBUTE,
                CareCircleCapability.MANAGE_APPOINTMENTS
        )).isFalse();
    }

    @Test
    void fullAccessReceivesAllDefinedCapabilities() {
        for (CareCircleCapability capability : CareCircleCapability.values()) {
            assertThat(authorizationService.hasCapability(
                    PermissionLevel.FULL_ACCESS,
                    capability
            )).isTrue();
        }
    }

    @Test
    void revokedAndUnrelatedMembershipsReturnNotFound() {
        UUID patientId = UUID.randomUUID();
        UUID callerId = UUID.randomUUID();
        when(membershipRepository.findByPatientUserIdAndFamilyMemberUserIdAndStatus(
                patientId,
                callerId,
                MembershipStatus.ACTIVE
        )).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authorizationService.requirePatientAccess(callerId, patientId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage(CareCircleAuthorizationService.PATIENT_ACCESS_NOT_FOUND);
    }

    @Test
    void insufficientPermissionReturnsForbidden() {
        UUID patientId = UUID.randomUUID();
        UUID callerId = UUID.randomUUID();
        CareCircleMembership viewOnly = membership(PermissionLevel.VIEW_ONLY, MembershipStatus.ACTIVE);
        viewOnly.setPatientUserId(patientId);
        viewOnly.setFamilyMemberUserId(callerId);

        when(membershipRepository.findByPatientUserIdAndFamilyMemberUserIdAndStatus(
                patientId,
                callerId,
                MembershipStatus.ACTIVE
        )).thenReturn(Optional.of(viewOnly));

        assertThatThrownBy(() -> authorizationService.requireCapability(
                callerId,
                patientId,
                CareCircleCapability.CONTRIBUTE_MEMORIES
        )).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void adminDoesNotReceiveAutomaticCareCircleAccess() {
        UUID patientId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();
        when(membershipRepository.findByPatientUserIdAndFamilyMemberUserIdAndStatus(
                patientId,
                adminId,
                MembershipStatus.ACTIVE
        )).thenReturn(Optional.empty());

        assertThat(authorizationService.hasCapability(
                adminId,
                patientId,
                CareCircleCapability.VIEW_MEMORIES
        )).isFalse();
    }

    @Test
    void selfMembershipIsRejected() {
        UUID userId = UUID.randomUUID();

        assertThatThrownBy(() -> new CareCircleMembership(
                userId,
                userId,
                RelationshipType.SPOUSE,
                PermissionLevel.FULL_ACCESS,
                MembershipStatus.ACTIVE
        )).isInstanceOf(IllegalArgumentException.class);
    }
}
