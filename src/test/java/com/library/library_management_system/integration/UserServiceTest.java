package com.library.library_management_system.integration;

import com.library.library_management_system.dto.request.UpdateUserRequest;
import com.library.library_management_system.dto.response.UserResponse;
import com.library.library_management_system.entity.Book;
import com.library.library_management_system.entity.BorrowingRecord;
import com.library.library_management_system.entity.Role;
import com.library.library_management_system.entity.User;
import com.library.library_management_system.exception.ResourceNotFoundException;
import com.library.library_management_system.exception.UserAlreadyExistsException;
import com.library.library_management_system.repository.*;
import com.library.library_management_system.service.UserService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
public class UserServiceTest {

    @Autowired
    private UserService userService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private BorrowingRecordRepository borrowingRecordRepository;

    @Autowired
    private BookRepository bookRepository;

    @Autowired
    private FineRepository fineRepository;

    @Autowired
    private ReservationRepository reservationRepository;


    private User user1, user2;
    private Book book1;

    @BeforeEach
    void setUp() {
        fineRepository.deleteAllInBatch();
        borrowingRecordRepository.deleteAllInBatch();
        reservationRepository.deleteAllInBatch();
        userRepository.deleteAllInBatch();
        bookRepository.deleteAllInBatch();


        user1 = User.builder()
                .username("testuser1")
                .email("test1@example.com")
                .fullName("Test User One")
                .password(passwordEncoder.encode("Password123!"))
                .role(Role.PATRON)
                .build();
        userRepository.save(user1);

        user2 = User.builder()
                .username("testuser2")
                .email("test2@example.com")
                .fullName("Test User Two")
                .password(passwordEncoder.encode("Password456!"))
                .role(Role.LIBRARIAN)
                .build();
        userRepository.save(user2);

        book1 = Book.builder()
                .title("Test Book for Borrowing")
                .author("Author Borrow")
                .isbn("ISBN-BORROW")
                .publicationDate(LocalDate.now().minusYears(1))
                .genre("Test Genre")
                .totalCopies(1).availableCopies(1).build();
        bookRepository.save(book1);
    }

    @AfterEach
    void tearDown() {
    }

    @Test
    void getUserById_whenUserExists_shouldReturnUserResponse() {
        UserResponse foundUser = userService.getUserById(user1.getId());

        assertNotNull(foundUser);
        assertEquals(user1.getId(), foundUser.getId());
        assertEquals(user1.getUsername(), foundUser.getUsername());
        assertEquals(user1.getEmail(), foundUser.getEmail());
        assertEquals(user1.getFullName(), foundUser.getFullName());
        assertEquals(user1.getRole(), foundUser.getRole());
    }

    @Test
    void getUserById_whenUserNotExists_shouldThrowResourceNotFoundException() {
        Long nonExistentId = 999L;
        ResourceNotFoundException exception = assertThrows(ResourceNotFoundException.class,
                () -> userService.getUserById(nonExistentId));
        assertEquals("User not found with id: " + nonExistentId, exception.getMessage());
    }

    @Test
    void getAllUsers_shouldReturnPageOfUserResponses() {
        Pageable pageable = PageRequest.of(0, 10);
        Page<UserResponse> resultPage = userService.getAllUsers(pageable);

        assertNotNull(resultPage);
        assertEquals(2, resultPage.getTotalElements());
        assertTrue(resultPage.getContent().stream().anyMatch(u -> u.getUsername().equals(user1.getUsername())));
        assertTrue(resultPage.getContent().stream().anyMatch(u -> u.getUsername().equals(user2.getUsername())));
    }

    @Test
    void updateUser_whenUserExistsAndDataIsValid_shouldReturnUpdatedUserResponse() {
        UpdateUserRequest updateUserRequest = new UpdateUserRequest();
        updateUserRequest.setUsername("updatedUsername");
        updateUserRequest.setEmail("updated@example.com");
        updateUserRequest.setFullName("Updated Full Name");
        updateUserRequest.setPassword("newEncodedPassword123!");
        updateUserRequest.setRole(Role.LIBRARIAN);

        UserResponse updatedUserResponse = userService.updateUser(user1.getId(), updateUserRequest);

        assertNotNull(updatedUserResponse);
        assertEquals(updateUserRequest.getUsername(), updatedUserResponse.getUsername());
        assertEquals(updateUserRequest.getEmail(), updatedUserResponse.getEmail());
        assertEquals(updateUserRequest.getFullName(), updatedUserResponse.getFullName());
        assertEquals(updateUserRequest.getRole(), updatedUserResponse.getRole());

        User userFromDb = userRepository.findById(user1.getId()).orElseThrow();
        assertEquals(updateUserRequest.getUsername(), userFromDb.getUsername());
        assertTrue(passwordEncoder.matches("newEncodedPassword123!", userFromDb.getPassword()));
    }

    @Test
    void updateUser_whenUserExistsAndOnlyPartialDataProvided_shouldUpdateOnlyProvidedFields() {
        UpdateUserRequest partialUpdateRequest = new UpdateUserRequest();
        partialUpdateRequest.setFullName("Only FullName Updated");
        UserResponse updatedUserResponse = userService.updateUser(user1.getId(), partialUpdateRequest);

        assertNotNull(updatedUserResponse);
        assertEquals(user1.getUsername(), updatedUserResponse.getUsername());
        assertEquals(user1.getEmail(), updatedUserResponse.getEmail());
        assertEquals("Only FullName Updated", updatedUserResponse.getFullName());
        assertEquals(user1.getRole(), updatedUserResponse.getRole());

        User userFromDb = userRepository.findById(user1.getId()).orElseThrow();
        assertEquals("Only FullName Updated", userFromDb.getFullName());
        assertEquals(user1.getPassword(), userFromDb.getPassword());
    }


    @Test
    void updateUser_whenUserNotExists_shouldThrowResourceNotFoundException() {
        UpdateUserRequest updateUserRequest = new UpdateUserRequest();
        updateUserRequest.setUsername("anyuser");
        updateUserRequest.setEmail("any@mail.com");
        updateUserRequest.setFullName("Any Name");

        Long nonExistentId = 999L;
        assertThrows(ResourceNotFoundException.class,
                () -> userService.updateUser(nonExistentId, updateUserRequest));
    }

    @Test
    void updateUser_whenNewUsernameExistsForAnotherUser_shouldThrowUserAlreadyExistsException() {
        UpdateUserRequest updateUserRequest = new UpdateUserRequest();
        updateUserRequest.setUsername(user2.getUsername());
        updateUserRequest.setEmail("newemail@example.com");
        updateUserRequest.setFullName("User One New Name");


        UserAlreadyExistsException exception = assertThrows(UserAlreadyExistsException.class,
                () -> userService.updateUser(user1.getId(), updateUserRequest));
        assertEquals("Username is already taken: " + user2.getUsername(), exception.getMessage());
    }

    @Test
    void updateUser_whenNewEmailExistsForAnotherUser_shouldThrowUserAlreadyExistsException() {
        UpdateUserRequest updateUserRequest = new UpdateUserRequest();
        updateUserRequest.setUsername("newusernameforuser1");
        updateUserRequest.setEmail(user2.getEmail());
        updateUserRequest.setFullName("User One New Name");

        UserAlreadyExistsException exception = assertThrows(UserAlreadyExistsException.class,
                () -> userService.updateUser(user1.getId(), updateUserRequest));
        assertEquals("Email is already registered: " + user2.getEmail(), exception.getMessage());
    }

    @Test
    void deleteUser_whenUserExistsAndHasNoActiveBorrows_shouldDeleteUser() {
        assertDoesNotThrow(() -> userService.deleteUser(user1.getId()));
        assertFalse(userRepository.existsById(user1.getId()));
    }

    @Test
    void deleteUser_whenUserHasActiveBorrows_shouldThrowIllegalStateException() {
        borrowingRecordRepository.save(BorrowingRecord.builder()
                .user(user1)
                .book(book1)
                .borrowDate(LocalDate.now().minusDays(5))
                .dueDate(LocalDate.now().plusDays(9))
                .returnDate(null)
                .build());
        book1.setAvailableCopies(0);
        bookRepository.save(book1);


        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> userService.deleteUser(user1.getId()));
        assertEquals("Cannot delete user. User has active borrowed books that are not returned.", exception.getMessage());
        assertTrue(userRepository.existsById(user1.getId()));
    }

    @Test
    void deleteUser_whenUserNotExists_shouldThrowResourceNotFoundException() {
        Long nonExistentId = 999L;
        assertThrows(ResourceNotFoundException.class,
                () -> userService.deleteUser(nonExistentId));
    }
}
