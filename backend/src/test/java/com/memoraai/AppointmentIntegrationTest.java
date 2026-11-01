package com.memoraai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.memoraai.dto.CreateAppointmentRequest;
import com.memoraai.dto.UpdateAppointmentRequest;
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

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class AppointmentIntegrationTest {
    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired UserRepository userRepository;
    @Autowired CareCircleMembershipRepository membershipRepository;
    @Autowired AppointmentRepository appointmentRepository;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired JwtService jwtService;

    private User patient, fullMember, contributeMember, viewMember, outsider, admin;
    private String patientToken, fullToken, contributeToken, viewToken, outsiderToken, adminToken;

    @BeforeEach
    void setUp() {
        appointmentRepository.deleteAll();
        membershipRepository.deleteAll();
        userRepository.deleteAll();
        patient = user("Patient", "appointment-patient@example.com", Role.USER);
        fullMember = user("Full", "appointment-full@example.com", Role.USER);
        contributeMember = user("Contribute", "appointment-contribute@example.com", Role.USER);
        viewMember = user("View", "appointment-view@example.com", Role.USER);
        outsider = user("Outsider", "appointment-outsider@example.com", Role.USER);
        admin = user("Admin", "appointment-admin@example.com", Role.ADMIN);
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

    private CreateAppointmentRequest createRequest(String title) {
        CreateAppointmentRequest request = new CreateAppointmentRequest();
        request.setTitle(title);
        request.setDescription("Routine checkup");
        request.setAppointmentDate(LocalDate.of(2099, 6, 15));
        request.setStartTime(LocalTime.of(10, 0));
        request.setEndTime(LocalTime.of(11, 0));
        request.setLocation("Main Clinic");
        request.setProviderName("Dr. Rao");
        return request;
    }

    private PatientAppointment appointment(String title, LocalDate date, AppointmentStatus status) {
        return appointmentRepository.save(new PatientAppointment(
                patient.getId(), title, null, date, LocalTime.of(9, 0),
                LocalTime.of(10, 0), null, null, status, patient.getId()));
    }

    @Test
    void patientAndFullAccessCanCreateRetrieveUpdateAndCancel() throws Exception {
        CreateAppointmentRequest request = createRequest("Cardiology");
        String response = mockMvc.perform(post("/api/family/patients/{id}/appointments", patient.getId())
                        .header("Authorization", "Bearer " + fullToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value("Cardiology"))
                .andExpect(jsonPath("$.status").value("SCHEDULED"))
                .andExpect(jsonPath("$.createdBy.name").value("Full"))
                .andReturn().getResponse().getContentAsString();
        UUID id = UUID.fromString(objectMapper.readTree(response).get("id").asText());

        UpdateAppointmentRequest update = new UpdateAppointmentRequest();
        update.setDescription(null);
        update.setLocation("City Hospital");
        mockMvc.perform(patch("/api/family/patients/{patientId}/appointments/{appointmentId}",
                        patient.getId(), id)
                        .header("Authorization", "Bearer " + patientToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(update)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.description").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.location").value("City Hospital"));

        mockMvc.perform(post("/api/family/patients/{patientId}/appointments/{appointmentId}/cancel",
                        patient.getId(), id)
                        .header("Authorization", "Bearer " + fullToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
        mockMvc.perform(post("/api/family/patients/{patientId}/appointments/{appointmentId}/cancel",
                        patient.getId(), id)
                        .header("Authorization", "Bearer " + fullToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
    }

    @Test
    void viewAndContributeCanViewButCannotManage() throws Exception {
        PatientAppointment appointment = appointment("Review", LocalDate.of(2099, 1, 1), AppointmentStatus.SCHEDULED);
        String base = "/api/family/patients/" + patient.getId() + "/appointments";

        mockMvc.perform(get(base).header("Authorization", "Bearer " + viewToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));
        mockMvc.perform(get(base).header("Authorization", "Bearer " + contributeToken))
                .andExpect(status().isOk());
        mockMvc.perform(post(base).header("Authorization", "Bearer " + viewToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("Denied"))))
                .andExpect(status().isForbidden());
        mockMvc.perform(post(base).header("Authorization", "Bearer " + contributeToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest("Denied"))))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete(base + "/" + appointment.getId())
                        .header("Authorization", "Bearer " + contributeToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void filtersUpcomingPastStatusDateRangeAndPagination() throws Exception {
        appointment("Past", LocalDate.of(2020, 1, 1), AppointmentStatus.COMPLETED);
        appointment("Upcoming scheduled", LocalDate.of(2099, 6, 1), AppointmentStatus.SCHEDULED);
        appointment("Upcoming cancelled", LocalDate.of(2099, 7, 1), AppointmentStatus.CANCELLED);
        String base = "/api/family/patients/" + patient.getId() + "/appointments";

        mockMvc.perform(get(base + "/past").header("Authorization", "Bearer " + viewToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));
        mockMvc.perform(get(base + "/upcoming")
                        .param("status", "SCHEDULED")
                        .header("Authorization", "Bearer " + viewToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));
        mockMvc.perform(get(base)
                        .param("from", "2099-01-01").param("to", "2099-12-31")
                        .param("page", "0").param("size", "1")
                        .header("Authorization", "Bearer " + viewToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pageSize").value(1))
                .andExpect(jsonPath("$.totalElements").value(2));
    }

    @Test
    void invalidRequestsAndStatusTransitionsAreRejected() throws Exception {
        String base = "/api/family/patients/" + patient.getId() + "/appointments";
        CreateAppointmentRequest invalidTime = createRequest("Invalid");
        invalidTime.setEndTime(LocalTime.of(9, 0));
        mockMvc.perform(post(base).header("Authorization", "Bearer " + patientToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidTime)))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post(base).header("Authorization", "Bearer " + patientToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Bad\",\"appointmentDate\":\"2099-01-01\",\"startTime\":\"10:00\",\"status\":\"MIDNIGHT\"}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get(base).param("page", "-1")
                        .header("Authorization", "Bearer " + viewToken))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get(base).param("size", "101")
                        .header("Authorization", "Bearer " + viewToken))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get(base).param("from", "2099-02-01").param("to", "2099-01-01")
                        .header("Authorization", "Bearer " + viewToken))
                .andExpect(status().isBadRequest());

        PatientAppointment completed = appointment("Completed", LocalDate.of(2099, 1, 1), AppointmentStatus.COMPLETED);
        UpdateAppointmentRequest invalidStatus = new UpdateAppointmentRequest();
        invalidStatus.setStatus(AppointmentStatus.CANCELLED);
        mockMvc.perform(patch(base + "/" + completed.getId())
                        .header("Authorization", "Bearer " + patientToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidStatus)))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post(base + "/" + completed.getId() + "/cancel")
                        .header("Authorization", "Bearer " + patientToken))
                .andExpect(status().isBadRequest());
    }

    @Test
    void revokedOutsiderCrossPatientAndAdminLoseAccess() throws Exception {
        CareCircleMembership revoked = membershipRepository
                .findByPatientUserIdAndFamilyMemberUserId(patient.getId(), contributeMember.getId())
                .orElseThrow();
        revoked.setStatus(MembershipStatus.REVOKED);
        membershipRepository.save(revoked);
        String base = "/api/family/patients/" + patient.getId() + "/appointments";

        mockMvc.perform(get(base).header("Authorization", "Bearer " + contributeToken))
                .andExpect(status().isNotFound());
        mockMvc.perform(get(base).header("Authorization", "Bearer " + outsiderToken))
                .andExpect(status().isNotFound());
        mockMvc.perform(get(base).header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNotFound());
        User otherPatient = user("Other", "appointment-other@example.com", Role.USER);
        mockMvc.perform(get("/api/family/patients/" + otherPatient.getId() + "/appointments")
                        .header("Authorization", "Bearer " + patientToken))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/family/patients/" + otherPatient.getId() + "/appointments")
                        .header("Authorization", "Bearer " + outsiderToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void patientSelfAccessWorksAndPatientIsolationIsPreserved() throws Exception {
        PatientAppointment own = appointment("Own", LocalDate.of(2099, 1, 1), AppointmentStatus.SCHEDULED);
        User otherPatient = user("Other", "appointment-other@example.com", Role.USER);
        PatientAppointment other = appointmentRepository.save(new PatientAppointment(
                otherPatient.getId(), "Other", null, LocalDate.of(2099, 1, 1),
                LocalTime.of(9, 0), null, null, null, AppointmentStatus.SCHEDULED,
                otherPatient.getId()));

        mockMvc.perform(get("/api/family/patients/{patientId}/appointments/{appointmentId}",
                        patient.getId(), own.getId())
                        .header("Authorization", "Bearer " + patientToken))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/family/patients/{patientId}/appointments/{appointmentId}",
                        patient.getId(), other.getId())
                        .header("Authorization", "Bearer " + patientToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void omittedUpdateFieldsAndStatusCompletionArePreserved() throws Exception {
        PatientAppointment appointment = appointment("Keep", LocalDate.of(2099, 2, 1), AppointmentStatus.SCHEDULED);
        UpdateAppointmentRequest update = new UpdateAppointmentRequest();
        update.setStatus(AppointmentStatus.COMPLETED);
        mockMvc.perform(patch("/api/family/patients/{patientId}/appointments/{appointmentId}",
                        patient.getId(), appointment.getId())
                        .header("Authorization", "Bearer " + patientToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(update)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Keep"))
                .andExpect(jsonPath("$.status").value("COMPLETED"));
    }
}
