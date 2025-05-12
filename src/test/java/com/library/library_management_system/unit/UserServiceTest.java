package com.library.library_management_system.unit;

import com.library.library_management_system.dto.request.UpdateUserRequest;
import com.library.library_management_system.dto.response.UserResponse;
import com.library.library_management_system.entity.Role;
import com.library.library_management_system.entity.User;
import com.library.library_management_system.exception.ResourceNotFoundException;
import com.library.library_management_system.exception.UserAlreadyExistsException;
import com.library.library_management_system.repository.BorrowingRecordRepository;
import com.library.library_management_system.repository.UserRepository;
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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import java.util.Collections;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@ActiveProfiles("test")
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private BorrowingRecordRepository borrowingRecordRepository;

    @InjectMocks
    private UserService userService;

    private User user;
    private UpdateUserRequest updateUserRequest;

    @BeforeEach
    void setUp() {
        user = User.builder()
                .id(1L)
                .username("testuser")
                .email("test@example.com")
                .fullName("Test User")
                .password("encodedPassword")
                .role(Role.PATRON)
                .build();

        updateUserRequest = new UpdateUserRequest();
        updateUserRequest.setUsername("updateduser");
        updateUserRequest.setEmail("updated@example.com");
        updateUserRequest.setFullName("Updated Test User");
        updateUserRequest.setPassword("newPassword");
        updateUserRequest.setRole(Role.LIBRARIAN);
    }

    @Test
    void getUserById_whenUserExists_shouldReturnUserResponse() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        UserResponse userResponse = userService.getUserById(1L);

        assertNotNull(userResponse);
        assertEquals(user.getId(), userResponse.getId());
        assertEquals(user.getUsername(), userResponse.getUsername());
        verify(userRepository, times(1)).findById(1L);
    }

    @Test
    void getUserById_whenUserNotExists_shouldThrowResourceNotFoundException() {
        when(userRepository.findById(1L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> userService.getUserById(1L));
        verify(userRepository, times(1)).findById(1L);
    }

    @Test
    void getAllUsers_shouldReturnPageOfUserResponses() {
        Pageable pageable = PageRequest.of(0, 10);
        Page<User> userPage = new PageImpl<>(Collections.singletonList(user), pageable, 1);
        when(userRepository.findAll(pageable)).thenReturn(userPage);

        Page<UserResponse> resultPage = userService.getAllUsers(pageable);

        assertNotNull(resultPage);
        assertEquals(1, resultPage.getTotalElements());
        assertEquals(user.getUsername(), resultPage.getContent().get(0).getUsername());
        verify(userRepository, times(1)).findAll(pageable);
    }

    @Test
    void updateUser_whenUserExistsAndDataIsValid_shouldReturnUpdatedUserResponse() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(userRepository.existsByUsername(updateUserRequest.getUsername())).thenReturn(false);
        when(userRepository.existsByEmail(updateUserRequest.getEmail())).thenReturn(false);
        when(passwordEncoder.encode(updateUserRequest.getPassword())).thenReturn("newEncodedPassword");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UserResponse updatedUser = userService.updateUser(1L, updateUserRequest);

        assertNotNull(updatedUser);
        assertEquals(updateUserRequest.getUsername(), updatedUser.getUsername());
        assertEquals(updateUserRequest.getEmail(), updatedUser.getEmail());
        assertEquals(updateUserRequest.getFullName(), updatedUser.getFullName());
        assertEquals(updateUserRequest.getRole(), updatedUser.getRole());
        verify(passwordEncoder, times(1)).encode("newPassword");
        verify(userRepository, times(1)).save(any(User.class));
    }

    @Test
    void updateUser_whenNewUsernameExists_shouldThrowUserAlreadyExistsException() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(userRepository.existsByUsername(updateUserRequest.getUsername())).thenReturn(true);

        assertThrows(UserAlreadyExistsException.class, () -> userService.updateUser(1L, updateUserRequest));
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void updateUser_whenNewEmailExists_shouldThrowUserAlreadyExistsException() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(userRepository.existsByUsername(updateUserRequest.getUsername())).thenReturn(false);
        when(userRepository.existsByEmail(updateUserRequest.getEmail())).thenReturn(true);

        assertThrows(UserAlreadyExistsException.class, () -> userService.updateUser(1L, updateUserRequest));
        verify(userRepository, never()).save(any(User.class));
    }


    @Test
    void deleteUser_whenUserExistsAndHasNoActiveBorrows_shouldDeleteUser() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(borrowingRecordRepository.existsByUserAndReturnDateIsNull(user)).thenReturn(false);

        userService.deleteUser(1L);

        verify(userRepository, times(1)).delete(user);
    }

    @Test
    void deleteUser_whenUserHasActiveBorrows_shouldThrowIllegalStateException() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(borrowingRecordRepository.existsByUserAndReturnDateIsNull(user)).thenReturn(true);

        assertThrows(IllegalStateException.class, () -> userService.deleteUser(1L));
        verify(userRepository, never()).delete(any(User.class));
    }

    @Test
    void deleteUser_whenUserNotExists_shouldThrowResourceNotFoundException() {
        when(userRepository.findById(1L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> userService.deleteUser(1L));
        verify(borrowingRecordRepository, never()).existsByUserAndReturnDateIsNull(any(User.class));
        verify(userRepository, never()).delete(any(User.class));
    }
}
