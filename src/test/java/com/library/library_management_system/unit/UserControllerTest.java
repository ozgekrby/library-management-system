package com.library.library_management_system.unit;

import com.library.library_management_system.controller.UserController;
import com.library.library_management_system.dto.request.UpdateUserRequest;
import com.library.library_management_system.dto.response.UserResponse;
import com.library.library_management_system.entity.Role;
import com.library.library_management_system.exception.ResourceNotFoundException;
import com.library.library_management_system.exception.UserAlreadyExistsException;
import com.library.library_management_system.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserControllerTest {

    @Mock
    private UserService userService;

    @InjectMocks
    private UserController userController;

    private UserResponse userResponse;
    private UpdateUserRequest updateUserRequest;
    private Pageable pageable;

    @BeforeEach
    void setUp() {
        userResponse = UserResponse.builder()
                .id(1L)
                .username("testuser")
                .email("test@example.com")
                .fullName("Test User FullName")
                .role(Role.PATRON)
                .build();

        updateUserRequest = new UpdateUserRequest();
        updateUserRequest.setFullName("Updated Name");
        updateUserRequest.setEmail("updated@example.com");

        pageable = PageRequest.of(0, 10);
    }

    @Test
    void getUserById_whenUserExists_shouldCallServiceAndReturnOk() {
        Long userId = 1L;
        when(userService.getUserById(userId)).thenReturn(userResponse);

        ResponseEntity<UserResponse> responseEntity = userController.getUserById(userId);
        assertNotNull(responseEntity);
        assertEquals(HttpStatus.OK, responseEntity.getStatusCode());
        assertEquals(userResponse, responseEntity.getBody());
        verify(userService, times(1)).getUserById(userId);
    }

    @Test
    void getUserById_whenUserNotFound_shouldPropagateResourceNotFoundException() {
        Long userId = 99L;
        when(userService.getUserById(userId)).thenThrow(new ResourceNotFoundException("User not found"));
        ResourceNotFoundException exception = assertThrows(ResourceNotFoundException.class, () -> {
            userController.getUserById(userId);
        });
        assertEquals("User not found", exception.getMessage());
        verify(userService, times(1)).getUserById(userId);
    }

    @Test
    void getAllUsers_shouldCallServiceAndReturnOkWithPage() {
        Page<UserResponse> userPage = new PageImpl<>(Collections.singletonList(userResponse), pageable, 1);
        when(userService.getAllUsers(any(Pageable.class))).thenReturn(userPage);

        ResponseEntity<Page<UserResponse>> responseEntity = userController.getAllUsers(pageable);
        assertNotNull(responseEntity);
        assertEquals(HttpStatus.OK, responseEntity.getStatusCode());
        assertEquals(userPage, responseEntity.getBody());
        verify(userService, times(1)).getAllUsers(pageable);
    }

    @Test
    void updateUser_whenUserExistsAndRequestValid_shouldCallServiceAndReturnOk() {
        Long userId = 1L;
        UserResponse updatedUserResponse = UserResponse.builder()
                .id(userId)
                .username(userResponse.getUsername())
                .email(updateUserRequest.getEmail())
                .fullName(updateUserRequest.getFullName())
                .role(userResponse.getRole())
                .build();
        when(userService.updateUser(eq(userId), any(UpdateUserRequest.class))).thenReturn(updatedUserResponse);

        ResponseEntity<UserResponse> responseEntity = userController.updateUser(userId, updateUserRequest);
        assertNotNull(responseEntity);
        assertEquals(HttpStatus.OK, responseEntity.getStatusCode());
        assertEquals(updatedUserResponse, responseEntity.getBody());
        verify(userService, times(1)).updateUser(userId, updateUserRequest);
    }

    @Test
    void updateUser_whenServiceThrowsUserAlreadyExistsException_shouldPropagateException() {
        Long userId = 1L;
        when(userService.updateUser(eq(userId), any(UpdateUserRequest.class)))
                .thenThrow(new UserAlreadyExistsException("Email already exists"));

        UserAlreadyExistsException exception = assertThrows(UserAlreadyExistsException.class, () -> {
            userController.updateUser(userId, updateUserRequest);
        });
        assertEquals("Email already exists", exception.getMessage());
        verify(userService, times(1)).updateUser(userId, updateUserRequest);
    }

    @Test
    void deleteUser_whenUserExists_shouldCallServiceAndReturnNoContent() {
        Long userId = 1L;
        doNothing().when(userService).deleteUser(userId);

        ResponseEntity<Void> responseEntity = userController.deleteUser(userId);
        assertNotNull(responseEntity);
        assertEquals(HttpStatus.NO_CONTENT, responseEntity.getStatusCode());
        verify(userService, times(1)).deleteUser(userId);
    }

    @Test
    void deleteUser_whenServiceThrowsIllegalStateException_shouldPropagateException() {
        Long userId = 1L;
        doThrow(new IllegalStateException("User has active borrows")).when(userService).deleteUser(userId);

        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> {
            userController.deleteUser(userId);
        });
        assertEquals("User has active borrows", exception.getMessage());
        verify(userService, times(1)).deleteUser(userId);
    }
}

