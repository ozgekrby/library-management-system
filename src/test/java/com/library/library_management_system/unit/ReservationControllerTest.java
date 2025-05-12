package com.library.library_management_system.unit;

import com.library.library_management_system.controller.ReservationController;
import com.library.library_management_system.dto.request.CreateReservationRequest;
import com.library.library_management_system.dto.response.ReservationResponse;
import com.library.library_management_system.entity.Book;
import com.library.library_management_system.entity.Role;
import com.library.library_management_system.entity.ReservationStatus;
import com.library.library_management_system.entity.User;
import com.library.library_management_system.exception.BookUnavailableException;
import com.library.library_management_system.exception.ResourceNotFoundException;
import com.library.library_management_system.service.ReservationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReservationControllerTest {

    @Mock
    private ReservationService reservationService;

    @InjectMocks
    private ReservationController reservationController;

    private User patronUser;
    private User librarianUser;
    private Book book;
    private CreateReservationRequest createReservationRequest;
    private ReservationResponse reservationResponse;


    @BeforeEach
    void setUp() {
        patronUser = User.builder().id(1L).username("resPatron1").role(Role.PATRON).fullName("Res Patron 1").build();
        librarianUser = User.builder().id(2L).username("resLibrarian").role(Role.LIBRARIAN).fullName("Res Librarian").build();
        book = Book.builder().id(101L).title("Unavailable Book One").availableCopies(0).build();

        createReservationRequest = new CreateReservationRequest();
        createReservationRequest.setBookId(book.getId());

        reservationResponse = ReservationResponse.builder()
                .id(1L)
                .bookId(book.getId())
                .bookTitle(book.getTitle())
                .userId(patronUser.getId())
                .username(patronUser.getUsername())
                .reservationDateTime(LocalDateTime.now())
                .status(ReservationStatus.PENDING)
                .build();
    }

    @Test
    void createReservation_whenValidRequest_shouldCallServiceAndReturnCreated() {
        when(reservationService.createReservation(any(CreateReservationRequest.class), any(User.class)))
                .thenReturn(reservationResponse);
        ResponseEntity<ReservationResponse> responseEntity =
                reservationController.createReservation(createReservationRequest, patronUser);

        assertNotNull(responseEntity);
        assertEquals(HttpStatus.CREATED, responseEntity.getStatusCode());
        assertEquals(reservationResponse, responseEntity.getBody());
        verify(reservationService, times(1)).createReservation(createReservationRequest, patronUser);
    }

    @Test
    void createReservation_whenServiceThrowsBookUnavailableException_shouldPropagateException() {
        when(reservationService.createReservation(any(CreateReservationRequest.class), any(User.class)))
                .thenThrow(new BookUnavailableException("Book is available"));
        BookUnavailableException exception = assertThrows(BookUnavailableException.class, () -> {
            reservationController.createReservation(createReservationRequest, patronUser);
        });
        assertEquals("Book is available", exception.getMessage());
        verify(reservationService, times(1)).createReservation(createReservationRequest, patronUser);
    }

    @Test
    void cancelReservation_byOwner_shouldCallServiceAndReturnNoContent() {
        Long reservationId = 1L;
        doNothing().when(reservationService).cancelReservation(anyLong(), any(User.class));
        ResponseEntity<Void> responseEntity =
                reservationController.cancelReservation(reservationId, patronUser);

        assertNotNull(responseEntity);
        assertEquals(HttpStatus.NO_CONTENT, responseEntity.getStatusCode());
        verify(reservationService, times(1)).cancelReservation(reservationId, patronUser);
    }

    @Test
    void cancelReservation_byLibrarian_shouldCallServiceAndReturnNoContent() {
        Long reservationId = 1L;
        doNothing().when(reservationService).cancelReservation(anyLong(), any(User.class));
        ResponseEntity<Void> responseEntity =
                reservationController.cancelReservation(reservationId, librarianUser);

        assertNotNull(responseEntity);
        assertEquals(HttpStatus.NO_CONTENT, responseEntity.getStatusCode());
        verify(reservationService, times(1)).cancelReservation(reservationId, librarianUser);
    }


    @Test
    void cancelReservation_whenServiceThrowsAccessDenied_shouldPropagateException() {
        Long reservationId = 1L;
        doThrow(new AccessDeniedException("Not authorized")).when(reservationService).cancelReservation(reservationId, patronUser);

        AccessDeniedException exception = assertThrows(AccessDeniedException.class, () -> {
            reservationController.cancelReservation(reservationId, patronUser);
        });
        assertEquals("Not authorized", exception.getMessage());
    }


    @Test
    void getMyActiveReservations_shouldCallServiceAndReturnOk() {
        List<ReservationResponse> activeReservations = Collections.singletonList(reservationResponse);
        when(reservationService.getMyActiveReservations(any(User.class))).thenReturn(activeReservations);

        ResponseEntity<List<ReservationResponse>> responseEntity =
                reservationController.getMyActiveReservations(patronUser);

        assertNotNull(responseEntity);
        assertEquals(HttpStatus.OK, responseEntity.getStatusCode());
        assertEquals(activeReservations, responseEntity.getBody());
        verify(reservationService, times(1)).getMyActiveReservations(patronUser);
    }

    @Test
    void getReservationsForBook_shouldCallServiceAndReturnOk() {
        Long bookId = book.getId();
        List<ReservationResponse> reservationsForBook = Collections.singletonList(reservationResponse);
        when(reservationService.getReservationsForBook(bookId)).thenReturn(reservationsForBook);

        ResponseEntity<List<ReservationResponse>> responseEntity =
                reservationController.getReservationsForBook(bookId);
        assertNotNull(responseEntity);
        assertEquals(HttpStatus.OK, responseEntity.getStatusCode());
        assertEquals(reservationsForBook, responseEntity.getBody());
        verify(reservationService, times(1)).getReservationsForBook(bookId);
    }

    @Test
    void getAllActiveReservations_shouldCallServiceAndReturnOk() {
        List<ReservationResponse> allActiveReservations = Collections.singletonList(reservationResponse);
        when(reservationService.getAllPendingReservations()).thenReturn(allActiveReservations);

        ResponseEntity<List<ReservationResponse>> responseEntity =
                reservationController.getAllActiveReservations();

        assertNotNull(responseEntity);
        assertEquals(HttpStatus.OK, responseEntity.getStatusCode());
        assertEquals(allActiveReservations, responseEntity.getBody());
        verify(reservationService, times(1)).getAllPendingReservations();
    }

    @Test
    void manuallyExpireReservations_shouldCallServiceAndReturnOk() {
        doNothing().when(reservationService).expireReservations();

        ResponseEntity<String> responseEntity = reservationController.manuallyExpireReservations();

        assertNotNull(responseEntity);
        assertEquals(HttpStatus.OK, responseEntity.getStatusCode());
        assertEquals("Expired reservations check completed.", responseEntity.getBody());
        verify(reservationService, times(1)).expireReservations();
    }
}
