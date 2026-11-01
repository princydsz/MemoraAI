package com.memoraai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.memoraai.dto.CreateInvitationRequest;
import com.memoraai.dto.RedeemInvitationRequest;
import com.memoraai.dto.UpdateMemberRequest;
import com.memoraai.entity.CareCircleInvitation;
import com.memoraai.entity.CareCircleMembership;
import com.memoraai.entity.InvitationStatus;
import com.memoraai.entity.MembershipStatus;
import com.memoraai.entity.PermissionLevel;
import com.memoraai.entity.RelationshipType;
import com.memoraai.entity.Role;
import com.memoraai.entity.User;
import com.memoraai.repository.CareCircleInvitationRepository;
import com.memoraai.repository.CareCircleMembershipRepository;
import com.memoraai.repository.MemoryMediaRepository;
import com.memoraai.repository.MemoryRepository;
import com.memoraai.repository.UserRepository;
import com.memoraai.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class FamilyPhase2IntegrationTest {
    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired UserRepository userRepository;
    @Autowired CareCircleMembershipRepository membershipRepository;
    @Autowired CareCircleInvitationRepository invitationRepository;
    @Autowired MemoryMediaRepository memoryMediaRepository;
    @Autowired MemoryRepository memoryRepository;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired JwtService jwtService;

    private User patient;
    private User familyOne;
    private User familyTwo;
    private String patientToken;
    private String familyOneToken;
    private String familyTwoToken;

    @BeforeEach
    void setUp() {
        memoryMediaRepository.deleteAll();
        memoryRepository.deleteAll();
        invitationRepository.deleteAll();
        membershipRepository.deleteAll();
        userRepository.deleteAll();
        patient = createUser("Patient", "patient@example.com");
        familyOne = createUser("Family One", "family1@example.com");
        familyTwo = createUser("Family Two", "family2@example.com");
        patientToken = jwtService.generateToken(patient);
        familyOneToken = jwtService.generateToken(familyOne);
        familyTwoToken = jwtService.generateToken(familyTwo);
    }

    private User createUser(String name, String email) {
        return userRepository.save(new User(name, email, passwordEncoder.encode("Password123!"), Role.USER));
    }

    private String hash(String token) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
        StringBuilder result = new StringBuilder();
        for (byte value : digest) result.append(String.format("%02x", value));
        return result.toString();
    }

    @Test
    void patientCanCreateInvitationAndTokenIsStoredOnlyAsHash() throws Exception {
        CreateInvitationRequest request = new CreateInvitationRequest();
        request.setRelationshipType(RelationshipType.CHILD);
        request.setPermissionLevel(PermissionLevel.CONTRIBUTE);
        request.setIntendedEmail("FAMILY2@EXAMPLE.COM");

        MvcResult result = mockMvc.perform(post("/api/family/patients/{patientId}/invitations", patient.getId())
                        .header("Authorization", "Bearer " + patientToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andReturn();

        String body = result.getResponse().getContentAsString();
        String token = objectMapper.readTree(body).get("token").asText();
        CareCircleInvitation saved = invitationRepository.findAll().get(0);
        assertThat(saved.getTokenHash()).isEqualTo(hash(token));
        assertThat(saved.getTokenHash()).doesNotContain(token);
        assertThat(saved.getIntendedEmail()).isEqualTo("family2@example.com");
        assertThat(saved.getExpiresAt()).isEqualTo(saved.getCreatedAt().plusSeconds(7L * 24 * 60 * 60));
    }

    @Test
    void fullAccessMemberCanInviteButViewOnlyCannot() throws Exception {
        membershipRepository.save(new CareCircleMembership(patient.getId(), familyOne.getId(),
                RelationshipType.CHILD, PermissionLevel.FULL_ACCESS, MembershipStatus.ACTIVE));

        CreateInvitationRequest request = new CreateInvitationRequest();
        request.setRelationshipType(RelationshipType.SIBLING);
        request.setPermissionLevel(PermissionLevel.FULL_ACCESS);

        mockMvc.perform(post("/api/family/patients/{patientId}/invitations", patient.getId())
                        .header("Authorization", "Bearer " + familyOneToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());

        membershipRepository.deleteAll();
        membershipRepository.save(new CareCircleMembership(patient.getId(), familyTwo.getId(),
                RelationshipType.CHILD, PermissionLevel.VIEW_ONLY, MembershipStatus.ACTIVE));
        mockMvc.perform(post("/api/family/patients/{patientId}/invitations", patient.getId())
                        .header("Authorization", "Bearer " + familyTwoToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    void memberCannotEscalatePermission() throws Exception {
        membershipRepository.save(new CareCircleMembership(patient.getId(), familyOne.getId(),
                RelationshipType.CHILD, PermissionLevel.CONTRIBUTE, MembershipStatus.ACTIVE));
        CreateInvitationRequest request = new CreateInvitationRequest();
        request.setRelationshipType(RelationshipType.CHILD);
        request.setPermissionLevel(PermissionLevel.FULL_ACCESS);

        mockMvc.perform(post("/api/family/patients/{patientId}/invitations", patient.getId())
                        .header("Authorization", "Bearer " + familyOneToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    void redeemCreatesMembershipAndPreventsReuse() throws Exception {
        CareCircleInvitation invitation = new CareCircleInvitation(
                patient.getId(), patient.getId(), "family2@example.com", hash("test-token"),
                PermissionLevel.CONTRIBUTE, RelationshipType.CHILD, Instant.now().plusSeconds(86400));
        invitationRepository.save(invitation);

        RedeemInvitationRequest request = new RedeemInvitationRequest();
        request.setToken("test-token");
        mockMvc.perform(post("/api/family/invitations/redeem")
                        .header("Authorization", "Bearer " + familyTwoToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        assertThat(membershipRepository.findAll()).hasSize(1);
        assertThat(invitationRepository.findById(invitation.getId()).orElseThrow().getStatus())
                .isEqualTo(InvitationStatus.REDEEMED);

        mockMvc.perform(post("/api/family/invitations/redeem")
                        .header("Authorization", "Bearer " + familyTwoToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict());
    }

    @Test
    void revokedExpiredUnknownAndEmailMismatchInvitationsAreRejected() throws Exception {
        CareCircleInvitation revoked = new CareCircleInvitation(
                patient.getId(), patient.getId(), null, hash("revoked-token"),
                PermissionLevel.VIEW_ONLY, RelationshipType.SIBLING, Instant.now().plusSeconds(86400));
        revoked.revoke(Instant.now());
        invitationRepository.save(revoked);

        CareCircleInvitation expired = new CareCircleInvitation(
                patient.getId(), patient.getId(), null, hash("expired-token"),
                PermissionLevel.VIEW_ONLY, RelationshipType.SIBLING, Instant.now().minusSeconds(1));
        invitationRepository.save(expired);

        RedeemInvitationRequest revokedRequest = new RedeemInvitationRequest();
        revokedRequest.setToken("revoked-token");
        mockMvc.perform(post("/api/family/invitations/redeem")
                        .header("Authorization", "Bearer " + familyTwoToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(revokedRequest)))
                .andExpect(status().isConflict());

        RedeemInvitationRequest expiredRequest = new RedeemInvitationRequest();
        expiredRequest.setToken("expired-token");
        mockMvc.perform(post("/api/family/invitations/redeem")
                        .header("Authorization", "Bearer " + familyTwoToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(expiredRequest)))
                .andExpect(status().isConflict());

        CareCircleInvitation bound = new CareCircleInvitation(
                patient.getId(), patient.getId(), "another@example.com", hash("bound-token"),
                PermissionLevel.VIEW_ONLY, RelationshipType.SIBLING, Instant.now().plusSeconds(86400));
        invitationRepository.save(bound);
        RedeemInvitationRequest boundRequest = new RedeemInvitationRequest();
        boundRequest.setToken("bound-token");
        mockMvc.perform(post("/api/family/invitations/redeem")
                        .header("Authorization", "Bearer " + familyTwoToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(boundRequest)))
                .andExpect(status().isConflict());

        RedeemInvitationRequest unknown = new RedeemInvitationRequest();
        unknown.setToken("unknown-token");
        mockMvc.perform(post("/api/family/invitations/redeem")
                        .header("Authorization", "Bearer " + familyTwoToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(unknown)))
                .andExpect(status().isNotFound());
    }

    @Test
    void revokedMembershipIsReactivatedWithoutDuplicate() throws Exception {
        CareCircleMembership membership = membershipRepository.save(new CareCircleMembership(
                patient.getId(), familyTwo.getId(), RelationshipType.SIBLING,
                PermissionLevel.VIEW_ONLY, MembershipStatus.REVOKED));
        CareCircleInvitation invitation = invitationRepository.save(new CareCircleInvitation(
                patient.getId(), patient.getId(), null, hash("reactivate-token"),
                PermissionLevel.FULL_ACCESS, RelationshipType.CHILD, Instant.now().plusSeconds(86400)));
        RedeemInvitationRequest request = new RedeemInvitationRequest();
        request.setToken("reactivate-token");

        mockMvc.perform(post("/api/family/invitations/redeem")
                        .header("Authorization", "Bearer " + familyTwoToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        assertThat(membershipRepository.findAll()).hasSize(1);
        CareCircleMembership reactivated = membershipRepository.findAll().get(0);
        assertThat(reactivated.getId()).isEqualTo(membership.getId());
        assertThat(reactivated.getStatus()).isEqualTo(MembershipStatus.ACTIVE);
        assertThat(reactivated.getPermissionLevel()).isEqualTo(PermissionLevel.FULL_ACCESS);
        assertThat(invitation.getStatus()).isEqualTo(InvitationStatus.PENDING);
        assertThat(invitationRepository.findById(invitation.getId()).orElseThrow().getStatus())
                .isEqualTo(InvitationStatus.REDEEMED);
    }

    @Test
    void patientAndFullAccessCanManageMembers() throws Exception {
        CareCircleMembership full = membershipRepository.save(new CareCircleMembership(
                patient.getId(), familyOne.getId(), RelationshipType.CHILD,
                PermissionLevel.FULL_ACCESS, MembershipStatus.ACTIVE));

        mockMvc.perform(get("/api/family/patients/{patientId}/members", patient.getId())
                        .header("Authorization", "Bearer " + familyOneToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].memberId").exists())
                .andExpect(jsonPath("$[0].password").doesNotExist());

        UpdateMemberRequest update = new UpdateMemberRequest();
        update.setPermissionLevel(PermissionLevel.CONTRIBUTE);
        mockMvc.perform(patch("/api/family/patients/{patientId}/members/{memberId}",
                        patient.getId(), familyOne.getId())
                        .header("Authorization", "Bearer " + patientToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(update)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.permissionLevel").value("CONTRIBUTE"));

        assertThat(membershipRepository.findById(full.getId()).orElseThrow().getPermissionLevel())
                .isEqualTo(PermissionLevel.CONTRIBUTE);
    }

    @Test
    void unauthorizedListingAndSelfPromotionAreRejected() throws Exception {
        membershipRepository.save(new CareCircleMembership(patient.getId(), familyOne.getId(),
                RelationshipType.CHILD, PermissionLevel.VIEW_ONLY, MembershipStatus.ACTIVE));

        mockMvc.perform(get("/api/family/patients/{patientId}/members", patient.getId())
                        .header("Authorization", "Bearer " + familyTwoToken))
                .andExpect(status().isNotFound());

        UpdateMemberRequest update = new UpdateMemberRequest();
        update.setPermissionLevel(PermissionLevel.FULL_ACCESS);
        mockMvc.perform(patch("/api/family/patients/{patientId}/members/{memberId}",
                        patient.getId(), familyOne.getId())
                        .header("Authorization", "Bearer " + familyOneToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(update)))
                .andExpect(status().isForbidden());
    }

    @Test
    void revokeMemberAndInvitationTakeEffect() throws Exception {
        membershipRepository.save(new CareCircleMembership(patient.getId(), familyOne.getId(),
                RelationshipType.CHILD, PermissionLevel.CONTRIBUTE, MembershipStatus.ACTIVE));
        CareCircleInvitation invitation = invitationRepository.save(new CareCircleInvitation(
                patient.getId(), patient.getId(), null, hash("revoke-invitation-token"),
                PermissionLevel.VIEW_ONLY, RelationshipType.SIBLING, Instant.now().plusSeconds(86400)));

        mockMvc.perform(delete("/api/family/patients/{patientId}/members/{memberId}",
                        patient.getId(), familyOne.getId())
                        .header("Authorization", "Bearer " + patientToken))
                .andExpect(status().isNoContent());
        assertThat(membershipRepository.findAll().get(0).getStatus())
                .isEqualTo(MembershipStatus.REVOKED);

        mockMvc.perform(delete("/api/family/patients/{patientId}/invitations/{invitationId}",
                        patient.getId(), invitation.getId())
                        .header("Authorization", "Bearer " + patientToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REVOKED"));

        RedeemInvitationRequest request = new RedeemInvitationRequest();
        request.setToken("revoke-invitation-token");
        mockMvc.perform(post("/api/family/invitations/redeem")
                        .header("Authorization", "Bearer " + familyTwoToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict());
    }
}
