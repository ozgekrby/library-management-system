package com.library.library_management_system.unit;

import com.library.library_management_system.controller.BorrowingController;
import com.library.library_management_system.dto.request.BorrowBookRequest;
import com.library.library_management_system.dto.response.BorrowingRecordResponse;
import com.library.library_management_system.entity.Book;
import com.library.library_management_system.entity.Role;
import com.library.library_management_system.entity.User;
import com.library.library_management_system.service.BorrowingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BorrowingControllerTest {

    @Mock
    private BorrowingService borrowingService;

    @InjectMocks
    private BorrowingController borrowingController;

    private User patronUser;
    private User librarianUser;
    private Book book;
    private BorrowBookRequest borrowBookRequest;
    private BorrowingRecordResponse borrowingRecordResponse;

    @BeforeEach
    void setUp() {
        patronUser = User.builder().id(1L).username("patron1").role(Role.PATRON).fullName("Patron User").build();
        librarianUser = User.builder().id(2L).username("librarian1").role(Role.LIBRARIAN).fullName("Librarian User").build();
        book = Book.builder().id(101L).title("Test Book").build();

        borrowBookRequest = new BorrowBookRequest();
        borrowBookRequest.setBookId(book.getId());
        borrowBookRequest.setDueDate(LocalDate.now().plusWeeks(2));

        borrowingRecordResponse = BorrowingRecordResponse.builder()
                .id(1L)
                .bookId(book.getId())
                .bookTitle(book.getTitle())
                .userId(patronUser.getId())
                .username(patronUser.getUsername())
                .borrowDate(LocalDate.now())
                .dueDate(LocalDate.now().plusWeeks(2))
                .build();
    }

    @Test
    void borrowBook_asPatron_shouldCallServiceAndReturnCreated() {
        when(borrowingService.borrowBook(any(BorrowBookRequest.class), any(User.class)))
                .thenReturn(borrowingRecordResponse);

        ResponseEntity<BorrowingRecordResponse> responseEntity =
                borrowingController.borrowBook(borrowBookRequest, patronUser);

        assertNotNull(responseEntity);
        assertEquals(HttpStatus.CREATED, responseEntity.getStatusCode());
        assertEquals(borrowingRecordResponse, responseEntity.getBody());
        verify(borrowingService, times(1)).borrowBook(borrowBookRequest, patronUser);
    }

    @Test
    void returnBook_byPatron_shouldCallReturnBookAndReturnOk() {
        Long borrowingRecordId = 1L;
        when(borrowingService.returnBook(eq(borrowingRecordId), any(User.class)))
                .thenReturn(borrowingRecordResponse);

        ResponseEntity<BorrowingRecordResponse> responseEntity =
                borrowingController.returnBook(borrowingRecordId, patronUser);

        assertNotNull(responseEntity);
        assertEquals(HttpStatus.OK, responseEntity.getStatusCode());
        assertEquals(borrowingRecordResponse, responseEntity.getBody());
        verify(borrowingService, times(1)).returnBook(borrowingRecordId, patronUser);
        verify(borrowingService, never()).returnBookByLibrarian(anyLong());
    }

    @Test
    void returnBook_byLibrarian_shouldCallReturnBookByLibrarianAndReturnOk() {
        Long borrowingRecordId = 1L;
        when(borrowingService.returnBookByLibrarian(eq(borrowingRecordId)))
                .thenReturn(borrowingRecordResponse);

        ResponseEntity<BorrowingRecordResponse> responseEntity =
                borrowingController.returnBook(borrowingRecordId, librarianUser);

        assertNotNull(responseEntity);
        assertEquals(HttpStatus.OK, responseEntity.getStatusCode());
        assertEquals(borrowingRecordResponse, responseEntity.getBody());
        verify(borrowingService, times(1)).returnBookByLibrarian(borrowingRecordId);
        verify(borrowingService, never()).returnBook(anyLong(), any(User.class));
    }


    @Test
    void getMyBorrowingHistory_shouldCallServiceAndReturnOk() {
        List<BorrowingRecordResponse> history = Collections.singletonList(borrowingRecordResponse);
        when(borrowingService.getUserBorrowingHistory(any(User.class))).thenReturn(history);

        ResponseEntity<List<BorrowingRecordResponse>> responseEntity =
                borrowingController.getMyBorrowingHistory(patronUser);

        assertNotNull(responseEntity);
        assertEquals(HttpStatus.OK, responseEntity.getStatusCode());
        assertEquals(history, responseEntity.getBody());
        verify(borrowingService, times(1)).getUserBorrowingHistory(patronUser);
    }

    @Test
    void getUserBorrowingHistoryByLibrarian_shouldCallServiceAndReturnOk() {
        Long targetUserId = patronUser.getId();
        List<BorrowingRecordResponse> history = Collections.singletonList(borrowingRecordResponse);
        when(borrowingService.getBorrowingHistoryForUserByLibrarian(eq(targetUserId))).thenReturn(history);

        ResponseEntity<List<BorrowingRecordResponse>> responseEntity =
                borrowingController.getUserBorrowingHistoryByLibrarian(targetUserId);

        assertNotNull(responseEntity);
        assertEquals(HttpStatus.OK, responseEntity.getStatusCode());
        assertEquals(history, responseEntity.getBody());
        verify(borrowingService, times(1)).getBorrowingHistoryForUserByLibrarian(targetUserId);
    }

    @Test
    void getAllBorrowingHistory_shouldCallServiceAndReturnOk() {
        List<BorrowingRecordResponse> history = Collections.singletonList(borrowingRecordResponse);
        when(borrowingService.getAllBorrowingHistory()).thenReturn(history);

        ResponseEntity<List<BorrowingRecordResponse>> responseEntity =
                borrowingController.getAllBorrowingHistory();

        assertNotNull(responseEntity);
        assertEquals(HttpStatus.OK, responseEntity.getStatusCode());
        assertEquals(history, responseEntity.getBody());
        verify(borrowingService, times(1)).getAllBorrowingHistory();
    }

    @Test
    void getOverdueBooks_shouldCallServiceAndReturnOk() {
        List<BorrowingRecordResponse> overdueBooks = Collections.singletonList(borrowingRecordResponse);
        when(borrowingService.getOverdueBooks()).thenReturn(overdueBooks);

        ResponseEntity<List<BorrowingRecordResponse>> responseEntity =
                borrowingController.getOverdueBooks();

        assertNotNull(responseEntity);
        assertEquals(HttpStatus.OK, responseEntity.getStatusCode());
        assertEquals(overdueBooks, responseEntity.getBody());
        verify(borrowingService, times(1)).getOverdueBooks();
    }
}
