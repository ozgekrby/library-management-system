package com.library.library_management_system.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.library.library_management_system.dto.request.LoginRequest;
import com.library.library_management_system.dto.request.RegisterUserRequest;
import com.library.library_management_system.entity.Role;
import com.library.library_management_system.entity.User;
import com.library.library_management_system.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private RegisterUserRequest validRegisterRequestPatron;
    private RegisterUserRequest validRegisterRequestLibrarian;

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();

        validRegisterRequestPatron = new RegisterUserRequest();
        validRegisterRequestPatron.setUsername("testuserpatron");
        validRegisterRequestPatron.setPassword("password123");
        validRegisterRequestPatron.setEmail("patron@example.com");
        validRegisterRequestPatron.setFullName("Test Patron");
        validRegisterRequestPatron.setRole(Role.PATRON);

        validRegisterRequestLibrarian = new RegisterUserRequest();
        validRegisterRequestLibrarian.setUsername("testuserlibrarian");
        validRegisterRequestLibrarian.setPassword("password456");
        validRegisterRequestLibrarian.setEmail("librarian@example.com");
        validRegisterRequestLibrarian.setFullName("Test Librarian");
        validRegisterRequestLibrarian.setRole(Role.LIBRARIAN);
    }

    @AfterEach
    void tearDown() {
        userRepository.deleteAll();
    }

    @Test
    void registerUser_withValidPatronData_shouldReturnCreated() throws Exception {
        ResultActions result = mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(validRegisterRequestPatron)));

        result.andExpect(status().isCreated())
                .andExpect(jsonPath("$.username", is(validRegisterRequestPatron.getUsername())))
                .andExpect(jsonPath("$.email", is(validRegisterRequestPatron.getEmail())))
                .andExpect(jsonPath("$.role", is(Role.PATRON.toString())));
    }

    @Test
    void registerUser_withValidLibrarianData_shouldReturnCreated() throws Exception {
        ResultActions result = mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(validRegisterRequestLibrarian)));

        result.andExpect(status().isCreated())
                .andExpect(jsonPath("$.username", is(validRegisterRequestLibrarian.getUsername())))
                .andExpect(jsonPath("$.email", is(validRegisterRequestLibrarian.getEmail())))
                .andExpect(jsonPath("$.role", is(Role.LIBRARIAN.toString())));
    }

    @Test
    void registerUser_whenUsernameAlreadyExists_shouldReturnConflict() throws Exception {
        userRepository.save(User.builder()
                .username(validRegisterRequestPatron.getUsername())
                .password(passwordEncoder.encode("anypassword"))
                .email("anotheremail@example.com")
                .fullName("Existing User")
                .role(Role.PATRON)
                .build());

        ResultActions result = mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(validRegisterRequestPatron)));

        result.andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", is("Username is already taken: " + validRegisterRequestPatron.getUsername())));
    }

    @Test
    void registerUser_whenEmailAlreadyExists_shouldReturnConflict() throws Exception {
        userRepository.save(User.builder()
                .username("anotherusername")
                .password(passwordEncoder.encode("anypassword"))
                .email(validRegisterRequestPatron.getEmail())
                .fullName("Existing User")
                .role(Role.PATRON)
                .build());

        ResultActions result = mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(validRegisterRequestPatron)));

        result.andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", is("Email is already registered: " + validRegisterRequestPatron.getEmail())));
    }

    @Test
    void registerUser_withInvalidData_shouldReturnBadRequest() throws Exception {
        RegisterUserRequest invalidRequest = new RegisterUserRequest();
        invalidRequest.setUsername("");
        invalidRequest.setEmail("notanemail");
        invalidRequest.setPassword("short");
        invalidRequest.setRole(null);

        ResultActions result = mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(invalidRequest)));

        result.andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.username", notNullValue()))
                .andExpect(jsonPath("$.errors.email", notNullValue()))
                .andExpect(jsonPath("$.errors.password", notNullValue()))
                .andExpect(jsonPath("$.errors.role", is("Role cannot be null")));
    }

    @Test
    void loginUser_withValidCredentials_shouldReturnOkAndAuthResponse() throws Exception {
        User userToLogin = User.builder()
                .username("loginTestUser")
                .password(passwordEncoder.encode("securePassword"))
                .email("login@test.com")
                .fullName("Login Test User")
                .role(Role.PATRON)
                .build();
        userRepository.save(userToLogin);

        LoginRequest loginRequest = new LoginRequest();
        loginRequest.setUsername("loginTestUser");
        loginRequest.setPassword("securePassword");

        ResultActions result = mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(loginRequest)));

        result.andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken", notNullValue()))
                .andExpect(jsonPath("$.tokenType", is("Bearer")))
                .andExpect(jsonPath("$.username", is("loginTestUser")))
                .andExpect(jsonPath("$.roles[0]", is(Role.PATRON.toString())));
    }

    @Test
    void loginUser_withInvalidPassword_shouldReturnUnauthorized() throws Exception {
        User userToLogin = User.builder()
                .username("loginTestUser")
                .password(passwordEncoder.encode("securePassword"))
                .email("login@test.com")
                .fullName("Login Test User")
                .role(Role.PATRON)
                .build();
        userRepository.save(userToLogin);

        LoginRequest loginRequest = new LoginRequest();
        loginRequest.setUsername("loginTestUser");
        loginRequest.setPassword("wrongPassword");

        ResultActions result = mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(loginRequest)));

        result.andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message", is("Invalid username or password")));
    }

    @Test
    void loginUser_withNonExistentUser_shouldReturnUnauthorized() throws Exception {
        LoginRequest loginRequest = new LoginRequest();
        loginRequest.setUsername("nonExistentUser");
        loginRequest.setPassword("anyPassword");

        ResultActions result = mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(loginRequest)));

        result.andExpect(status().isUnauthorized());
    }

    @Test
    void loginUser_withBlankUsername_shouldReturnBadRequest() throws Exception {
        LoginRequest loginRequest = new LoginRequest();
        loginRequest.setUsername("");
        loginRequest.setPassword("anyPassword");

        ResultActions result = mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(loginRequest)));

        result.andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.username", is("Username cannot be blank")));
    }
}
