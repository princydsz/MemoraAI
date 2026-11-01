package com.memoraai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.memoraai.entity.MembershipStatus;
import com.memoraai.entity.Notification;
import com.memoraai.entity.NotificationType;
import com.memoraai.entity.PermissionLevel;
import com.memoraai.entity.RelationshipType;
import com.memoraai.entity.Role;
import com.memoraai.entity.User;
import com.memoraai.entity.CareCircleMembership;
import com.memoraai.entity.CareCircleInvitation;
import com.memoraai.dto.RedeemInvitationRequest;
import com.memoraai.repository.CareCircleMembershipRepository;
import com.memoraai.repository.CareCircleInvitationRepository;
import com.memoraai.repository.NotificationRepository;
import com.memoraai.repository.UserRepository;
import com.memoraai.repository.MemoryRepository;
import com.memoraai.repository.MemoryMediaRepository;
import com.memoraai.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.http.MediaType;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class NotificationIntegrationTest {
    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired UserRepository userRepository;
    @Autowired CareCircleMembershipRepository membershipRepository;
    @Autowired CareCircleInvitationRepository invitationRepository;
    @Autowired NotificationRepository notificationRepository;
    @Autowired MemoryMediaRepository memoryMediaRepository;
    @Autowired MemoryRepository memoryRepository;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired JwtService jwtService;

    private User patient;
    private User member;
    private User outsider;
    private String patientToken;
    private String memberToken;
    private String outsiderToken;

    @BeforeEach
    void setUp() {
        notificationRepository.deleteAll();
        membershipRepository.deleteAll();
        invitationRepository.deleteAll();
        memoryMediaRepository.deleteAll();
        memoryRepository.deleteAll();
        userRepository.deleteAll();
        patient = createUser("Patient User", "notification-patient@example.com");
        member = createUser("Member User", "notification-member@example.com");
        outsider = createUser("Outsider User", "notification-outsider@example.com");
        patientToken = jwtService.generateToken(patient);
        memberToken = jwtService.generateToken(member);
        outsiderToken = jwtService.generateToken(outsider);
        membershipRepository.save(new CareCircleMembership(
                patient.getId(), member.getId(), RelationshipType.CHILD,
                PermissionLevel.FULL_ACCESS, MembershipStatus.ACTIVE));
    }

    private User createUser(String name, String email) {
        return userRepository.save(new User(
                name, email, passwordEncoder.encode("Password123!"), Role.USER));
    }

    private Notification createNotification(UUID recipient, UUID patientId, NotificationType type) {
        return notificationRepository.save(new Notification(
                recipient, patientId, patient.getId(), type,
                "Test notification", "Safe test message",
                null, null, UUID.randomUUID() + ":" + recipient));
    }

    private String hashToken(String token) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256")
                .digest(token.getBytes(StandardCharsets.UTF_8));
        StringBuilder result = new StringBuilder();
        for (byte value : digest) {
            result.append(String.format("%02x", value));
        }
        return result.toString();
    }

    private CareCircleInvitation createInvitation(UUID inviterId, String token) throws Exception {
        return invitationRepository.save(new CareCircleInvitation(
                patient.getId(),
                inviterId,
                outsider.getEmail(),
                hashToken(token),
                PermissionLevel.CONTRIBUTE,
                RelationshipType.CHILD,
                Instant.now().plusSeconds(86400)));
    }

    private void redeem(String token, String redeemerToken) throws Exception {
        RedeemInvitationRequest request = new RedeemInvitationRequest();
        request.setToken(token);
        mockMvc.perform(post("/api/family/invitations/redeem")
                        .header("Authorization", "Bearer " + redeemerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());
    }

    @Test
    void listRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/notifications"))
                .andExpect(status().isForbidden());
    }

    @Test
    void activeMemberListsPatientContextNotificationAndUnreadCount() throws Exception {
        createNotification(member.getId(), patient.getId(), NotificationType.MEMBER_JOINED);

        mockMvc.perform(get("/api/notifications")
                        .header("Authorization", "Bearer " + memberToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].type").value("MEMBER_JOINED"))
                .andExpect(jsonPath("$.totalElements").value(1));

        mockMvc.perform(get("/api/notifications/unread-count")
                        .header("Authorization", "Bearer " + memberToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unreadCount").value(1));
    }

    @Test
    void outsiderCannotSeeOrReadPatientNotification() throws Exception {
        Notification notification = createNotification(
                patient.getId(), patient.getId(), NotificationType.MEMBER_JOINED);

        mockMvc.perform(get("/api/notifications")
                        .header("Authorization", "Bearer " + outsiderToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));

        mockMvc.perform(patch("/api/notifications/{id}/read", notification.getId())
                        .header("Authorization", "Bearer " + outsiderToken))
                .andExpect(status().isNotFound());

        mockMvc.perform(delete("/api/notifications/{id}", notification.getId())
                        .header("Authorization", "Bearer " + outsiderToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void patientContextNotificationStopsBeingVisibleAfterMembershipRevocation() throws Exception {
        Notification notification = createNotification(
                member.getId(), patient.getId(), NotificationType.INVITATION_REDEEMED);
        CareCircleMembership membership = membershipRepository
                .findByPatientUserIdAndFamilyMemberUserId(patient.getId(), member.getId())
                .orElseThrow();
        membership.setStatus(MembershipStatus.REVOKED);
        membershipRepository.save(membership);

        mockMvc.perform(get("/api/notifications")
                        .header("Authorization", "Bearer " + memberToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));

        mockMvc.perform(patch("/api/notifications/{id}/read", notification.getId())
                        .header("Authorization", "Bearer " + memberToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void nullPatientContextNotificationRemainsVisibleAfterRevocationAndCanBeRead() throws Exception {
        CareCircleMembership membership = membershipRepository
                .findByPatientUserIdAndFamilyMemberUserId(patient.getId(), member.getId())
                .orElseThrow();
        membership.setStatus(MembershipStatus.REVOKED);
        membershipRepository.save(membership);
        Notification notification = notificationRepository.save(new Notification(
                member.getId(), null, patient.getId(), NotificationType.MEMBER_REMOVED,
                "Care Circle access revoked", "Your access was revoked.",
                patient.getId(), "patient", "membership:test:member:" + member.getId()));

        mockMvc.perform(get("/api/notifications")
                        .header("Authorization", "Bearer " + memberToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));

        mockMvc.perform(patch("/api/notifications/{id}/read", notification.getId())
                        .header("Authorization", "Bearer " + memberToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.readAt").isNotEmpty());

        Notification updated = notificationRepository.findById(notification.getId()).orElseThrow();
        assertThat(updated.getReadAt()).isNotNull();
    }

    @Test
    void markAllAndDeleteAreScopedToAuthenticatedRecipient() throws Exception {
        Notification owned = createNotification(member.getId(), patient.getId(), NotificationType.MEMBER_REMOVED);
        createNotification(patient.getId(), patient.getId(), NotificationType.MEMBER_JOINED);

        mockMvc.perform(patch("/api/notifications/read-all")
                        .header("Authorization", "Bearer " + memberToken))
                .andExpect(status().isNoContent());
        assertThat(notificationRepository.findById(owned.getId()).orElseThrow().getReadAt()).isNotNull();

        mockMvc.perform(delete("/api/notifications/{id}", owned.getId())
                        .header("Authorization", "Bearer " + memberToken))
                .andExpect(status().isNoContent());
        assertThat(notificationRepository.findById(owned.getId())).isEmpty();
    }

    @Test
    void invitationRedemptionUsesPatientManagerMemberJoinedAndSeparateInviterType() throws Exception {
        User inviter = createUser("Separate Inviter", "separate-inviter@example.com");
        createInvitation(inviter.getId(), "separate-inviter-token");

        redeem("separate-inviter-token", outsiderToken);

        Notification patientNotification = notificationRepository.findAll().stream()
                .filter(notification -> notification.getRecipientUserId().equals(patient.getId()))
                .findFirst().orElseThrow();
        Notification managerNotification = notificationRepository.findAll().stream()
                .filter(notification -> notification.getRecipientUserId().equals(member.getId()))
                .findFirst().orElseThrow();
        Notification inviterNotification = notificationRepository.findAll().stream()
                .filter(notification -> notification.getRecipientUserId().equals(inviter.getId()))
                .findFirst().orElseThrow();

        assertThat(notificationRepository.findAll()).hasSize(3);
        assertThat(patientNotification.getType()).isEqualTo(NotificationType.MEMBER_JOINED);
        assertThat(managerNotification.getType()).isEqualTo(NotificationType.MEMBER_JOINED);
        assertThat(inviterNotification.getType()).isEqualTo(NotificationType.INVITATION_REDEEMED);
        assertThat(notificationRepository.findAll())
                .noneMatch(notification -> notification.getRecipientUserId().equals(outsider.getId()));
    }

    @Test
    void invitationRedemptionDeduplicatesAndOverridesManagerInviterType() throws Exception {
        createInvitation(member.getId(), "manager-inviter-token");

        redeem("manager-inviter-token", outsiderToken);

        Notification patientNotification = notificationRepository.findAll().stream()
                .filter(notification -> notification.getRecipientUserId().equals(patient.getId()))
                .findFirst().orElseThrow();
        Notification managerNotification = notificationRepository.findAll().stream()
                .filter(notification -> notification.getRecipientUserId().equals(member.getId()))
                .findFirst().orElseThrow();

        assertThat(notificationRepository.findAll()).hasSize(2);
        assertThat(patientNotification.getType()).isEqualTo(NotificationType.MEMBER_JOINED);
        assertThat(managerNotification.getType()).isEqualTo(NotificationType.INVITATION_REDEEMED);
        assertThat(notificationRepository.findAll().stream()
                .filter(notification -> notification.getRecipientUserId().equals(member.getId()))
                .count()).isEqualTo(1);
    }

    @Test
    void invitationRedemptionDeduplicatesPatientInviterAndUsesInviterType() throws Exception {
        createInvitation(patient.getId(), "patient-inviter-token");

        redeem("patient-inviter-token", outsiderToken);

        Notification patientNotification = notificationRepository.findAll().stream()
                .filter(notification -> notification.getRecipientUserId().equals(patient.getId()))
                .findFirst().orElseThrow();
        Notification managerNotification = notificationRepository.findAll().stream()
                .filter(notification -> notification.getRecipientUserId().equals(member.getId()))
                .findFirst().orElseThrow();

        assertThat(notificationRepository.findAll()).hasSize(2);
        assertThat(patientNotification.getType()).isEqualTo(NotificationType.INVITATION_REDEEMED);
        assertThat(managerNotification.getType()).isEqualTo(NotificationType.MEMBER_JOINED);
        assertThat(notificationRepository.findAll().stream()
                .filter(notification -> notification.getRecipientUserId().equals(patient.getId()))
                .count()).isEqualTo(1);
    }

    @Test
    void missingInviterAccountDoesNotReceiveOrCreateExtraNotification() throws Exception {
        createInvitation(UUID.randomUUID(), "missing-inviter-token");

        redeem("missing-inviter-token", outsiderToken);

        assertThat(notificationRepository.findAll()).hasSize(2);
        assertThat(notificationRepository.findAll())
                .extracting(Notification::getRecipientUserId)
                .containsExactlyInAnyOrder(patient.getId(), member.getId());
        assertThat(notificationRepository.findAll())
                .allMatch(notification -> notification.getType() == NotificationType.MEMBER_JOINED);
    }
}
