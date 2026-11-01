package com.memoraai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.memoraai.dto.CreatePatientRequest;
import com.memoraai.entity.MembershipStatus;
import com.memoraai.entity.PermissionLevel;
import com.memoraai.entity.RelationshipType;
import com.memoraai.entity.Role;
import com.memoraai.entity.User;
import com.memoraai.repository.CareCircleMembershipRepository;
import com.memoraai.repository.UserRepository;
import com.memoraai.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class FamilyIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CareCircleMembershipRepository membershipRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtService jwtService;

    @SpyBean
    private CareCircleMembershipRepository membershipRepositorySpy;

    private User caregiver;
    private String caregiverToken;

    @BeforeEach
    void setUp() {
        reset(membershipRepositorySpy);
        membershipRepository.deleteAll();
        userRepository.deleteAll();
        caregiver = userRepository.save(new User(
                "Family Caregiver",
                "caregiver@example.com",
                passwordEncoder.encode("Password123!"),
                Role.USER
        ));
        caregiverToken = jwtService.generateToken(caregiver);
    }

    @Test
    void createsPatientAndActiveFullAccessMembershipWithoutSensitiveFields() throws Exception {
        CreatePatientRequest request =
                new CreatePatientRequest("  Alice Patient  ", RelationshipType.CHILD);

        mockMvc.perform(post("/api/family/patients")
                        .header("Authorization", "Bearer " + caregiverToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Alice Patient"))
                .andExpect(jsonPath("$.relationshipType").value("CHILD"))
                .andExpect(jsonPath("$.permissionLevel").value("FULL_ACCESS"))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.email").doesNotExist())
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.token").doesNotExist());
    }

    @Test
    void listingExcludesRevokedAndUnrelatedPatients() throws Exception {
        User patient1 = userRepository.save(new User(
                "Active Patient",
                "patient-active@patients.memoraai.invalid",
                passwordEncoder.encode("unknown"),
                Role.USER
        ));
        User patient2 = userRepository.save(new User(
                "Revoked Patient",
                "patient-revoked@patients.memoraai.invalid",
                passwordEncoder.encode("unknown"),
                Role.USER
        ));
        User unrelated = userRepository.save(new User(
                "Unrelated Patient",
                "patient-unrelated@patients.memoraai.invalid",
                passwordEncoder.encode("unknown"),
                Role.USER
        ));

        membershipRepository.save(new com.memoraai.entity.CareCircleMembership(
                patient1.getId(),
                caregiver.getId(),
                RelationshipType.SPOUSE,
                PermissionLevel.FULL_ACCESS,
                MembershipStatus.ACTIVE
        ));
        membershipRepository.save(new com.memoraai.entity.CareCircleMembership(
                patient2.getId(),
                caregiver.getId(),
                RelationshipType.CHILD,
                PermissionLevel.VIEW_ONLY,
                MembershipStatus.REVOKED
        ));

        mockMvc.perform(get("/api/family/patients")
                        .header("Authorization", "Bearer " + caregiverToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Active Patient"))
                .andExpect(jsonPath("$[0].email").doesNotExist())
                .andExpect(jsonPath("$[?(@.name == 'Revoked Patient')]").isEmpty())
                .andExpect(jsonPath("$[?(@.name == 'Unrelated Patient')]").isEmpty());
    }

    @Test
    void rejectsInvalidPatientRequest() throws Exception {
        mockMvc.perform(post("/api/family/patients")
                        .header("Authorization", "Bearer " + caregiverToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CreatePatientRequest("", null)
                        )))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void membershipFailureRollsBackPatientCreation() throws Exception {
        long usersBefore = userRepository.count();
        doThrow(new RuntimeException("forced membership persistence failure"))
                .when(membershipRepositorySpy).save(any());

        mockMvc.perform(post("/api/family/patients")
                        .header("Authorization", "Bearer " + caregiverToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CreatePatientRequest("Rollback Patient", RelationshipType.CAREGIVER)
                        )))
                .andExpect(status().isInternalServerError());

        org.assertj.core.api.Assertions.assertThat(userRepository.count()).isEqualTo(usersBefore);
    }
}
