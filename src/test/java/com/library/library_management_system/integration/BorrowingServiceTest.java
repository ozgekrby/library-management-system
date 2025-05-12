package com.library.library_management_system.integration;

import com.library.library_management_system.dto.request.BorrowBookRequest;
import com.library.library_management_system.dto.response.BorrowingRecordResponse;
import com.library.library_management_system.entity.*;
import com.library.library_management_system.exception.BookUnavailableException;
import com.library.library_management_system.exception.ResourceNotFoundException;
import com.library.library_management_system.repository.BookRepository;
import com.library.library_management_system.repository.BorrowingRecordRepository;
import com.library.library_management_system.repository.FineRepository;
import com.library.library_management_system.repository.UserRepository;
import com.library.library_management_system.service.BorrowingService;
import com.library.library_management_system.service.FineService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
public class BorrowingServiceTest {

    @Autowired
    private BorrowingService borrowingService;

    @Autowired
    private BookRepository bookRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private BorrowingRecordRepository borrowingRecordRepository;

    @Autowired
    private FineService fineService;

    @Autowired
    private FineRepository fineRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private User patronUser;
    private User librarianUser;
    private Book availableBook;
    private Book anotherAvailableBook;

    @BeforeEach

    void setUp() {
        fineRepository.deleteAll();
        borrowingRecordRepository.deleteAll();
        bookRepository.deleteAll();
        userRepository.deleteAll();

        patronUser = User.builder()
                .username("test_patron")
                .password(passwordEncoder.encode("password123"))
                .email("patron@example.com")
                .fullName("Test Patron FullName")
                .role(Role.PATRON)
                .build();
        userRepository.save(patronUser);

        librarianUser = User.builder()
                .username("test_librarian")
                .password(passwordEncoder.encode("password123"))
                .email("librarian@example.com")
                .fullName("Test Librarian FullName")
                .role(Role.LIBRARIAN)
                .build();
        userRepository.save(librarianUser);

        availableBook = Book.builder()
                .title("The Great Gatsby")
                .author("F. Scott Fitzgerald")
                .isbn("978-0743273565")
                .publicationDate(LocalDate.of(1925, 4, 10))
                .genre("Classic")
                .totalCopies(3)
                .availableCopies(3)
                .build();
        bookRepository.save(availableBook);

        anotherAvailableBook = Book.builder()
                .title("1984")
                .author("George Orwell")
                .isbn("978-0451524935")
                .publicationDate(LocalDate.of(1949, 6, 8))
                .genre("Dystopian")
                .totalCopies(2)
                .availableCopies(2)
                .build();
        bookRepository.save(anotherAvailableBook);
    }

    @AfterEach
    void tearDown() {
    }

    @Test
    void borrowBook_whenBookAvailableAndUserEligible_shouldCreateBorrowingRecord() {
        BorrowBookRequest request = new BorrowBookRequest();
        request.setBookId(availableBook.getId());

        BorrowingRecordResponse response = borrowingService.borrowBook(request, patronUser);

        assertNotNull(response);
        assertEquals(availableBook.getId(), response.getBookId());
        assertEquals(patronUser.getId(), response.getUserId());
        assertEquals(LocalDate.now(), response.getBorrowDate());
        assertEquals(LocalDate.now().plusWeeks(2), response.getDueDate());
        assertNull(response.getReturnDate());

        Book updatedBook = bookRepository.findById(availableBook.getId()).orElseThrow();
        assertEquals(2, updatedBook.getAvailableCopies());

        assertTrue(borrowingRecordRepository.existsById(response.getId()));
    }

    @Test
    void borrowBook_withSpecificDueDate_shouldUseProvidedDueDate() {
        BorrowBookRequest request = new BorrowBookRequest();
        request.setBookId(availableBook.getId());
        LocalDate specificDueDate = LocalDate.now().plusDays(10);
        request.setDueDate(specificDueDate);

        BorrowingRecordResponse response = borrowingService.borrowBook(request, patronUser);
        assertEquals(specificDueDate, response.getDueDate());
    }

    @Test
    void borrowBook_whenBookNotFound_shouldThrowResourceNotFoundException() {
        BorrowBookRequest request = new BorrowBookRequest();
        request.setBookId(999L);

        assertThrows(ResourceNotFoundException.class, () -> borrowingService.borrowBook(request, patronUser));
    }

    @Test
    void borrowBook_whenBookNotAvailable_shouldThrowBookUnavailableException() {
        availableBook.setAvailableCopies(0);
        bookRepository.save(availableBook);

        BorrowBookRequest request = new BorrowBookRequest();
        request.setBookId(availableBook.getId());

        assertThrows(BookUnavailableException.class, () -> borrowingService.borrowBook(request, patronUser));
    }

    @Test
    void borrowBook_whenUserAlreadyBorrowedBook_shouldThrowIllegalStateException() {
        BorrowBookRequest request1 = new BorrowBookRequest();
        request1.setBookId(availableBook.getId());
        borrowingService.borrowBook(request1, patronUser);

        BorrowBookRequest request2 = new BorrowBookRequest();
        request2.setBookId(availableBook.getId());

        assertThrows(IllegalStateException.class, () -> borrowingService.borrowBook(request2, patronUser));
    }

    @Test
    void returnBook_byUser_whenRecordExistsAndReturnedOnTime_shouldUpdateRecordAndBook_noFine() {
        BorrowBookRequest borrowRequest = new BorrowBookRequest();
        borrowRequest.setBookId(availableBook.getId());
        borrowRequest.setDueDate(LocalDate.now().plusDays(5));
        BorrowingRecordResponse borrowedRecord = borrowingService.borrowBook(borrowRequest, patronUser);

        BorrowingRecordResponse returnedResponse = borrowingService.returnBook(borrowedRecord.getId(), patronUser);

        assertNotNull(returnedResponse);
        assertEquals(borrowedRecord.getId(), returnedResponse.getId());
        assertEquals(LocalDate.now(), returnedResponse.getReturnDate());

        Book updatedBook = bookRepository.findById(availableBook.getId()).orElseThrow();
        assertEquals(availableBook.getTotalCopies(), updatedBook.getAvailableCopies());

        Optional<BorrowingRecord> recordInDbOpt = borrowingRecordRepository.findById(borrowedRecord.getId());
        assertTrue(recordInDbOpt.isPresent());
        assertNotNull(recordInDbOpt.get().getReturnDate());

        Optional<Fine> optionalFine = fineRepository.findByBorrowingRecordId(borrowedRecord.getId());
        assertFalse(optionalFine.isPresent());
    }

    @Test
    void returnBook_byUser_whenRecordExistsAndReturnedOverdue_shouldGenerateFine() {
        BorrowBookRequest borrowRequest = new BorrowBookRequest();
        borrowRequest.setBookId(availableBook.getId());
        BorrowingRecordResponse borrowedRecord = borrowingService.borrowBook(borrowRequest, patronUser);

        BorrowingRecord br = borrowingRecordRepository.findById(borrowedRecord.getId()).orElseThrow();
        br.setBorrowDate(LocalDate.now().minusDays(10));
        br.setDueDate(LocalDate.now().minusDays(3));
        borrowingRecordRepository.save(br);

        BorrowingRecordResponse returnedResponse = borrowingService.returnBook(borrowedRecord.getId(), patronUser);

        assertNotNull(returnedResponse.getReturnDate());
        Book updatedBook = bookRepository.findById(availableBook.getId()).orElseThrow();
        assertEquals(availableBook.getTotalCopies(), updatedBook.getAvailableCopies());

        Optional<Fine> optionalFine = fineRepository.findByBorrowingRecordId(borrowedRecord.getId());
        assertTrue(optionalFine.isPresent());
        Fine fine = optionalFine.get();
        assertTrue(fine.getAmount().compareTo(BigDecimal.ZERO) > 0);
        assertEquals(FineStatus.PENDING, fine.getStatus());
    }


    @Test
    void returnBook_byUser_whenRecordNotFound_shouldThrowResourceNotFoundException() {
        assertThrows(ResourceNotFoundException.class,
                () -> borrowingService.returnBook(999L, patronUser));
    }


    @Test
    void returnBookByLibrarian_whenRecordExistsAndReturnedOnTime_shouldUpdateRecordAndBook_noFine() {
        BorrowBookRequest borrowRequest = new BorrowBookRequest();
        borrowRequest.setBookId(availableBook.getId());
        borrowRequest.setDueDate(LocalDate.now().plusDays(5));
        BorrowingRecordResponse borrowedRecord = borrowingService.borrowBook(borrowRequest, patronUser);

        BorrowingRecordResponse returnedResponse = borrowingService.returnBookByLibrarian(borrowedRecord.getId());

        assertNotNull(returnedResponse.getReturnDate());
        Optional<Fine> optionalFine = fineRepository.findByBorrowingRecordId(borrowedRecord.getId());
        assertFalse(optionalFine.isPresent());
    }

    @Test
    void returnBookByLibrarian_whenRecordAlreadyReturned_shouldThrowResourceNotFoundException() {
        BorrowBookRequest borrowRequest = new BorrowBookRequest();
        borrowRequest.setBookId(availableBook.getId());
        BorrowingRecordResponse borrowedRecord = borrowingService.borrowBook(borrowRequest, patronUser);
        borrowingService.returnBookByLibrarian(borrowedRecord.getId());

        Long alreadyReturnedRecordId = borrowedRecord.getId();
        assertThrows(ResourceNotFoundException.class,
                () -> borrowingService.returnBookByLibrarian(alreadyReturnedRecordId));

        Book bookAfterAttempts = bookRepository.findById(availableBook.getId()).orElseThrow();
        assertEquals(availableBook.getTotalCopies(), bookAfterAttempts.getAvailableCopies());
    }


    @Test
    void getUserBorrowingHistory_shouldReturnUserRecords() {
        BorrowBookRequest req1 = new BorrowBookRequest();
        req1.setBookId(availableBook.getId());
        borrowingService.borrowBook(req1, patronUser);

        BorrowBookRequest req2 = new BorrowBookRequest();
        req2.setBookId(anotherAvailableBook.getId());
        borrowingService.borrowBook(req2, patronUser);

        List<BorrowingRecordResponse> history = borrowingService.getUserBorrowingHistory(patronUser);
        assertEquals(2, history.size());
    }

    @Test
    void getBorrowingHistoryForUserByLibrarian_whenUserExists_shouldReturnHistory() {
        BorrowBookRequest req1 = new BorrowBookRequest();
        req1.setBookId(availableBook.getId());
        borrowingService.borrowBook(req1, patronUser);

        List<BorrowingRecordResponse> history = borrowingService.getBorrowingHistoryForUserByLibrarian(patronUser.getId());
        assertEquals(1, history.size());
        assertEquals(patronUser.getId(), history.get(0).getUserId());
    }

    @Test
    void getBorrowingHistoryForUserByLibrarian_whenUserNotExists_shouldThrowResourceNotFound() {
        assertThrows(ResourceNotFoundException.class,
                () -> borrowingService.getBorrowingHistoryForUserByLibrarian(999L));
    }

    @Test
    void getAllBorrowingHistory_shouldReturnAllRecordsSorted() {
        fineRepository.deleteAll();
        borrowingRecordRepository.deleteAll();
        Book book1 = bookRepository.findById(availableBook.getId()).orElseThrow();
        book1.setAvailableCopies(book1.getTotalCopies());
        bookRepository.save(book1);

        Book book2 = bookRepository.findById(anotherAvailableBook.getId()).orElseThrow();
        book2.setAvailableCopies(book2.getTotalCopies());
        bookRepository.save(book2);

        User user1 = userRepository.findById(patronUser.getId()).orElseThrow();
        User user2 = User.builder()
                .username("patron2_hist")
                .password(passwordEncoder.encode("password456"))
                .email("p2hist@example.com")
                .fullName("Patron Two History")
                .role(Role.PATRON)
                .build();
        userRepository.save(user2);

        BorrowBookRequest req1 = new BorrowBookRequest();
        req1.setBookId(book1.getId());
        BorrowingRecordResponse resp1 = borrowingService.borrowBook(req1, user1);
        BorrowingRecord record1 = borrowingRecordRepository.findById(resp1.getId()).orElseThrow();
        record1.setBorrowDate(LocalDate.now().minusDays(2));
        borrowingRecordRepository.save(record1);

        BorrowBookRequest req2 = new BorrowBookRequest();
        req2.setBookId(book2.getId());
        BorrowingRecordResponse resp2 = borrowingService.borrowBook(req2, user2);
        BorrowingRecord record2 = borrowingRecordRepository.findById(resp2.getId()).orElseThrow();
        record2.setBorrowDate(LocalDate.now().minusDays(1));
        borrowingRecordRepository.save(record2);

        List<BorrowingRecordResponse> history = borrowingService.getAllBorrowingHistory();

        assertEquals(2, history.size());
        assertEquals(record2.getId(), history.get(0).getId(), "Daha yeni tarihli kayıt listenin başında olmalı.");
        assertEquals(record1.getId(), history.get(1).getId());

        assertEquals(LocalDate.now().minusDays(1), history.get(0).getBorrowDate());
        assertEquals(LocalDate.now().minusDays(2), history.get(1).getBorrowDate());
    }

    @Test
    void getOverdueBooks_shouldReturnOnlyOverdueAndNotReturnedRecords() {
        BorrowBookRequest onTimeReq = new BorrowBookRequest();
        onTimeReq.setBookId(availableBook.getId());
        onTimeReq.setDueDate(LocalDate.now().plusDays(1));
        borrowingService.borrowBook(onTimeReq, patronUser);

        BorrowBookRequest overdueReq = new BorrowBookRequest();
        overdueReq.setBookId(anotherAvailableBook.getId());
        BorrowingRecordResponse overdueRecordResp = borrowingService.borrowBook(overdueReq, patronUser);
        BorrowingRecord overdueRecordEntity = borrowingRecordRepository.findById(overdueRecordResp.getId()).orElseThrow();
        overdueRecordEntity.setBorrowDate(LocalDate.now().minusDays(10));
        overdueRecordEntity.setDueDate(LocalDate.now().minusDays(2));
        borrowingRecordRepository.save(overdueRecordEntity);

        List<BorrowingRecordResponse> overdueBooks = borrowingService.getOverdueBooks();
        assertEquals(1, overdueBooks.size());
        assertEquals(anotherAvailableBook.getId(), overdueBooks.get(0).getBookId());
        assertNull(overdueBooks.get(0).getReturnDate());
    }
}
