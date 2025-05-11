package com.library.library_management_system.dto.request;

import com.library.library_management_system.entity.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UpdateUserRequest {

    @Size(min = 3, max = 50, message = "Username must be between 3 and 50 characters if provided")
    private String username;

    @Size(min = 6, max = 100, message = "Password must be between 6 and 100 characters if provided")
    private String password;

    @Email(message = "Invalid email format if provided")
    @Size(max = 100, message = "Email cannot exceed 100 characters if provided")
    private String email;

    @Size(max = 100, message = "Full name cannot exceed 100 characters if provided")
    private String fullName;

    private Role role;
}
