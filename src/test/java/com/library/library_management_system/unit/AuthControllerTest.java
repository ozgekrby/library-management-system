package com.library.library_management_system.unit;

import com.library.library_management_system.controller.AuthController;
import com.library.library_management_system.dto.request.LoginRequest;
import com.library.library_management_system.dto.request.RegisterUserRequest;
import com.library.library_management_system.dto.response.AuthResponse;
import com.library.library_management_system.dto.response.UserResponse;
import com.library.library_management_system.entity.Role;
import com.library.library_management_system.exception.UserAlreadyExistsException;
import com.library.library_management_system.service.AuthService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    @Mock
    private AuthService authService;

    @InjectMocks
    private AuthController authController;

    private RegisterUserRequest validRegisterRequestPatron;
    private UserResponse userResponsePatron;
    private LoginRequest validLoginRequest;
    private AuthResponse authResponse;

    @BeforeEach
    void setUp() {
        validRegisterRequestPatron = new RegisterUserRequest();
        validRegisterRequestPatron.setUsername("testpatron");
        validRegisterRequestPatron.setPassword("password123");
        validRegisterRequestPatron.setEmail("patron@example.com");
        validRegisterRequestPatron.setFullName("Test Patron");
        validRegisterRequestPatron.setRole(Role.PATRON);

        userResponsePatron = UserResponse.builder()
                .id(1L)
                .username("testpatron")
                .email("patron@example.com")
                .fullName("Test Patron")
                .role(Role.PATRON)
                .build();

        validLoginRequest = new LoginRequest();
        validLoginRequest.setUsername("testpatron");
        validLoginRequest.setPassword("password123");

        authResponse = AuthResponse.builder()
                .accessToken("mock.jwt.token")
                .tokenType("Bearer")
                .userId(1L)
                .username("testpatron")
                .roles(Collections.singletonList("PATRON"))
                .build();
    }

    @Test
    void registerUser_withValidData_shouldCallAuthServiceAndReturnCreated() {
        when(authService.registerUser(any(RegisterUserRequest.class))).thenReturn(userResponsePatron);
        ResponseEntity<UserResponse> responseEntity = authController.registerUser(validRegisterRequestPatron);
        assertNotNull(responseEntity);
        assertEquals(HttpStatus.CREATED, responseEntity.getStatusCode());
        assertNotNull(responseEntity.getBody());
        assertEquals(userResponsePatron.getUsername(), responseEntity.getBody().getUsername());
        assertEquals(userResponsePatron.getEmail(), responseEntity.getBody().getEmail());
        verify(authService, times(1)).registerUser(validRegisterRequestPatron);
    }

    @Test
    void registerUser_whenAuthServiceThrowsUserAlreadyExists_shouldPropagateException() {
        when(authService.registerUser(any(RegisterUserRequest.class)))
                .thenThrow(new UserAlreadyExistsException("Username is already taken: testpatron"));
        UserAlreadyExistsException exception = assertThrows(UserAlreadyExistsException.class, () -> {
            authController.registerUser(validRegisterRequestPatron);
        });
        assertEquals("Username is already taken: testpatron", exception.getMessage());
        verify(authService, times(1)).registerUser(validRegisterRequestPatron);
    }

    @Test
    void loginUser_withValidCredentials_shouldCallAuthServiceAndReturnOk() {
        when(authService.loginUser(any(LoginRequest.class))).thenReturn(authResponse);
        ResponseEntity<AuthResponse> responseEntity = authController.loginUser(validLoginRequest);
        assertNotNull(responseEntity);
        assertEquals(HttpStatus.OK, responseEntity.getStatusCode());
        assertNotNull(responseEntity.getBody());
        assertEquals(authResponse.getAccessToken(), responseEntity.getBody().getAccessToken());
        assertEquals(authResponse.getUsername(), responseEntity.getBody().getUsername());

        verify(authService, times(1)).loginUser(validLoginRequest);
    }

    @Test
    void loginUser_whenAuthServiceThrowsBadCredentials_shouldPropagateException() {
        when(authService.loginUser(any(LoginRequest.class)))
                .thenThrow(new BadCredentialsException("Invalid username or password"));
        BadCredentialsException exception = assertThrows(BadCredentialsException.class, () -> {
            authController.loginUser(validLoginRequest);
        });
        assertEquals("Invalid username or password", exception.getMessage());
        verify(authService, times(1)).loginUser(validLoginRequest);
    }
}
