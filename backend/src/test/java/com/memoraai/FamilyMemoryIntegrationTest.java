package com.memoraai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.memoraai.dto.CreateMemoryRequest;
import com.memoraai.dto.UpdateMemoryRequest;
import com.memoraai.entity.*;
import com.memoraai.repository.*;
import com.memoraai.security.JwtService;
import com.memoraai.service.StorageService;
import com.memoraai.service.VectorStoreService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
public class FamilyMemoryIntegrationTest {
    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserRepository userRepository;
    @Autowired private MemoryRepository memoryRepository;
    @Autowired private MemoryMediaRepository memoryMediaRepository;
    @Autowired private CareCircleMembershipRepository membershipRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private JwtService jwtService;

    @MockBean private VectorStoreService vectorStoreService;
    @MockBean private StorageService storageService;

    private User patient, contributeMember, viewMember, fullMember, outsider, admin;
    private String patientToken, contributeToken, viewToken, fullToken, outsiderToken, adminToken;

    @BeforeEach
    void setUp() {
        memoryMediaRepository.deleteAll();
        memoryRepository.deleteAll();
        membershipRepository.deleteAll();
        userRepository.deleteAll();

        patient = saveUser("Patient", "phase6.patient@example.com", Role.USER);
        contributeMember = saveUser("Contributor", "phase6.contribute@example.com", Role.USER);
        viewMember = saveUser("Viewer", "phase6.view@example.com", Role.USER);
        fullMember = saveUser("Full Member", "phase6.full@example.com", Role.USER);
        outsider = saveUser("Outsider", "phase6.outsider@example.com", Role.USER);
        admin = saveUser("Admin", "phase6.admin@example.com", Role.ADMIN);

        membershipRepository.save(new CareCircleMembership(
                patient.getId(), contributeMember.getId(), RelationshipType.CHILD,
                PermissionLevel.CONTRIBUTE, MembershipStatus.ACTIVE));
        membershipRepository.save(new CareCircleMembership(
                patient.getId(), viewMember.getId(), RelationshipType.CHILD,
                PermissionLevel.VIEW_ONLY, MembershipStatus.ACTIVE));
        membershipRepository.save(new CareCircleMembership(
                patient.getId(), fullMember.getId(), RelationshipType.CHILD,
                PermissionLevel.FULL_ACCESS, MembershipStatus.ACTIVE));

        patientToken = jwtService.generateToken(patient);
        contributeToken = jwtService.generateToken(contributeMember);
        viewToken = jwtService.generateToken(viewMember);
        fullToken = jwtService.generateToken(fullMember);
        outsiderToken = jwtService.generateToken(outsider);
        adminToken = jwtService.generateToken(admin);
    }

    private User saveUser(String name, String email, Role role) {
        return userRepository.save(new User(
                name, email, passwordEncoder.encode("Password123!"), role));
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }

    @Test
    void familyContributeAndRetrieveWithContributorAttribution() throws Exception {
        CreateMemoryRequest request = new CreateMemoryRequest(
                "Family story", "Grandma told this story.", "Family", "story");

        mockMvc.perform(post("/api/family/patients/" + patient.getId() + "/memories")
                        .header("Authorization", bearer(contributeToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value("Family story"))
                .andExpect(jsonPath("$.contributor.id").value(contributeMember.getId().toString()))
                .andExpect(jsonPath("$.contributor.name").value("Contributor"));

        mockMvc.perform(get("/api/family/patients/" + patient.getId() + "/memories")
                        .header("Authorization", bearer(viewToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].contributor.id")
                        .value(contributeMember.getId().toString()));
    }

    @Test
    void patientCreatesAndViewOnlyCannotContribute() throws Exception {
        CreateMemoryRequest request = new CreateMemoryRequest(
                "My memory", "Patient authored.", null, null);

        mockMvc.perform(post("/api/family/patients/" + patient.getId() + "/memories")
                        .header("Authorization", bearer(patientToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.contributor").doesNotExist());

        mockMvc.perform(post("/api/family/patients/" + patient.getId() + "/memories")
                        .header("Authorization", bearer(viewToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    void familyCannotEditOrDeletePatientAuthoredMemory() throws Exception {
        Memory memory = memoryRepository.save(
                new Memory(patient, "Patient title", "Patient content", null, null));

        UpdateMemoryRequest update = new UpdateMemoryRequest(
                "Changed", "Changed content", null, null);
        mockMvc.perform(patch("/api/family/patients/" + patient.getId() + "/memories/" + memory.getId())
                        .header("Authorization", bearer(fullToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(update)))
                .andExpect(status().isForbidden());

        mockMvc.perform(delete("/api/family/patients/" + patient.getId() + "/memories/" + memory.getId())
                        .header("Authorization", bearer(fullToken)))
                .andExpect(status().isForbidden());
        assertThat(memoryRepository.findById(memory.getId())).isPresent();
    }

    @Test
    void contributorCanEditAndDeleteOwnContribution() throws Exception {
        Memory memory = memoryRepository.save(new Memory(
                patient, contributeMember, "Original", "Original content", null, null));
        UpdateMemoryRequest update = new UpdateMemoryRequest(
                "Updated", "Updated content", null, null);

        mockMvc.perform(put("/api/family/patients/" + patient.getId() + "/memories/" + memory.getId())
                        .header("Authorization", bearer(contributeToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(update)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Updated"));

        mockMvc.perform(delete("/api/family/patients/" + patient.getId() + "/memories/" + memory.getId())
                        .header("Authorization", bearer(contributeToken)))
                .andExpect(status().isNoContent());
        assertThat(memoryRepository.findById(memory.getId())).isEmpty();
    }

    @Test
    void revokedOutsiderCrossPatientAndAdminCannotAccess() throws Exception {
        UUID otherPatientId = UUID.randomUUID();
        mockMvc.perform(get("/api/family/patients/" + otherPatientId + "/memories")
                        .header("Authorization", bearer(contributeToken)))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/family/patients/" + patient.getId() + "/memories")
                        .header("Authorization", bearer(outsiderToken)))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/family/patients/" + patient.getId() + "/memories")
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isNotFound());

        var membership = membershipRepository.findByPatientUserIdAndFamilyMemberUserId(
                patient.getId(), contributeMember.getId()).orElseThrow();
        membership.setStatus(MembershipStatus.REVOKED);
        membershipRepository.save(membership);
        mockMvc.perform(get("/api/family/patients/" + patient.getId() + "/memories")
                        .header("Authorization", bearer(contributeToken)))
                .andExpect(status().isNotFound());
    }

    @Test
    void invalidPaginationIsRejected() throws Exception {
        mockMvc.perform(get("/api/family/patients/" + patient.getId() + "/memories?page=-1")
                        .header("Authorization", bearer(viewToken)))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/family/patients/" + patient.getId() + "/memories?size=101")
                        .header("Authorization", bearer(viewToken)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void indexingFailureDoesNotRollBackContribution() throws Exception {
        doThrow(new RuntimeException("Qdrant unavailable"))
                .when(vectorStoreService).indexMemory(any(Memory.class));
        CreateMemoryRequest request = new CreateMemoryRequest(
                "Preserved contribution", "The database record must remain.", null, null);

        mockMvc.perform(post("/api/family/patients/" + patient.getId() + "/memories")
                        .header("Authorization", bearer(fullToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());

        assertThat(memoryRepository.findAllByUserIdOrderByCreatedAtDesc(patient.getId()))
                .hasSize(1);
    }

    @Test
    void familyMediaUploadIsScopedToOwnContribution() throws Exception {
        Memory contributed = memoryRepository.save(new Memory(
                patient, contributeMember, "Photo story", "Story content", null, null));
        MockMultipartFile file = new MockMultipartFile(
                "file", "photo.jpg", "image/jpeg",
                new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0, 0, 0, 0, 0, 0, 0, 0, 0});
        when(storageService.uploadMedia(any(UUID.class), any(UUID.class), any()))
                .thenReturn(new StorageService.UploadResult(
                        UUID.randomUUID(), "test/photo.jpg", "image/jpeg", "photo.jpg", file.getSize()));

        mockMvc.perform(multipart("/api/family/patients/" + patient.getId()
                        + "/memories/" + contributed.getId() + "/media")
                        .file(file)
                        .header("Authorization", bearer(contributeToken)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$[0].fileName").value("photo.jpg"));

        Memory patientMemory = memoryRepository.save(
                new Memory(patient, "Patient photo", "Patient content", null, null));
        mockMvc.perform(multipart("/api/family/patients/" + patient.getId()
                        + "/memories/" + patientMemory.getId() + "/media")
                        .file(file)
                        .header("Authorization", bearer(fullToken)))
                .andExpect(status().isForbidden());
    }
}
