package com.library.library_management_system.service;

import com.library.library_management_system.dto.request.UpdateUserRequest;
import com.library.library_management_system.dto.response.UserResponse;
import com.library.library_management_system.entity.User;
import com.library.library_management_system.exception.ResourceNotFoundException;
import com.library.library_management_system.exception.UserAlreadyExistsException;
import com.library.library_management_system.repository.BorrowingRecordRepository;
import com.library.library_management_system.repository.FineRepository;
import com.library.library_management_system.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final FineRepository fineRepository;
    private final PasswordEncoder passwordEncoder;
    private final BorrowingRecordRepository borrowingRecordRepository;

    //Retrieves a user by their ID.
    @Transactional(readOnly = true)
    public UserResponse getUserById(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + userId));
        return mapToUserResponse(user);
    }

    //Retrieves a paginated list of all users.
    @Transactional(readOnly = true)
    public Page<UserResponse> getAllUsers(Pageable pageable) {
        return userRepository.findAll(pageable).map(this::mapToUserResponse);
    }

    //Updates an existing user’s information.
    @Transactional
    public UserResponse updateUser(Long userId, UpdateUserRequest updateUserRequest) {
        User userToUpdate = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + userId));


        if (StringUtils.hasText(updateUserRequest.getUsername()) && !userToUpdate.getUsername().equals(updateUserRequest.getUsername())) {
            if (userRepository.existsByUsername(updateUserRequest.getUsername())) {
                throw new UserAlreadyExistsException("Username is already taken: " + updateUserRequest.getUsername());
            }
            userToUpdate.setUsername(updateUserRequest.getUsername());
        }


        if (StringUtils.hasText(updateUserRequest.getEmail()) && !userToUpdate.getEmail().equals(updateUserRequest.getEmail())) {
            if (userRepository.existsByEmail(updateUserRequest.getEmail())) {
                throw new UserAlreadyExistsException("Email is already registered: " + updateUserRequest.getEmail());
            }
            userToUpdate.setEmail(updateUserRequest.getEmail());
        }

        if (StringUtils.hasText(updateUserRequest.getFullName())) {
            userToUpdate.setFullName(updateUserRequest.getFullName());
        }


        if (StringUtils.hasText(updateUserRequest.getPassword())) {
            userToUpdate.setPassword(passwordEncoder.encode(updateUserRequest.getPassword()));
        }


        if (updateUserRequest.getRole() != null) {
            userToUpdate.setRole(updateUserRequest.getRole());
        }

        User updatedUser = userRepository.save(userToUpdate);
        log.info("User updated: {}", updatedUser.getUsername());
        return mapToUserResponse(updatedUser);
    }

    //Deletes a user if they have no active borrowed books.
    @Transactional
    public void deleteUser(Long userId) {
        User userToDelete = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + userId));


        if (borrowingRecordRepository.existsByUserAndReturnDateIsNull(userToDelete)) {
            throw new IllegalStateException("Cannot delete user. User has active borrowed books that are not returned.");
        }
        userRepository.delete(userToDelete);
        log.info("User deleted with id: {}", userId);
    }

    //Maps a User entity to a UserResponse DTO.
    private UserResponse mapToUserResponse(User user) {
        return UserResponse.builder()
                .id(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .fullName(user.getFullName())
                .role(user.getRole())
                .build();
    }
}
