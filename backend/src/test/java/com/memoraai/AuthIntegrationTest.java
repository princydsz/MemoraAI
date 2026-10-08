package com.memoraai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.memoraai.dto.LoginRequest;
import com.memoraai.dto.RegisterRequest;
import com.memoraai.entity.Role;
import com.memoraai.entity.User;
import com.memoraai.repository.UserRepository;
import com.memoraai.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
public class AuthIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtService jwtService;

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();
    }

    @Test
    @DisplayName("Health endpoint is publicly accessible")
    void testHealthEndpoint() throws Exception {
        mockMvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("UP")))
                .andExpect(jsonPath("$.service", is("MemoraAI Backend")));
    }

    @Test
    @DisplayName("User registration succeeds and returns JWT and user details with role USER")
    void testSuccessfulRegistration() throws Exception {
        RegisterRequest request = new RegisterRequest("Jane Doe", "jane.doe@example.com", "SecurePassword123!");

        MvcResult result = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.type", is("Bearer")))
                .andExpect(jsonPath("$.user.name", is("Jane Doe")))
                .andExpect(jsonPath("$.user.email", is("jane.doe@example.com")))
                .andExpect(jsonPath("$.user.role", is("USER")))
                .andExpect(jsonPath("$.user.id").isNotEmpty())
                .andReturn();

        // Verify password was hashed in the database and user has USER role
        Optional<User> savedUser = userRepository.findByEmail("jane.doe@example.com");
        assertThat(savedUser).isPresent();
        assertThat(savedUser.get().getRole()).isEqualTo(Role.USER);
        assertThat(savedUser.get().getPassword()).isNotEqualTo("SecurePassword123!");
        assertThat(passwordEncoder.matches("SecurePassword123!", savedUser.get().getPassword())).isTrue();
    }

    @Test
    @DisplayName("Malicious registration request with role ADMIN is forced to USER role")
    void testMaliciousRoleRegistration() throws Exception {
        String maliciousPayload = "{"
                + "\"name\":\"Attacker User\","
                + "\"email\":\"attacker@example.com\","
                + "\"password\":\"SecurePassword123!\","
                + "\"role\":\"ADMIN\""
                + "}";

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(maliciousPayload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.user.role", is("USER")));

        Optional<User> savedUser = userRepository.findByEmail("attacker@example.com");
        assertThat(savedUser).isPresent();
        assertThat(savedUser.get().getRole()).isEqualTo(Role.USER);
        assertThat(savedUser.get().getRole()).isNotEqualTo(Role.ADMIN);
    }

    @Test
    @DisplayName("Registration rejects duplicate email with 409 Conflict")
    void testDuplicateEmailRegistration() throws Exception {
        RegisterRequest request = new RegisterRequest("First User", "duplicate@example.com", "Password123!");

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());

        RegisterRequest duplicateRequest = new RegisterRequest("Second User", "duplicate@example.com", "Password456!");

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(duplicateRequest)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error", is("Conflict")))
                .andExpect(jsonPath("$.status", is(409)));
    }

    @Test
    @DisplayName("Registration rejects invalid input with 400 Bad Request")
    void testRegistrationValidation() throws Exception {
        RegisterRequest request = new RegisterRequest("", "invalid-email", "short");

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status", is(400)))
                .andExpect(jsonPath("$.error", is("Validation Failed")))
                .andExpect(jsonPath("$.fieldErrors.email").exists())
                .andExpect(jsonPath("$.fieldErrors.password").exists())
                .andExpect(jsonPath("$.fieldErrors.name").exists());
    }

    @Test
    @DisplayName("User login succeeds with correct credentials")
    void testSuccessfulLogin() throws Exception {
        // Register user first
        User user = new User("Alice Smith", "alice@example.com", passwordEncoder.encode("SecretAlice123"), Role.USER);
        userRepository.save(user);

        LoginRequest loginRequest = new LoginRequest("alice@example.com", "SecretAlice123");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.user.name", is("Alice Smith")))
                .andExpect(jsonPath("$.user.email", is("alice@example.com")));
    }

    @Test
    @DisplayName("User login fails with incorrect password (401 Unauthorized)")
    void testLoginInvalidPassword() throws Exception {
        User user = new User("Bob Smith", "bob@example.com", passwordEncoder.encode("CorrectPassword123"), Role.USER);
        userRepository.save(user);

        LoginRequest loginRequest = new LoginRequest("bob@example.com", "WrongPassword999");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status", is(401)))
                .andExpect(jsonPath("$.error", is("Unauthorized")));
    }

    @Test
    @DisplayName("Protected endpoint (/api/auth/me) rejects unauthenticated request")
    void testUnauthorizedAccess() throws Exception {
        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Protected endpoint (/api/auth/me) accepts valid JWT Bearer token")
    void testProtectedEndpointWithJwt() throws Exception {
        User user = new User("Admin User", "admin@example.com", passwordEncoder.encode("AdminPass123!"), Role.ADMIN);
        User savedUser = userRepository.save(user);

        String token = jwtService.generateToken(savedUser);

        mockMvc.perform(get("/api/auth/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.email", is("admin@example.com")))
                .andExpect(jsonPath("$.name", is("Admin User")))
                .andExpect(jsonPath("$.role", is("ADMIN")))
                .andExpect(jsonPath("$.createdAt").isNotEmpty());
    }
}
