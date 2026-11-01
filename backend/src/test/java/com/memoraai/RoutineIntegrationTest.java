package com.memoraai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.memoraai.dto.CreateRoutineActivityRequest;
import com.memoraai.dto.UpdateRoutineActivityRequest;
import com.memoraai.entity.*;
import com.memoraai.repository.*;
import com.memoraai.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class RoutineIntegrationTest {
    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired UserRepository userRepository;
    @Autowired CareCircleMembershipRepository membershipRepository;
    @Autowired RoutineActivityCompletionRepository completionRepository;
    @Autowired RoutineActivityRepository activityRepository;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired JwtService jwtService;

    private User patient, fullMember, contributeMember, viewMember, outsider, admin;
    private String patientToken, fullToken, contributeToken, viewToken, outsiderToken, adminToken;

    @BeforeEach
    void setUp() {
        completionRepository.deleteAll();
        activityRepository.deleteAll();
        membershipRepository.deleteAll();
        userRepository.deleteAll();
        patient = user("Patient", "routine-patient@example.com", Role.USER);
        fullMember = user("Full", "routine-full@example.com", Role.USER);
        contributeMember = user("Contribute", "routine-contribute@example.com", Role.USER);
        viewMember = user("View", "routine-view@example.com", Role.USER);
        outsider = user("Outsider", "routine-outsider@example.com", Role.USER);
        admin = user("Admin", "routine-admin@example.com", Role.ADMIN);
        membership(patient, fullMember, PermissionLevel.FULL_ACCESS);
        membership(patient, contributeMember, PermissionLevel.CONTRIBUTE);
        membership(patient, viewMember, PermissionLevel.VIEW_ONLY);
        patientToken = jwtService.generateToken(patient);
        fullToken = jwtService.generateToken(fullMember);
        contributeToken = jwtService.generateToken(contributeMember);
        viewToken = jwtService.generateToken(viewMember);
        outsiderToken = jwtService.generateToken(outsider);
        adminToken = jwtService.generateToken(admin);
    }

    private User user(String name, String email, Role role) {
        return userRepository.save(new User(name, email,
                passwordEncoder.encode("Password123!"), role));
    }

    private void membership(User patientUser, User member, PermissionLevel level) {
        membershipRepository.save(new CareCircleMembership(
                patientUser.getId(), member.getId(), RelationshipType.CAREGIVER,
                level, MembershipStatus.ACTIVE));
    }

    private CreateRoutineActivityRequest createRequest(String title, RoutinePeriod period,
                                                       LocalTime time, int order) {
        CreateRoutineActivityRequest request = new CreateRoutineActivityRequest();
        request.setTitle(title);
        request.setDescription("Daily task");
        request.setPeriod(period);
        request.setScheduledTime(time);
        request.setSortOrder(order);
        return request;
    }

    @Test
    void createUpdateArchiveReactivateAndRetrieveGroupedSortedRoutine() throws Exception {
        CreateRoutineActivityRequest afternoon = createRequest(
                "Lunch", RoutinePeriod.AFTERNOON, LocalTime.of(13, 0), 2);
        CreateRoutineActivityRequest morning = createRequest(
                "Breakfast", RoutinePeriod.MORNING, LocalTime.of(8, 0), 1);

        mockMvc.perform(post("/api/family/patients/{id}/routines", patient.getId())
                        .header("Authorization", "Bearer " + fullToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(afternoon)))
                .andExpect(status().isCreated());
        String morningJson = objectMapper.writeValueAsString(morning);
        String response = mockMvc.perform(post("/api/family/patients/{id}/routines/activities", patient.getId())
                        .header("Authorization", "Bearer " + patientToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(morningJson))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        UUID morningId = UUID.fromString(objectMapper.readTree(response).get("id").asText());

        UpdateRoutineActivityRequest update = new UpdateRoutineActivityRequest();
        update.setTitle("Updated breakfast");
        update.setSortOrder(0);
        mockMvc.perform(patch("/api/family/patients/{patientId}/routines/activities/{activityId}",
                        patient.getId(), morningId)
                        .header("Authorization", "Bearer " + fullToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(update)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Updated breakfast"));

        mockMvc.perform(delete("/api/family/patients/{patientId}/routines/activities/{activityId}",
                        patient.getId(), morningId)
                        .header("Authorization", "Bearer " + fullToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));

        mockMvc.perform(post("/api/family/patients/{patientId}/routines/activities/{activityId}/reactivate",
                        patient.getId(), morningId)
                        .header("Authorization", "Bearer " + patientToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(true));

        mockMvc.perform(get("/api/family/patients/{id}/routines", patient.getId())
                        .param("date", "2026-11-01")
                        .header("Authorization", "Bearer " + viewToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.date").value("2026-11-01"))
                .andExpect(jsonPath("$.morning.length()").value(1))
                .andExpect(jsonPath("$.afternoon.length()").value(1));
    }

    @Test
    void completeIsIdempotentUncompleteWorksAndHistoryIsPaginated() throws Exception {
        RoutineActivity activity = activityRepository.save(new RoutineActivity(
                patient.getId(), "Medication", null, RoutinePeriod.MORNING,
                LocalTime.of(9, 0), 0, patient.getId()));
        String url = "/api/family/patients/" + patient.getId()
                + "/routines/activities/" + activity.getId();

        mockMvc.perform(post(url + "/complete").param("date", "2026-11-01")
                        .header("Authorization", "Bearer " + contributeToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activityId").value(activity.getId().toString()))
                .andExpect(jsonPath("$.completionDate").value("2026-11-01"))
                .andExpect(jsonPath("$.completedBy.name").value("Contribute"));
        mockMvc.perform(post(url + "/complete").param("date", "2026-11-01")
                        .header("Authorization", "Bearer " + contributeToken))
                .andExpect(status().isOk());
        assertEquals(1, completionRepository.count());
        mockMvc.perform(get(url + "/history")
                        .param("from", "2026-10-01")
                        .param("to", "2026-11-30")
                        .param("page", "0").param("size", "20")
                        .header("Authorization", "Bearer " + viewToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));
        mockMvc.perform(delete("/api/family/patients/{patientId}/routines/activities/{activityId}",
                        patient.getId(), activity.getId())
                        .header("Authorization", "Bearer " + fullToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));
        mockMvc.perform(get(url + "/history").header("Authorization", "Bearer " + viewToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));
        mockMvc.perform(delete(url + "/complete").param("date", "2026-11-01")
                        .header("Authorization", "Bearer " + patientToken))
                .andExpect(status().isNoContent());
        mockMvc.perform(get(url + "/history").header("Authorization", "Bearer " + viewToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void archivedActivitiesCannotBeCompletedAndCrossPatientAccessIsRejected() throws Exception {
        RoutineActivity archived = activityRepository.save(new RoutineActivity(
                patient.getId(), "Archived", null, RoutinePeriod.EVENING,
                null, 0, patient.getId()));
        archived.setActive(false);
        activityRepository.save(archived);
        User otherPatient = user("Other", "routine-other@example.com", Role.USER);
        RoutineActivity otherActivity = activityRepository.save(new RoutineActivity(
                otherPatient.getId(), "Other routine", null, RoutinePeriod.MORNING,
                null, 0, otherPatient.getId()));
        String base = "/api/family/patients/" + patient.getId() + "/routines/activities/";

        mockMvc.perform(post(base + archived.getId() + "/complete")
                        .header("Authorization", "Bearer " + contributeToken))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get(base + otherActivity.getId())
                        .header("Authorization", "Bearer " + patientToken))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/family/patients/" + otherPatient.getId() + "/routines")
                        .header("Authorization", "Bearer " + patientToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void viewContributeFullAndPatientPermissionsAreEnforced() throws Exception {
        CreateRoutineActivityRequest request = createRequest(
                "Test", RoutinePeriod.MORNING, null, 0);
        String createUrl = "/api/family/patients/" + patient.getId() + "/routines";

        mockMvc.perform(post(createUrl).header("Authorization", "Bearer " + viewToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
        mockMvc.perform(post(createUrl).header("Authorization", "Bearer " + contributeToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
        mockMvc.perform(post(createUrl).header("Authorization", "Bearer " + fullToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());
        mockMvc.perform(post(createUrl).header("Authorization", "Bearer " + patientToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());
    }

    @Test
    void revokedMembershipAndAdminWithoutMembershipLoseAccess() throws Exception {
        CareCircleMembership revoked = membershipRepository
                .findByPatientUserIdAndFamilyMemberUserId(patient.getId(), contributeMember.getId())
                .orElseThrow();
        revoked.setStatus(MembershipStatus.REVOKED);
        membershipRepository.save(revoked);
        String routineUrl = "/api/family/patients/" + patient.getId() + "/routines";

        mockMvc.perform(get(routineUrl).header("Authorization", "Bearer " + contributeToken))
                .andExpect(status().isNotFound());
        mockMvc.perform(get(routineUrl).header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void invalidRequestsReturnValidationErrors() throws Exception {
        CreateRoutineActivityRequest invalid = createRequest(
                " ", RoutinePeriod.MORNING, null, -1);
        mockMvc.perform(post("/api/family/patients/{id}/routines", patient.getId())
                        .header("Authorization", "Bearer " + patientToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalid)))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/family/patients/{id}/routines", patient.getId())
                        .header("Authorization", "Bearer " + patientToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Test\",\"period\":\"MIDNIGHT\",\"sortOrder\":0}"))
                .andExpect(status().isBadRequest());

        RoutineActivity activity = activityRepository.save(new RoutineActivity(
                patient.getId(), "Test history", null, RoutinePeriod.MORNING,
                null, 0, patient.getId()));
        String historyUrl = "/api/family/patients/" + patient.getId()
                + "/routines/activities/" + activity.getId() + "/history";
        mockMvc.perform(get(historyUrl).param("page", "-1")
                        .header("Authorization", "Bearer " + viewToken))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get(historyUrl).param("size", "101")
                        .header("Authorization", "Bearer " + viewToken))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get(historyUrl).param("from", "2026-11-02")
                        .param("to", "2026-11-01")
                        .header("Authorization", "Bearer " + viewToken))
                .andExpect(status().isBadRequest());
    }

    @Test
    void completeAndViewDateBehaviorAndInsufficientCompletionPermission() throws Exception {
        RoutineActivity activity = activityRepository.save(new RoutineActivity(
                patient.getId(), "Walk", null, RoutinePeriod.AFTERNOON,
                null, 0, patient.getId()));
        String base = "/api/family/patients/" + patient.getId()
                + "/routines/activities/" + activity.getId();

        mockMvc.perform(post(base + "/complete")
                        .header("Authorization", "Bearer " + viewToken))
                .andExpect(status().isForbidden());
        mockMvc.perform(post(base + "/complete")
                        .header("Authorization", "Bearer " + patientToken))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/family/patients/{id}/routines", patient.getId())
                        .param("date", "not-a-date")
                        .header("Authorization", "Bearer " + viewToken))
                .andExpect(status().isBadRequest());
    }
}
