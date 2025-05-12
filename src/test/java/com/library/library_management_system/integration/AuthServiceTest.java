package com.library.library_management_system.integration;

import com.library.library_management_system.dto.request.LoginRequest;
import com.library.library_management_system.dto.request.RegisterUserRequest;
import com.library.library_management_system.dto.response.AuthResponse;
import com.library.library_management_system.dto.response.UserResponse;
import com.library.library_management_system.entity.Role;
import com.library.library_management_system.entity.User;
import com.library.library_management_system.exception.UserAlreadyExistsException;
import com.library.library_management_system.repository.UserRepository;
import com.library.library_management_system.security.JwtTokenProvider;
import com.library.library_management_system.service.AuthService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
public class AuthServiceTest {

    @Autowired
    private AuthService authService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    private RegisterUserRequest registerUserRequestPatron;
    private RegisterUserRequest registerUserRequestLibrarian;

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();

        registerUserRequestPatron = new RegisterUserRequest();
        registerUserRequestPatron.setUsername("testpatron");
        registerUserRequestPatron.setPassword("password123");
        registerUserRequestPatron.setEmail("testpatron@example.com");
        registerUserRequestPatron.setFullName("Test Patron FullName");
        registerUserRequestPatron.setRole(Role.PATRON);

        registerUserRequestLibrarian = new RegisterUserRequest();
        registerUserRequestLibrarian.setUsername("testlibrarian");
        registerUserRequestLibrarian.setPassword("password456");
        registerUserRequestLibrarian.setEmail("testlibrarian@example.com");
        registerUserRequestLibrarian.setFullName("Test Librarian FullName");
        registerUserRequestLibrarian.setRole(Role.LIBRARIAN);
    }

    @AfterEach
    void tearDown() {
    }

    @Test
    void registerUser_whenPatronDataIsValid_shouldRegisterAndReturnUserResponse() {
        UserResponse response = authService.registerUser(registerUserRequestPatron);

        assertNotNull(response);
        assertNotNull(response.getId());
        assertEquals(registerUserRequestPatron.getUsername(), response.getUsername());
        assertEquals(registerUserRequestPatron.getEmail(), response.getEmail());
        assertEquals(Role.PATRON, response.getRole());

        Optional<User> savedUserOpt = userRepository.findByUsername(registerUserRequestPatron.getUsername());
        assertTrue(savedUserOpt.isPresent());
        User savedUser = savedUserOpt.get();
        assertEquals(registerUserRequestPatron.getUsername(), savedUser.getUsername());
        assertEquals(registerUserRequestPatron.getEmail(), savedUser.getEmail());
        assertTrue(passwordEncoder.matches(registerUserRequestPatron.getPassword(), savedUser.getPassword()));
        assertEquals(Role.PATRON, savedUser.getRole());
    }

    @Test
    void registerUser_whenLibrarianDataIsValid_shouldRegisterAndReturnUserResponse() {
        UserResponse response = authService.registerUser(registerUserRequestLibrarian);

        assertNotNull(response);
        assertNotNull(response.getId());
        assertEquals(registerUserRequestLibrarian.getUsername(), response.getUsername());
        assertEquals(Role.LIBRARIAN, response.getRole());

        Optional<User> savedUserOpt = userRepository.findByUsername(registerUserRequestLibrarian.getUsername());
        assertTrue(savedUserOpt.isPresent());
        assertEquals(Role.LIBRARIAN, savedUserOpt.get().getRole());
    }

    @Test
    void registerUser_whenUsernameExists_shouldThrowUserAlreadyExistsException() {
        authService.registerUser(registerUserRequestPatron);

        RegisterUserRequest duplicateUsernameRequest = new RegisterUserRequest();
        duplicateUsernameRequest.setUsername(registerUserRequestPatron.getUsername());
        duplicateUsernameRequest.setPassword("anotherPassword");
        duplicateUsernameRequest.setEmail("another@example.com");
        duplicateUsernameRequest.setFullName("Another User");
        duplicateUsernameRequest.setRole(Role.PATRON);

        UserAlreadyExistsException exception = assertThrows(UserAlreadyExistsException.class,
                () -> authService.registerUser(duplicateUsernameRequest));
        assertEquals("Username is already taken: " + registerUserRequestPatron.getUsername(), exception.getMessage());
        assertEquals(1, userRepository.findAll().stream()
                .filter(u -> u.getUsername().equals(registerUserRequestPatron.getUsername())).count());
    }

    @Test
    void registerUser_whenEmailExists_shouldThrowUserAlreadyExistsException() {
        authService.registerUser(registerUserRequestPatron);

        RegisterUserRequest duplicateEmailRequest = new RegisterUserRequest();
        duplicateEmailRequest.setUsername("anotherUser");
        duplicateEmailRequest.setPassword("anotherPassword");
        duplicateEmailRequest.setEmail(registerUserRequestPatron.getEmail());
        duplicateEmailRequest.setFullName("Another User");
        duplicateEmailRequest.setRole(Role.PATRON);

        UserAlreadyExistsException exception = assertThrows(UserAlreadyExistsException.class,
                () -> authService.registerUser(duplicateEmailRequest));
        assertEquals("Email is already registered: " + registerUserRequestPatron.getEmail(), exception.getMessage());

        assertEquals(1, userRepository.findAll().stream()
                .filter(u -> u.getEmail().equals(registerUserRequestPatron.getEmail())).count());
    }

    @Test
    void loginUser_whenCredentialsAreValid_shouldReturnAuthResponseWithToken() {
        authService.registerUser(registerUserRequestPatron);

        LoginRequest loginRequest = new LoginRequest();
        loginRequest.setUsername(registerUserRequestPatron.getUsername());
        loginRequest.setPassword(registerUserRequestPatron.getPassword());

        AuthResponse response = authService.loginUser(loginRequest);

        assertNotNull(response);
        assertNotNull(response.getAccessToken());
        assertFalse(response.getAccessToken().isEmpty());
        assertEquals("Bearer", response.getTokenType());
        assertNotNull(response.getUserId());
        assertEquals(registerUserRequestPatron.getUsername(), response.getUsername());
        assertTrue(response.getRoles().contains(Role.PATRON.name()));

        assertTrue(jwtTokenProvider.validateToken(response.getAccessToken()));
        assertEquals(registerUserRequestPatron.getUsername(), jwtTokenProvider.getUsernameFromJWT(response.getAccessToken()));
    }

    @Test
    void loginUser_whenUsernameIsInvalid_shouldThrowBadCredentialsException() {
        authService.registerUser(registerUserRequestPatron);

        LoginRequest loginRequest = new LoginRequest();
        loginRequest.setUsername("wronguser");
        loginRequest.setPassword(registerUserRequestPatron.getPassword());

        assertThrows(BadCredentialsException.class, () -> {
            authService.loginUser(loginRequest);
        });
    }

    @Test
    void loginUser_whenPasswordIsInvalid_shouldThrowBadCredentialsException() {
        authService.registerUser(registerUserRequestPatron);

        LoginRequest loginRequest = new LoginRequest();
        loginRequest.setUsername(registerUserRequestPatron.getUsername());
        loginRequest.setPassword("wrongPassword");

        assertThrows(BadCredentialsException.class, () -> {
            authService.loginUser(loginRequest);
        });
    }
}
