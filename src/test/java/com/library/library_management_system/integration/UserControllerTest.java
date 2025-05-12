package com.library.library_management_system.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.library.library_management_system.dto.request.UpdateUserRequest;
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
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private User librarianUser;
    private User patronUser;

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();

        librarianUser = User.builder()
                .username("librarian")
                .password(passwordEncoder.encode("pass"))
                .email("librarian@example.com")
                .fullName("Lib Rarian")
                .role(Role.LIBRARIAN)
                .build();
        userRepository.save(librarianUser);

        patronUser = User.builder()
                .username("patron")
                .password(passwordEncoder.encode("pass"))
                .email("patron@example.com")
                .fullName("Pat Ron")
                .role(Role.PATRON)
                .build();
        userRepository.save(patronUser);
    }

    @AfterEach
    void tearDown() {
        userRepository.deleteAll();
    }

    @Test
    @WithMockUser(username = "librarian", roles = "LIBRARIAN")
    void getUserById_whenLibrarianAndUserExists_shouldReturnUser() throws Exception {
        mockMvc.perform(get("/api/users/" + patronUser.getId()))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id", is(patronUser.getId().intValue())))
                .andExpect(jsonPath("$.username", is(patronUser.getUsername())));
    }

    @Test
    @WithMockUser(username = "patron", roles = "PATRON")
    void getUserById_whenPatron_shouldReturnForbidden() throws Exception {
        mockMvc.perform(get("/api/users/" + librarianUser.getId()))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "librarian", roles = "LIBRARIAN")
    void getAllUsers_whenLibrarian_shouldReturnPageOfUsers() throws Exception {
        mockMvc.perform(get("/api/users").param("page", "0").param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(2)))
                .andExpect(jsonPath("$.totalPages").value(1))
                .andExpect(jsonPath("$.totalElements").value(2));
    }

    @Test
    @WithMockUser(username = "librarian", roles = "LIBRARIAN")
    void updateUser_whenLibrarianAndValidRequest_shouldReturnUpdatedUser() throws Exception {
        UpdateUserRequest updateUserRequest = new UpdateUserRequest();
        updateUserRequest.setFullName("Updated Patron Name");
        updateUserRequest.setEmail("updatedpatron@example.com");

        mockMvc.perform(put("/api/users/" + patronUser.getId())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateUserRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName", is("Updated Patron Name")))
                .andExpect(jsonPath("$.email", is("updatedpatron@example.com")));
    }

    @Test
    @WithMockUser(username = "librarian", roles = "LIBRARIAN")
    void deleteUser_whenLibrarianAndUserHasNoActiveBorrows_shouldReturnNoContent() throws Exception {
        mockMvc.perform(delete("/api/users/" + patronUser.getId()).with(csrf()))
                .andExpect(status().isNoContent());
    }

    @Test
    @WithMockUser(username = "unauthorizedUser", roles = "PATRON")
    void deleteUser_whenNotLibrarian_shouldReturnForbidden() throws Exception {
        mockMvc.perform(delete("/api/users/" + patronUser.getId()).with(csrf()))
                .andExpect(status().isForbidden());
    }


    @Test
    void getUserById_whenUnauthenticated_shouldReturnUnauthorized() throws Exception {
        mockMvc.perform(get("/api/users/" + patronUser.getId()))
                .andExpect(status().isForbidden());
    }
}