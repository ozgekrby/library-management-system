package com.library.library_management_system.controller;

import com.library.library_management_system.dto.request.LoginRequest;
import com.library.library_management_system.dto.request.RegisterUserRequest;
import com.library.library_management_system.dto.response.AuthResponse;
import com.library.library_management_system.dto.response.UserResponse;
import com.library.library_management_system.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "APIs for user registration and login")
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    @Operation(summary = "Register a new user (patron or librarian)")
    public ResponseEntity<UserResponse> registerUser(@Valid @RequestBody RegisterUserRequest registerRequest) {
        UserResponse userResponse = authService.registerUser(registerRequest);
        return new ResponseEntity<>(userResponse, HttpStatus.CREATED);
    }

    @PostMapping("/login")
    @Operation(summary = "Login for existing users")
    public ResponseEntity<AuthResponse> loginUser(@Valid @RequestBody LoginRequest loginRequest) {
        AuthResponse authResponse = authService.loginUser(loginRequest);
        return ResponseEntity.ok(authResponse);
    }
}
