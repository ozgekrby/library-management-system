package com.library.library_management_system.unit;

import com.library.library_management_system.dto.request.CreateReservationRequest;
import com.library.library_management_system.dto.response.ReservationResponse;
import com.library.library_management_system.entity.*;
import com.library.library_management_system.exception.BookUnavailableException;
import com.library.library_management_system.exception.ResourceNotFoundException;
import com.library.library_management_system.repository.BookRepository;
import com.library.library_management_system.repository.ReservationRepository;
import com.library.library_management_system.repository.UserRepository;
import com.library.library_management_system.service.ReservationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@ActiveProfiles("test")
class ReservationServiceTest {

    @Mock
    private ReservationRepository reservationRepository;

    @Mock
    private BookRepository bookRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private ReservationService reservationService;

    private User patron1;
    private User patron2;
    private User librarian;
    private Book unavailableBook;
    private Book availableBook;
    private CreateReservationRequest createReservationRequest;
    private Reservation pendingReservation;
    private Reservation availableStateReservation;

    private final int HOLD_DURATION_HOURS = 24;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(reservationService, "reservationHoldDurationHours", HOLD_DURATION_HOURS);

        patron1 = User.builder().id(1L).username("patron1").role(Role.PATRON).build();
        patron2 = User.builder().id(2L).username("patron2").role(Role.PATRON).build();
        librarian = User.builder().id(3L).username("librarian").role(Role.LIBRARIAN).build();

        unavailableBook = Book.builder().id(101L).title("Unavailable Book").availableCopies(0).build();
        availableBook = Book.builder().id(102L).title("Available Book").availableCopies(1).build();

        createReservationRequest = new CreateReservationRequest();
        createReservationRequest.setBookId(unavailableBook.getId());

        pendingReservation = Reservation.builder()
                .id(1L)
                .book(unavailableBook)
                .user(patron1)
                .reservationDateTime(LocalDateTime.now().minusDays(1))
                .status(ReservationStatus.PENDING)
                .build();

        availableStateReservation = Reservation.builder()
                .id(2L)
                .book(unavailableBook)
                .user(patron2)
                .reservationDateTime(LocalDateTime.now().minusHours(HOLD_DURATION_HOURS + 2))
                .status(ReservationStatus.AVAILABLE)
                .expirationDateTime(LocalDateTime.now().minusHours(2))
                .build();
    }

    @Test
    void createReservation_whenBookNotFound_shouldThrowResourceNotFoundException() {
        when(bookRepository.findById(anyLong())).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class,
                () -> reservationService.createReservation(createReservationRequest, patron1));
    }

    @Test
    void createReservation_whenBookIsAvailable_shouldThrowBookUnavailableException() {
        createReservationRequest.setBookId(availableBook.getId());
        when(bookRepository.findById(availableBook.getId())).thenReturn(Optional.of(availableBook));
        assertThrows(BookUnavailableException.class,
                () -> reservationService.createReservation(createReservationRequest, patron1));
    }

    @Test
    void createReservation_whenUserAlreadyHasActiveReservationForBook_shouldThrowIllegalStateException() {
        when(bookRepository.findById(unavailableBook.getId())).thenReturn(Optional.of(unavailableBook));
        when(reservationRepository.existsByUserAndBookAndStatusIn(
                patron1, unavailableBook, Arrays.asList(ReservationStatus.PENDING, ReservationStatus.AVAILABLE)))
                .thenReturn(true);

        assertThrows(IllegalStateException.class,
                () -> reservationService.createReservation(createReservationRequest, patron1));
    }

    @Test
    void createReservation_whenSuccessful_shouldSaveAndReturnReservation() {
        when(bookRepository.findById(unavailableBook.getId())).thenReturn(Optional.of(unavailableBook));
        when(reservationRepository.existsByUserAndBookAndStatusIn(patron1, unavailableBook,
                Arrays.asList(ReservationStatus.PENDING, ReservationStatus.AVAILABLE))).thenReturn(false);
        when(reservationRepository.save(any(Reservation.class))).thenAnswer(invocation -> {
            Reservation saved = invocation.getArgument(0);
            saved.setId(5L);
            return saved;
        });

        ReservationResponse response = reservationService.createReservation(createReservationRequest, patron1);

        assertNotNull(response);
        assertEquals(unavailableBook.getId(), response.getBookId());
        assertEquals(patron1.getUsername(), response.getUsername());
        assertEquals(ReservationStatus.PENDING, response.getStatus());
        verify(reservationRepository, times(1)).save(any(Reservation.class));
    }

    @Test
    void cancelReservation_whenReservationNotFound_shouldThrowResourceNotFoundException() {
        when(reservationRepository.findById(anyLong())).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class,
                () -> reservationService.cancelReservation(1L, patron1));
    }

    @Test
    void cancelReservation_whenUserNotAuthorized_shouldThrowAccessDeniedException() {
        when(reservationRepository.findById(pendingReservation.getId())).thenReturn(Optional.of(pendingReservation));
        assertThrows(AccessDeniedException.class,
                () -> reservationService.cancelReservation(pendingReservation.getId(), patron2));
    }

    @Test
    void cancelReservation_whenStatusNotCancellable_shouldThrowIllegalStateException() {
        pendingReservation.setStatus(ReservationStatus.FULFILLED);
        when(reservationRepository.findById(pendingReservation.getId())).thenReturn(Optional.of(pendingReservation));
        assertThrows(IllegalStateException.class,
                () -> reservationService.cancelReservation(pendingReservation.getId(), patron1));
    }

    @Test
    void cancelReservation_byOwner_whenStatusIsPending_shouldCancel() {
        when(reservationRepository.findById(pendingReservation.getId())).thenReturn(Optional.of(pendingReservation));
        when(reservationRepository.save(any(Reservation.class))).thenReturn(pendingReservation);

        reservationService.cancelReservation(pendingReservation.getId(), patron1);

        assertEquals(ReservationStatus.CANCELED, pendingReservation.getStatus());
        verify(reservationRepository, times(1)).save(pendingReservation);
        verify(reservationRepository, never()).findFirstByBookAndStatusOrderByReservationDateTimeAsc(any(), eq(ReservationStatus.PENDING));
    }

    @Test
    void cancelReservation_byLibrarian_whenStatusIsAvailableAndNextReservationExists_shouldCancelAndProcessNext() {
        availableStateReservation.setUser(patron1);
        when(reservationRepository.findById(availableStateReservation.getId())).thenReturn(Optional.of(availableStateReservation));
        when(reservationRepository.save(any(Reservation.class))).thenReturn(availableStateReservation);

        Reservation nextPending = Reservation.builder().id(3L).book(unavailableBook).user(patron2).status(ReservationStatus.PENDING).reservationDateTime(LocalDateTime.now()).build();
        when(reservationRepository.findFirstByBookAndStatusOrderByReservationDateTimeAsc(unavailableBook, ReservationStatus.PENDING))
                .thenReturn(Optional.of(nextPending));

        reservationService.cancelReservation(availableStateReservation.getId(), librarian);

        assertEquals(ReservationStatus.CANCELED, availableStateReservation.getStatus());
        verify(reservationRepository, times(1)).save(availableStateReservation);
        verify(reservationRepository, times(1)).findFirstByBookAndStatusOrderByReservationDateTimeAsc(unavailableBook, ReservationStatus.PENDING);
        verify(reservationRepository, times(1)).save(nextPending);
        assertEquals(ReservationStatus.AVAILABLE, nextPending.getStatus());
        assertNotNull(nextPending.getExpirationDateTime());
    }

    @Test
    void getMyActiveReservations_shouldReturnActiveReservationsForCurrentUser() {
        List<ReservationStatus> activeStatuses = Arrays.asList(ReservationStatus.PENDING, ReservationStatus.AVAILABLE);
        when(reservationRepository.findByUserAndStatusInOrderByReservationDateTimeAsc(patron1, activeStatuses))
                .thenReturn(Collections.singletonList(pendingReservation));

        List<ReservationResponse> responses = reservationService.getMyActiveReservations(patron1);

        assertNotNull(responses);
        assertEquals(1, responses.size());
        assertEquals(pendingReservation.getId(), responses.get(0).getId());
    }

    @Test
    void getReservationsForBook_whenBookExists_shouldReturnPendingReservations() {
        when(bookRepository.findById(unavailableBook.getId())).thenReturn(Optional.of(unavailableBook));
        when(reservationRepository.findByBookAndStatusOrderByReservationDateTimeAsc(unavailableBook, ReservationStatus.PENDING))
                .thenReturn(Collections.singletonList(pendingReservation));

        List<ReservationResponse> responses = reservationService.getReservationsForBook(unavailableBook.getId());

        assertNotNull(responses);
        assertEquals(1, responses.size());
        assertEquals(pendingReservation.getBook().getTitle(), responses.get(0).getBookTitle());
    }

    @Test
    void processNextReservationForBook_whenPendingReservationExists_shouldMakeItAvailable() {
        when(reservationRepository.findFirstByBookAndStatusOrderByReservationDateTimeAsc(unavailableBook, ReservationStatus.PENDING))
                .thenReturn(Optional.of(pendingReservation));
        when(reservationRepository.save(any(Reservation.class))).thenReturn(pendingReservation);

        reservationService.processNextReservationForBook(unavailableBook);

        ArgumentCaptor<Reservation> reservationCaptor = ArgumentCaptor.forClass(Reservation.class);
        verify(reservationRepository).save(reservationCaptor.capture());

        Reservation savedReservation = reservationCaptor.getValue();
        assertEquals(ReservationStatus.AVAILABLE, savedReservation.getStatus());
        assertNotNull(savedReservation.getExpirationDateTime());
        assertTrue(savedReservation.getExpirationDateTime().isAfter(LocalDateTime.now()));
    }

    @Test
    void processNextReservationForBook_whenNoPendingReservation_shouldDoNothing() {
        when(reservationRepository.findFirstByBookAndStatusOrderByReservationDateTimeAsc(unavailableBook, ReservationStatus.PENDING))
                .thenReturn(Optional.empty());

        reservationService.processNextReservationForBook(unavailableBook);

        verify(reservationRepository, never()).save(any(Reservation.class));
    }

    @Test
    void fulfillReservation_whenAvailableReservationExists_shouldMarkAsFulfilled() {
        availableStateReservation.setUser(patron1);
        availableStateReservation.setStatus(ReservationStatus.AVAILABLE);
        when(reservationRepository.findByUserAndBookAndStatusIn(patron1, unavailableBook, List.of(ReservationStatus.AVAILABLE)))
                .thenReturn(Collections.singletonList(availableStateReservation));
        when(reservationRepository.save(any(Reservation.class))).thenReturn(availableStateReservation);

        reservationService.fulfillReservation(unavailableBook, patron1);

        assertEquals(ReservationStatus.FULFILLED, availableStateReservation.getStatus());
        verify(reservationRepository, times(1)).save(availableStateReservation);
    }

    @Test
    void fulfillReservation_whenNoAvailableReservation_shouldDoNothing() {
        when(reservationRepository.findByUserAndBookAndStatusIn(patron1, unavailableBook, List.of(ReservationStatus.AVAILABLE)))
                .thenReturn(Collections.emptyList());

        reservationService.fulfillReservation(unavailableBook, patron1);

        verify(reservationRepository, never()).save(any(Reservation.class));
    }

    @Test
    void expireReservations_whenAvailableReservationExpired_shouldMarkAsExpiredAndProcessNext() {
        when(reservationRepository.findAll()).thenReturn(Collections.singletonList(availableStateReservation));
        when(reservationRepository.save(availableStateReservation)).thenReturn(availableStateReservation);
        when(reservationRepository.findFirstByBookAndStatusOrderByReservationDateTimeAsc(unavailableBook, ReservationStatus.PENDING))
                .thenReturn(Optional.of(pendingReservation));
        when(reservationRepository.save(pendingReservation)).thenReturn(pendingReservation);

        reservationService.expireReservations();

        assertEquals(ReservationStatus.EXPIRED, availableStateReservation.getStatus());
        assertEquals(ReservationStatus.AVAILABLE, pendingReservation.getStatus());
        assertNotNull(pendingReservation.getExpirationDateTime());

        verify(reservationRepository, times(1)).save(availableStateReservation);
        verify(reservationRepository, times(1)).save(pendingReservation);
        verify(reservationRepository, times(1)).findFirstByBookAndStatusOrderByReservationDateTimeAsc(unavailableBook, ReservationStatus.PENDING);
    }

    @Test
    void expireReservations_whenNoExpiredAvailableReservations_shouldDoNothing() {
        pendingReservation.setStatus(ReservationStatus.AVAILABLE);
        pendingReservation.setExpirationDateTime(LocalDateTime.now().plusHours(1));
        when(reservationRepository.findAll()).thenReturn(Collections.singletonList(pendingReservation));

        reservationService.expireReservations();

        assertEquals(ReservationStatus.AVAILABLE, pendingReservation.getStatus());
        verify(reservationRepository, never()).save(any(Reservation.class));
        verify(reservationRepository, never()).findFirstByBookAndStatusOrderByReservationDateTimeAsc(any(), any());
    }
}