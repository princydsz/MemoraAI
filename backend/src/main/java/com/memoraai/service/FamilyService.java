package com.memoraai.service;

import com.memoraai.dto.CreatePatientRequest;
import com.memoraai.dto.PatientResponse;
import com.memoraai.dto.CreateInvitationRequest;
import com.memoraai.dto.InvitationResponse;
import com.memoraai.dto.MemberResponse;
import com.memoraai.dto.RedeemInvitationRequest;
import com.memoraai.dto.UpdateMemberRequest;
import com.memoraai.entity.CareCircleMembership;
import com.memoraai.entity.MembershipStatus;
import com.memoraai.entity.PermissionLevel;
import com.memoraai.entity.Role;
import com.memoraai.entity.RelationshipType;
import com.memoraai.entity.User;
import com.memoraai.entity.CareCircleInvitation;
import com.memoraai.entity.InvitationStatus;
import com.memoraai.entity.CareCircleCapability;
import com.memoraai.entity.NotificationType;
import com.memoraai.exception.DuplicateResourceException;
import com.memoraai.exception.ResourceNotFoundException;
import com.memoraai.repository.CareCircleMembershipRepository;
import com.memoraai.repository.CareCircleInvitationRepository;
import com.memoraai.repository.UserRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import java.time.Instant;

@Service
public class FamilyService {

    private final UserRepository userRepository;
    private final CareCircleMembershipRepository careCircleMembershipRepository;
    private final CareCircleInvitationRepository careCircleInvitationRepository;
    private final CareCircleAuthorizationService authorizationService;
    private final NotificationService notificationService;
    private final PasswordEncoder passwordEncoder;
    private final SecureRandom secureRandom = new SecureRandom();

    public FamilyService(
            UserRepository userRepository,
            CareCircleMembershipRepository careCircleMembershipRepository,
            CareCircleInvitationRepository careCircleInvitationRepository,
            CareCircleAuthorizationService authorizationService,
            PasswordEncoder passwordEncoder,
            NotificationService notificationService) {
        this.userRepository = userRepository;
        this.careCircleMembershipRepository = careCircleMembershipRepository;
        this.careCircleInvitationRepository = careCircleInvitationRepository;
        this.authorizationService = authorizationService;
        this.passwordEncoder = passwordEncoder;
        this.notificationService = notificationService;
    }

    private User getAuthenticatedUser(String email) {
        return userRepository.findByEmail(email.toLowerCase().trim())
                .orElseThrow(() -> new UsernameNotFoundException("User not found with email: " + email));
    }

    private String generateInternalEmail() {
        return "patient-" + UUID.randomUUID() + "@patients.memoraai.invalid";
    }

    private String generateUnknownPassword() {
        byte[] randomBytes = new byte[32];
        secureRandom.nextBytes(randomBytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
    }

    @Transactional
    public PatientResponse createPatient(CreatePatientRequest request, String caregiverEmail) {
        User caregiver = getAuthenticatedUser(caregiverEmail);
        String normalizedName = request.getName().trim();

        User patient = new User(
                normalizedName,
                generateInternalEmail(),
                passwordEncoder.encode(generateUnknownPassword()),
                Role.USER
        );
        User savedPatient;
        CareCircleMembership savedMembership;
        try {
            savedPatient = userRepository.save(patient);

            CareCircleMembership membership = new CareCircleMembership(
                    savedPatient.getId(),
                    caregiver.getId(),
                    request.getRelationshipType(),
                    PermissionLevel.FULL_ACCESS,
                    MembershipStatus.ACTIVE
            );
            savedMembership = careCircleMembershipRepository.save(membership);
        } catch (DataIntegrityViolationException ex) {
            throw new DuplicateResourceException(
                    "The patient account or care circle membership conflicts with existing data"
            );
        }

        return PatientResponse.fromEntity(savedPatient, savedMembership);
    }

    @Transactional(readOnly = true)
    public List<PatientResponse> getPatients(String caregiverEmail) {
        User caregiver = getAuthenticatedUser(caregiverEmail);
        List<CareCircleMembership> memberships =
                careCircleMembershipRepository.findAllByFamilyMemberUserIdAndStatus(
                        caregiver.getId(),
                        MembershipStatus.ACTIVE
                );

        return memberships.stream()
                .map(membership -> userRepository.findById(membership.getPatientUserId())
                        .map(patient -> PatientResponse.fromEntity(patient, membership))
                        .orElse(null))
                .filter(response -> response != null)
                .toList();
    }

    private String normalizeEmail(String email) {
        return email == null || email.isBlank() ? null : email.toLowerCase().trim();
    }

    private String generateInvitationToken() {
        byte[] randomBytes = new byte[32];
        secureRandom.nextBytes(randomBytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
    }

    private String hashToken(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte value : digest) {
                hex.append(String.format("%02x", value));
            }
            return hex.toString();
        } catch (java.security.NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is unavailable", ex);
        }
    }

    private User getPatient(UUID patientId) {
        return userRepository.findById(patientId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        authorizationService.PATIENT_ACCESS_NOT_FOUND));
    }

    private void requireInvitationManager(User actor, UUID patientId) {
        if (actor.getId().equals(patientId)) {
            return;
        }
        authorizationService.requireCapability(
                actor.getId(), patientId, CareCircleCapability.MANAGE_MEMBERSHIPS);
    }

    private int permissionRank(PermissionLevel permissionLevel) {
        return switch (permissionLevel) {
            case VIEW_ONLY -> 0;
            case CONTRIBUTE -> 1;
            case FULL_ACCESS -> 2;
        };
    }

    private void validateCanGrant(User actor, UUID patientId, PermissionLevel requested) {
        if (actor.getId().equals(patientId)) {
            return;
        }
        CareCircleMembership membership = careCircleMembershipRepository
                .findByPatientUserIdAndFamilyMemberUserIdAndStatus(
                        patientId, actor.getId(), MembershipStatus.ACTIVE)
                .orElseThrow(() -> new ResourceNotFoundException(
                        authorizationService.PATIENT_ACCESS_NOT_FOUND));
        if (permissionRank(requested) > permissionRank(membership.getPermissionLevel())) {
            throw new org.springframework.security.access.AccessDeniedException(
                    authorizationService.INSUFFICIENT_PERMISSION);
        }
    }

    private java.util.LinkedHashSet<UUID> activeManagersAndPatient(UUID patientId) {
        java.util.LinkedHashSet<UUID> recipients = new java.util.LinkedHashSet<>();
        recipients.add(patientId);
        careCircleMembershipRepository
                .findAllByPatientUserIdAndStatus(patientId, MembershipStatus.ACTIVE)
                .stream()
                .filter(membership -> membership.getPermissionLevel() == PermissionLevel.FULL_ACCESS)
                .map(CareCircleMembership::getFamilyMemberUserId)
                .forEach(recipients::add);
        return recipients;
    }

    private void createInvitationRedemptionNotifications(UUID patientId, UUID inviterId,
                                                         UUID redeemerId, String redeemerName,
                                                         String dedupeKey) {
        java.util.LinkedHashSet<UUID> baseRecipients = activeManagersAndPatient(patientId);
        baseRecipients.remove(redeemerId);

        UUID effectiveInviterId = inviterId != null
                && userRepository.findById(inviterId).isPresent()
                ? inviterId
                : null;
        if (effectiveInviterId != null
                && !effectiveInviterId.equals(redeemerId)) {
            baseRecipients.add(effectiveInviterId);
        }

        for (UUID recipientId : baseRecipients) {
            NotificationType type = recipientId.equals(effectiveInviterId)
                    ? NotificationType.INVITATION_REDEEMED
                    : NotificationType.MEMBER_JOINED;
            String title = type == NotificationType.INVITATION_REDEEMED
                    ? "Care Circle invitation redeemed"
                    : "New Care Circle member";
            String message = type == NotificationType.INVITATION_REDEEMED
                    ? redeemerName + " redeemed your invitation and joined the Care Circle."
                    : redeemerName + " joined the Care Circle.";
            notificationService.createNotification(
                    recipientId,
                    patientId,
                    redeemerId,
                    type,
                    title,
                    message,
                    redeemerId,
                    "care_circle_member",
                    dedupeKey + ":" + recipientId);
        }
    }

    private void createMemberRemovedNotifications(UUID patientId, UUID actorId,
                                                   UUID removedMemberId, String removedMemberName,
                                                   boolean memberLeft, String dedupeKey) {
        if (!memberLeft) {
            notificationService.createNotification(
                    removedMemberId,
                    null,
                    actorId,
                    NotificationType.MEMBER_REMOVED,
                    "Care Circle access revoked",
                    "Your access to the patient's Care Circle was revoked.",
                    patientId,
                    "patient",
                    dedupeKey + ":member:" + removedMemberId);
        }
        for (UUID recipientId : activeManagersAndPatient(patientId)) {
            if (recipientId.equals(removedMemberId)) {
                continue;
            }
            notificationService.createNotification(
                    recipientId,
                    patientId,
                    actorId,
                    NotificationType.MEMBER_REMOVED,
                    memberLeft ? "Care Circle member left" : "Care Circle member removed",
                    removedMemberName + (memberLeft
                            ? " left the Care Circle."
                            : " was removed from the Care Circle."),
                    removedMemberId,
                    "care_circle_member",
                    dedupeKey + ":" + recipientId);
        }
    }

    @Transactional
    public InvitationResponse createInvitation(UUID patientId, CreateInvitationRequest request,
                                               String inviterEmail) {
        User actor = getAuthenticatedUser(inviterEmail);
        getPatient(patientId);
        requireInvitationManager(actor, patientId);
        validateCanGrant(actor, patientId, request.getPermissionLevel());

        Instant now = Instant.now();
        String plaintextToken = generateInvitationToken();
        CareCircleInvitation invitation = new CareCircleInvitation(
                patientId,
                actor.getId(),
                normalizeEmail(request.getIntendedEmail()),
                hashToken(plaintextToken),
                request.getPermissionLevel(),
                request.getRelationshipType(),
                null);
        CareCircleInvitation saved = careCircleInvitationRepository.save(invitation);
        return InvitationResponse.fromEntity(saved, now, plaintextToken);
    }

    @Transactional
    public MemberResponse redeemInvitation(RedeemInvitationRequest request, String redeemerEmail) {
        User redeemer = getAuthenticatedUser(redeemerEmail);
        if (request.getToken() == null || request.getToken().isBlank()) {
            throw new IllegalArgumentException("Invitation token is required");
        }

        String tokenHash = hashToken(request.getToken().trim());
        CareCircleInvitation invitation = careCircleInvitationRepository
                .findByTokenHashForUpdate(tokenHash)
                .orElseThrow(() -> new ResourceNotFoundException("Invitation not found"));

        Instant now = Instant.now();
        InvitationStatus effectiveStatus = invitation.effectiveStatus(now);
        if (effectiveStatus == InvitationStatus.EXPIRED) {
            throw new DuplicateResourceException("Invitation has expired");
        }
        if (effectiveStatus == InvitationStatus.REVOKED) {
            throw new DuplicateResourceException("Invitation has been revoked");
        }
        if (effectiveStatus == InvitationStatus.REDEEMED) {
            throw new DuplicateResourceException("Invitation has already been redeemed");
        }
        if (!effectiveStatus.equals(InvitationStatus.PENDING)) {
            throw new DuplicateResourceException("Invitation cannot be redeemed");
        }
        if (redeemer.getId().equals(invitation.getInviterUserId())) {
            throw new DuplicateResourceException("The inviter cannot redeem their own invitation");
        }
        if (redeemer.getId().equals(invitation.getPatientUserId())) {
            throw new DuplicateResourceException("The patient cannot redeem this invitation");
        }
        if (invitation.getIntendedEmail() != null
                && !invitation.getIntendedEmail().equals(normalizeEmail(redeemer.getEmail()))) {
            throw new DuplicateResourceException("Invitation is intended for a different email address");
        }

        var existing = careCircleMembershipRepository
                .findByPatientUserIdAndFamilyMemberUserId(
                        invitation.getPatientUserId(), redeemer.getId());
        if (existing.isPresent() && existing.get().getStatus() == MembershipStatus.ACTIVE) {
            throw new DuplicateResourceException("Active Care Circle membership already exists");
        }

        CareCircleMembership membership = existing
                .map(current -> {
                    current.setRelationshipType(invitation.getRelationshipType());
                    current.setPermissionLevel(invitation.getPermissionLevel());
                    current.setStatus(MembershipStatus.ACTIVE);
                    return current;
                })
                .orElseGet(() -> new CareCircleMembership(
                        invitation.getPatientUserId(),
                        redeemer.getId(),
                        invitation.getRelationshipType(),
                        invitation.getPermissionLevel(),
                        MembershipStatus.ACTIVE));
        CareCircleMembership saved = careCircleMembershipRepository.save(membership);
        invitation.redeem(redeemer.getId(), now);
        careCircleInvitationRepository.save(invitation);
        createInvitationRedemptionNotifications(
                invitation.getPatientUserId(),
                invitation.getInviterUserId(),
                redeemer.getId(),
                redeemer.getName(),
                "invitation:" + invitation.getId() + ":redeemed");

        return MemberResponse.fromEntity(redeemer, saved);
    }

    @Transactional(readOnly = true)
    public List<MemberResponse> getMembers(UUID patientId, String actorEmail) {
        User actor = getAuthenticatedUser(actorEmail);
        getPatient(patientId);
        if (!actor.getId().equals(patientId)) {
            authorizationService.requireCapability(
                    actor.getId(), patientId, CareCircleCapability.MANAGE_MEMBERSHIPS);
        }
        return careCircleMembershipRepository
                .findAllByPatientUserIdAndStatus(patientId, MembershipStatus.ACTIVE)
                .stream()
                .map(membership -> userRepository.findById(membership.getFamilyMemberUserId())
                        .map(member -> MemberResponse.fromEntity(member, membership))
                        .orElse(null))
                .filter(response -> response != null)
                .toList();
    }

    @Transactional
    public MemberResponse updateMember(UUID patientId, UUID memberId,
                                       UpdateMemberRequest request, String actorEmail) {
        User actor = getAuthenticatedUser(actorEmail);
        getPatient(patientId);
        if (memberId.equals(patientId)) {
            throw new IllegalArgumentException("Patient self-access cannot be changed through a membership record");
        }
        requireInvitationManager(actor, patientId);
        validateCanGrant(actor, patientId, request.getPermissionLevel());

        CareCircleMembership membership = careCircleMembershipRepository
                .findByPatientUserIdAndFamilyMemberUserIdAndStatus(
                        patientId, memberId, MembershipStatus.ACTIVE)
                .orElseThrow(() -> new ResourceNotFoundException(
                        authorizationService.PATIENT_ACCESS_NOT_FOUND));
        if (actor.getId().equals(memberId)
                && permissionRank(request.getPermissionLevel()) > permissionRank(membership.getPermissionLevel())) {
            throw new org.springframework.security.access.AccessDeniedException(
                    "A member cannot promote themselves");
        }
        membership.setPermissionLevel(request.getPermissionLevel());
        CareCircleMembership saved = careCircleMembershipRepository.save(membership);
        User member = userRepository.findById(memberId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        authorizationService.PATIENT_ACCESS_NOT_FOUND));
        return MemberResponse.fromEntity(member, saved);
    }

    @Transactional
    public void revokeMember(UUID patientId, UUID memberId, String actorEmail) {
        User actor = getAuthenticatedUser(actorEmail);
        getPatient(patientId);
        if (memberId.equals(patientId)) {
            throw new IllegalArgumentException("Patient self-access cannot be revoked");
        }
        if (!actor.getId().equals(memberId)) {
            requireInvitationManager(actor, patientId);
        }
        CareCircleMembership membership = careCircleMembershipRepository
                .findByPatientUserIdAndFamilyMemberUserIdAndStatus(
                        patientId, memberId, MembershipStatus.ACTIVE)
                .orElseThrow(() -> new ResourceNotFoundException(
                        authorizationService.PATIENT_ACCESS_NOT_FOUND));
        membership.setStatus(MembershipStatus.REVOKED);
        careCircleMembershipRepository.save(membership);
        User removedMember = userRepository.findById(memberId).orElse(null);
        createMemberRemovedNotifications(
                patientId,
                actor.getId(),
                memberId,
                removedMember == null ? "A member" : removedMember.getName(),
                actor.getId().equals(memberId),
                "membership:" + membership.getId() + ":removed");
    }

    @Transactional
    public InvitationResponse revokeInvitation(UUID patientId, UUID invitationId, String actorEmail) {
        User actor = getAuthenticatedUser(actorEmail);
        getPatient(patientId);
        requireInvitationManager(actor, patientId);
        CareCircleInvitation invitation = careCircleInvitationRepository.findById(invitationId)
                .orElseThrow(() -> new ResourceNotFoundException("Invitation not found"));
        if (!invitation.getPatientUserId().equals(patientId)) {
            throw new ResourceNotFoundException("Invitation not found");
        }
        Instant now = Instant.now();
        if (invitation.effectiveStatus(now) != InvitationStatus.PENDING) {
            throw new DuplicateResourceException("Only pending invitations can be revoked");
        }
        invitation.revoke(now);
        CareCircleInvitation saved = careCircleInvitationRepository.save(invitation);
        return InvitationResponse.fromEntity(saved, now, null);
    }
}
