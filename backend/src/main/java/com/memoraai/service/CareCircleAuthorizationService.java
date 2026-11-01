package com.memoraai.service;

import com.memoraai.entity.CareCircleCapability;
import com.memoraai.entity.CareCircleMembership;
import com.memoraai.entity.MembershipStatus;
import com.memoraai.entity.PermissionLevel;
import com.memoraai.exception.ResourceNotFoundException;
import com.memoraai.repository.CareCircleMembershipRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class CareCircleAuthorizationService {

    public static final String PATIENT_ACCESS_NOT_FOUND =
            "Patient or active care circle membership not found";
    public static final String INSUFFICIENT_PERMISSION =
            "You do not have permission to perform this action";

    private final CareCircleMembershipRepository careCircleMembershipRepository;

    public CareCircleAuthorizationService(CareCircleMembershipRepository careCircleMembershipRepository) {
        this.careCircleMembershipRepository = careCircleMembershipRepository;
    }

    public CareCircleMembership requirePatientAccess(UUID callerId, UUID patientId) {
        if (callerId == null || patientId == null) {
            throw new ResourceNotFoundException(PATIENT_ACCESS_NOT_FOUND);
        }
        if (callerId.equals(patientId)) {
            return null;
        }
        return careCircleMembershipRepository
                .findByPatientUserIdAndFamilyMemberUserIdAndStatus(
                        patientId,
                        callerId,
                        MembershipStatus.ACTIVE
                )
                .orElseThrow(() -> new ResourceNotFoundException(PATIENT_ACCESS_NOT_FOUND));
    }

    public boolean hasCapability(UUID callerId, UUID patientId, CareCircleCapability capability) {
        if (callerId == null || patientId == null || capability == null) {
            return false;
        }
        if (callerId.equals(patientId)) {
            return true;
        }

        CareCircleMembership membership = careCircleMembershipRepository
                .findByPatientUserIdAndFamilyMemberUserIdAndStatus(
                        patientId,
                        callerId,
                        MembershipStatus.ACTIVE
                )
                .orElse(null);
        if (membership == null) {
            return false;
        }
        return hasCapability(membership.getPermissionLevel(), capability);
    }

    public void requireCapability(UUID callerId, UUID patientId, CareCircleCapability capability) {
        if (callerId == null || patientId == null || capability == null) {
            throw new ResourceNotFoundException(PATIENT_ACCESS_NOT_FOUND);
        }
        if (!callerId.equals(patientId)) {
            CareCircleMembership membership = requirePatientAccess(callerId, patientId);
            if (!hasCapability(membership.getPermissionLevel(), capability)) {
                throw new AccessDeniedException(INSUFFICIENT_PERMISSION);
            }
            return;
        }
    }

    public boolean hasPermission(
            UUID callerId,
            UUID patientId,
            PermissionLevel requiredPermissionLevel) {
        if (callerId == null || patientId == null || requiredPermissionLevel == null) {
            return false;
        }
        if (callerId.equals(patientId)) {
            return true;
        }

        return careCircleMembershipRepository
                .findByPatientUserIdAndFamilyMemberUserIdAndStatus(
                        patientId,
                        callerId,
                        MembershipStatus.ACTIVE
                )
                .map(membership -> permissionAtLeast(
                        membership.getPermissionLevel(),
                        requiredPermissionLevel
                ))
                .orElse(false);
    }

    public boolean hasCapability(PermissionLevel permissionLevel, CareCircleCapability capability) {
        if (permissionLevel == null || capability == null) {
            return false;
        }

        return switch (capability) {
            case VIEW_MEMORIES, VIEW_ROUTINES, VIEW_APPOINTMENTS -> true;
            case CONTRIBUTE_MEMORIES, UPLOAD_OWN_ATTACHMENTS, COMPLETE_ROUTINES ->
                    permissionLevel == PermissionLevel.CONTRIBUTE
                            || permissionLevel == PermissionLevel.FULL_ACCESS;
            case UPLOAD_ANY_ATTACHMENTS, MANAGE_ROUTINES, MANAGE_APPOINTMENTS,
                 INVITE_FAMILY, MANAGE_MEMBERSHIPS ->
                    permissionLevel == PermissionLevel.FULL_ACCESS;
        };
    }

    public boolean permissionAtLeast(
            PermissionLevel actual,
            PermissionLevel required) {
        if (actual == null || required == null) {
            return false;
        }
        return switch (actual) {
            case VIEW_ONLY -> required == PermissionLevel.VIEW_ONLY;
            case CONTRIBUTE -> required == PermissionLevel.VIEW_ONLY
                    || required == PermissionLevel.CONTRIBUTE;
            case FULL_ACCESS -> true;
        };
    }

    public void validateNotSelfMembership(UUID patientUserId, UUID familyMemberUserId) {
        if (patientUserId != null && patientUserId.equals(familyMemberUserId)) {
            throw new IllegalArgumentException("A user cannot be their own family member");
        }
    }
}
