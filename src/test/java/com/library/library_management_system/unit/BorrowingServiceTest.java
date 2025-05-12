package com.library.library_management_system.unit;

import com.library.library_management_system.dto.request.BorrowBookRequest;
import com.library.library_management_system.dto.response.BorrowingRecordResponse;
import com.library.library_management_system.dto.response.FineResponse;
import com.library.library_management_system.entity.Book;
import com.library.library_management_system.entity.BorrowingRecord;
import com.library.library_management_system.entity.Role;
import com.library.library_management_system.entity.User;
import com.library.library_management_system.exception.BookUnavailableException;
import com.library.library_management_system.exception.ResourceNotFoundException;
import com.library.library_management_system.repository.BookRepository;
import com.library.library_management_system.repository.BorrowingRecordRepository;
import com.library.library_management_system.repository.UserRepository;
import com.library.library_management_system.service.BorrowingService;
import com.library.library_management_system.service.FineService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@ActiveProfiles("test")
class BorrowingServiceTest {

    @Mock
    private BorrowingRecordRepository borrowingRecordRepository;

    @Mock
    private BookRepository bookRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private FineService fineService;

    @InjectMocks
    private BorrowingService borrowingService;

    private User user;
    private User librarian;
    private Book book;
    private BorrowBookRequest borrowBookRequest;
    private BorrowingRecord borrowingRecord;
    private BorrowingRecord overdueBorrowingRecord;


    @BeforeEach
    void setUp() {
        user = User.builder().id(1L).username("patron").role(Role.PATRON).build();
        librarian = User.builder().id(2L).username("librarian").role(Role.LIBRARIAN).build();
        book = Book.builder().id(1L).title("Test Book").availableCopies(1).totalCopies(1).build();

        borrowBookRequest = new BorrowBookRequest();
        borrowBookRequest.setBookId(1L);

        borrowingRecord = BorrowingRecord.builder()
                .id(1L)
                .user(user)
                .book(book)
                .borrowDate(LocalDate.now().minusDays(5))
                .dueDate(LocalDate.now().plusWeeks(1))
                .returnDate(null)
                .build();

        overdueBorrowingRecord = BorrowingRecord.builder()
                .id(2L)
                .user(user)
                .book(book)
                .borrowDate(LocalDate.now().minusDays(20))
                .dueDate(LocalDate.now().minusDays(5))
                .returnDate(null)
                .build();
    }

    @Test
    void borrowBook_whenBookAvailableAndUserEligible_shouldCreateBorrowingRecord() {
        when(bookRepository.findById(1L)).thenReturn(Optional.of(book));
        when(borrowingRecordRepository.existsByBookAndUserAndReturnDateIsNull(book, user)).thenReturn(false);
        when(borrowingRecordRepository.save(any(BorrowingRecord.class))).thenAnswer(invocation -> invocation.getArgument(0));
        BorrowingRecordResponse response = borrowingService.borrowBook(borrowBookRequest, user);

        assertNotNull(response);
        assertEquals(book.getId(), response.getBookId());
        assertEquals(user.getId(), response.getUserId());
        assertEquals(0, book.getAvailableCopies());
        verify(bookRepository, times(1)).save(book);
        verify(borrowingRecordRepository, times(1)).save(any(BorrowingRecord.class));
    }

    @Test
    void borrowBook_whenBookNotFound_shouldThrowResourceNotFoundException() {
        when(bookRepository.findById(1L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> borrowingService.borrowBook(borrowBookRequest, user));
        verify(borrowingRecordRepository, never()).save(any(BorrowingRecord.class));
    }

    @Test
    void borrowBook_whenBookNotAvailable_shouldThrowBookUnavailableException() {
        book.setAvailableCopies(0);
        when(bookRepository.findById(1L)).thenReturn(Optional.of(book));

        assertThrows(BookUnavailableException.class, () -> borrowingService.borrowBook(borrowBookRequest, user));
        verify(borrowingRecordRepository, never()).save(any(BorrowingRecord.class));
    }

    @Test
    void borrowBook_whenUserAlreadyBorrowedBook_shouldThrowIllegalStateException() {
        when(bookRepository.findById(1L)).thenReturn(Optional.of(book));
        when(borrowingRecordRepository.existsByBookAndUserAndReturnDateIsNull(book, user)).thenReturn(true);

        assertThrows(IllegalStateException.class, () -> borrowingService.borrowBook(borrowBookRequest, user));
        verify(borrowingRecordRepository, never()).save(any(BorrowingRecord.class));
    }


    @Test
    void returnBook_whenRecordExistsForUserAndReturnedOnTime_shouldUpdateRecordAndBook() {
        book.setAvailableCopies(0);

        when(borrowingRecordRepository.findByIdAndUserAndReturnDateIsNull(borrowingRecord.getId(), user))
                .thenReturn(Optional.of(borrowingRecord));
        when(borrowingRecordRepository.save(any(BorrowingRecord.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(bookRepository.save(any(Book.class))).thenReturn(book);
        when(fineService.createOrUpdateFineForOverdueBook(any(BorrowingRecord.class))).thenReturn(null);

        BorrowingRecordResponse response = borrowingService.returnBook(borrowingRecord.getId(), user);

        assertNotNull(response);
        assertEquals(LocalDate.now(), response.getReturnDate());
        assertEquals(1, book.getAvailableCopies());
        verify(bookRepository, times(1)).save(book);
        verify(borrowingRecordRepository, times(1)).save(borrowingRecord);
        verify(fineService, times(1)).createOrUpdateFineForOverdueBook(borrowingRecord);
    }

    @Test
    void returnBook_whenRecordExistsForUserAndReturnedOverdue_shouldGenerateFine() {
        book.setAvailableCopies(0);
        when(borrowingRecordRepository.findByIdAndUserAndReturnDateIsNull(overdueBorrowingRecord.getId(), user))
                .thenReturn(Optional.of(overdueBorrowingRecord));
        when(borrowingRecordRepository.save(any(BorrowingRecord.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(bookRepository.save(any(Book.class))).thenReturn(book);

        FineResponse mockFine = FineResponse.builder().id(1L).amount(new BigDecimal("5.00")).build();
        when(fineService.createOrUpdateFineForOverdueBook(any(BorrowingRecord.class))).thenReturn(mockFine);

        BorrowingRecordResponse response = borrowingService.returnBook(overdueBorrowingRecord.getId(), user);

        assertNotNull(response);
        assertEquals(LocalDate.now(), response.getReturnDate());
        assertEquals(1, book.getAvailableCopies());
        verify(fineService, times(1)).createOrUpdateFineForOverdueBook(overdueBorrowingRecord);
    }


    @Test
    void returnBookByLibrarian_whenRecordExistsAndReturnedOnTime_shouldUpdateRecordAndBook() {
        book.setAvailableCopies(0);
        when(borrowingRecordRepository.findByIdAndReturnDateIsNull(borrowingRecord.getId()))
                .thenReturn(Optional.of(borrowingRecord));
        when(borrowingRecordRepository.save(any(BorrowingRecord.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(bookRepository.save(any(Book.class))).thenReturn(book);
        when(fineService.createOrUpdateFineForOverdueBook(any(BorrowingRecord.class))).thenReturn(null);

        BorrowingRecordResponse response = borrowingService.returnBookByLibrarian(borrowingRecord.getId());

        assertNotNull(response);
        assertEquals(LocalDate.now(), response.getReturnDate());
        assertEquals(1, book.getAvailableCopies());
        verify(fineService, times(1)).createOrUpdateFineForOverdueBook(borrowingRecord);
    }

    @Test
    void returnBook_whenRecordAlreadyReturned_shouldNotProcessAgainAndReturnExistingRecord() {
        LocalDate alreadyReturnedDate = LocalDate.now().minusDays(1);
        borrowingRecord.setReturnDate(alreadyReturnedDate);
        book.setAvailableCopies(1);

        when(borrowingRecordRepository.findByIdAndUserAndReturnDateIsNull(borrowingRecord.getId(), user))
                .thenReturn(Optional.empty());
        when(borrowingRecordRepository.findByIdAndUserAndReturnDateIsNull(1L, user)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> borrowingService.returnBook(1L, user));

        verify(fineService, never()).createOrUpdateFineForOverdueBook(any());
        verify(bookRepository, never()).save(any());
    }

    @Test
    void getUserBorrowingHistory_shouldReturnListOfRecords() {
        when(borrowingRecordRepository.findByUserOrderByBorrowDateDesc(user)).thenReturn(Collections.singletonList(borrowingRecord));

        List<BorrowingRecordResponse> history = borrowingService.getUserBorrowingHistory(user);

        assertNotNull(history);
        assertFalse(history.isEmpty());
        assertEquals(1, history.size());
        assertEquals(borrowingRecord.getId(), history.get(0).getId());
    }

    @Test
    void getBorrowingHistoryForUserByLibrarian_whenUserExists_shouldReturnHistory() {
        Long userIdToFind = 1L;
        when(userRepository.findById(userIdToFind)).thenReturn(Optional.of(user));
        when(borrowingRecordRepository.findByUserOrderByBorrowDateDesc(user))
                .thenReturn(Collections.singletonList(borrowingRecord));

        List<BorrowingRecordResponse> history = borrowingService.getBorrowingHistoryForUserByLibrarian(userIdToFind);

        assertNotNull(history);
        assertFalse(history.isEmpty());
        assertEquals(borrowingRecord.getId(), history.get(0).getId());
        verify(userRepository, times(1)).findById(userIdToFind);
    }

    @Test
    void getBorrowingHistoryForUserByLibrarian_whenUserNotExists_shouldThrowResourceNotFound() {
        Long nonExistentUserId = 99L;
        when(userRepository.findById(nonExistentUserId)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> borrowingService.getBorrowingHistoryForUserByLibrarian(nonExistentUserId));
        verify(borrowingRecordRepository, never()).findByUserOrderByBorrowDateDesc(any());
    }


    @Test
    void getAllBorrowingHistory_shouldReturnAllRecords() {

        when(borrowingRecordRepository.findAll(any(org.springframework.data.domain.Sort.class)))
                .thenReturn(Collections.singletonList(borrowingRecord));

        List<BorrowingRecordResponse> history = borrowingService.getAllBorrowingHistory();

        assertNotNull(history);
        assertFalse(history.isEmpty());
        assertEquals(1, history.size());
    }

    @Test
    void getOverdueBooks_shouldReturnListOfOverdueRecords() {
        when(borrowingRecordRepository.findByReturnDateIsNullAndDueDateBefore(any(LocalDate.class)))
                .thenReturn(Collections.singletonList(overdueBorrowingRecord));

        List<BorrowingRecordResponse> overdueBooks = borrowingService.getOverdueBooks();

        assertNotNull(overdueBooks);
        assertFalse(overdueBooks.isEmpty());
        assertEquals(1, overdueBooks.size());
        assertEquals(overdueBorrowingRecord.getId(), overdueBooks.get(0).getId());
    }
}