package com.library.library_management_system.integration;

import com.library.library_management_system.dto.request.CreateReservationRequest;
import com.library.library_management_system.dto.response.ReservationResponse;
import com.library.library_management_system.entity.*;
import com.library.library_management_system.exception.BookUnavailableException;
import com.library.library_management_system.exception.ResourceNotFoundException;
import com.library.library_management_system.repository.BookRepository;
import com.library.library_management_system.repository.BorrowingRecordRepository;
import com.library.library_management_system.repository.FineRepository;
import com.library.library_management_system.repository.ReservationRepository;
import com.library.library_management_system.repository.UserRepository;
import com.library.library_management_system.service.ReservationService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
public class ReservationServiceTest {

    @Autowired
    private ReservationService reservationService;

    @Autowired
    private ReservationRepository reservationRepository;

    @Autowired
    private BookRepository bookRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private FineRepository fineRepository;

    @Autowired
    private BorrowingRecordRepository borrowingRecordRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private UserDetailsService userDetailsService;

    @Value("${library.reservation.hold-duration-hours:24}")
    private int reservationHoldDurationHours;


    private User patron1, patron2, librarianUser;
    private Book unavailableBook, availableBook, anotherUnavailableBook;

    @BeforeEach
    void setUp() {
        fineRepository.deleteAllInBatch();
        borrowingRecordRepository.deleteAllInBatch();
        reservationRepository.deleteAllInBatch();
        bookRepository.deleteAllInBatch();
        userRepository.deleteAllInBatch();

        patron1 = userRepository.save(User.builder().username("patron1").fullName("Patron One Name").password(passwordEncoder.encode("password123")).email("p1@mail.com").role(Role.PATRON).build());
        patron2 = userRepository.save(User.builder().username("patron2").fullName("Patron Two Name").password(passwordEncoder.encode("password123")).email("p2@mail.com").role(Role.PATRON).build());
        librarianUser = userRepository.save(User.builder().username("librarian").fullName("Librarian Name").password(passwordEncoder.encode("password123")).email("lib@mail.com").role(Role.LIBRARIAN).build());

        unavailableBook = bookRepository.save(Book.builder().title("Unavailable Book 1").author("Author U1").isbn("ISBN-U1").publicationDate(LocalDate.now().minusYears(1)).genre("Mystery").totalCopies(1).availableCopies(0).build());
        availableBook = bookRepository.save(Book.builder().title("Available Book 1").author("Author A1").isbn("ISBN-A1").publicationDate(LocalDate.now().minusYears(2)).genre("Sci-Fi").totalCopies(2).availableCopies(1).build());
        anotherUnavailableBook = bookRepository.save(Book.builder().title("Unavailable Book 2").author("Author U2").isbn("ISBN-U2").publicationDate(LocalDate.now().minusMonths(6)).genre("Fantasy").totalCopies(1).availableCopies(0).build());

        SecurityContextHolder.clearContext();
    }
    private void loginUser(User user) {
        UserDetails userDetails = userDetailsService.loadUserByUsername(user.getUsername());
        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                userDetails, null, userDetails.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }


    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void createReservation_whenBookNotFound_shouldThrowResourceNotFoundException() {
        loginUser(patron1);
        CreateReservationRequest request = new CreateReservationRequest();
        request.setBookId(999L);
        assertThrows(ResourceNotFoundException.class,
                () -> reservationService.createReservation(request, patron1));
    }

    @Test
    void createReservation_whenBookIsAvailable_shouldThrowBookUnavailableException() {
        loginUser(patron1);
        CreateReservationRequest request = new CreateReservationRequest();
        request.setBookId(availableBook.getId());

        assertThrows(BookUnavailableException.class,
                () -> reservationService.createReservation(request, patron1));
    }

    @Test
    void createReservation_whenUserAlreadyHasActiveReservationForBook_shouldThrowIllegalStateException() {
        loginUser(patron1);
        CreateReservationRequest request1 = new CreateReservationRequest();
        request1.setBookId(unavailableBook.getId());
        reservationService.createReservation(request1, patron1);

        CreateReservationRequest request2 = new CreateReservationRequest();
        request2.setBookId(unavailableBook.getId());
        assertThrows(IllegalStateException.class,
                () -> reservationService.createReservation(request2, patron1));
    }

    @Test
    void createReservation_whenSuccessful_shouldSaveAndReturnPendingReservation() {
        loginUser(patron1);
        CreateReservationRequest request = new CreateReservationRequest();
        request.setBookId(unavailableBook.getId());

        ReservationResponse response = reservationService.createReservation(request, patron1);

        assertNotNull(response);
        assertEquals(unavailableBook.getId(), response.getBookId());
        assertEquals(patron1.getUsername(), response.getUsername());
        assertEquals(ReservationStatus.PENDING, response.getStatus());
        assertNull(response.getExpirationDateTime());

        Optional<Reservation> savedReservation = reservationRepository.findById(response.getId());
        assertTrue(savedReservation.isPresent());
        assertEquals(ReservationStatus.PENDING, savedReservation.get().getStatus());
    }

    @Test
    void cancelReservation_byOwner_whenReservationNotFound_shouldThrowResourceNotFoundException() {
        loginUser(patron1);
        assertThrows(ResourceNotFoundException.class,
                () -> reservationService.cancelReservation(999L, patron1));
    }

    @Test
    @Transactional
    void cancelReservation_byAnotherPatron_shouldThrowAccessDeniedException() {
        loginUser(patron1);
        CreateReservationRequest request = new CreateReservationRequest();
        request.setBookId(unavailableBook.getId());
        ReservationResponse reservationByPatron1 = reservationService.createReservation(request, patron1);

        loginUser(patron2);
        final Long reservationId = reservationByPatron1.getId();
        assertThrows(AccessDeniedException.class,
                () -> reservationService.cancelReservation(reservationId, patron2));
    }

    @Test
    void cancelReservation_byOwner_whenStatusNotCancellable_shouldThrowIllegalStateException() {
        loginUser(patron1);
        Reservation reservation = reservationRepository.save(Reservation.builder()
                .book(unavailableBook).user(patron1).status(ReservationStatus.FULFILLED)
                .reservationDateTime(LocalDateTime.now()).build());

        final Long reservationId = reservation.getId();
        assertThrows(IllegalStateException.class,
                () -> reservationService.cancelReservation(reservationId, patron1));
    }

    @Test
    void cancelReservation_byOwner_whenStatusIsPending_shouldCancel() {
        loginUser(patron1);
        Reservation reservation = reservationRepository.save(Reservation.builder()
                .book(unavailableBook).user(patron1).status(ReservationStatus.PENDING)
                .reservationDateTime(LocalDateTime.now()).build());

        reservationService.cancelReservation(reservation.getId(), patron1);

        Reservation cancelledReservation = reservationRepository.findById(reservation.getId()).orElseThrow();
        assertEquals(ReservationStatus.CANCELED, cancelledReservation.getStatus());
    }

    @Test
    void cancelReservation_byLibrarian_whenStatusIsAvailableAndNextReservationExists_shouldCancelAndProcessNext() {
        loginUser(librarianUser);

        Reservation reservationForPatron1 = reservationRepository.save(Reservation.builder()
                .book(unavailableBook).user(patron1).status(ReservationStatus.AVAILABLE)
                .reservationDateTime(LocalDateTime.now().minusHours(1))
                .expirationDateTime(LocalDateTime.now().plusHours(reservationHoldDurationHours -1))
                .build());
        Reservation reservationForPatron2 = reservationRepository.save(Reservation.builder()
                .book(unavailableBook).user(patron2).status(ReservationStatus.PENDING)
                .reservationDateTime(LocalDateTime.now())
                .build());

        reservationService.cancelReservation(reservationForPatron1.getId(), librarianUser);

        Reservation cancelledRes = reservationRepository.findById(reservationForPatron1.getId()).orElseThrow();
        assertEquals(ReservationStatus.CANCELED, cancelledRes.getStatus());

        Reservation nextProcessedRes = reservationRepository.findById(reservationForPatron2.getId()).orElseThrow();
        assertEquals(ReservationStatus.AVAILABLE, nextProcessedRes.getStatus());
        assertNotNull(nextProcessedRes.getExpirationDateTime());
        assertTrue(nextProcessedRes.getExpirationDateTime().isAfter(LocalDateTime.now()));
    }

    @Test
    void getMyActiveReservations_shouldReturnActiveReservationsForCurrentUser() {
        loginUser(patron1);
        reservationRepository.save(Reservation.builder().book(unavailableBook).user(patron1).status(ReservationStatus.PENDING).reservationDateTime(LocalDateTime.now()).build());
        reservationRepository.save(Reservation.builder().book(anotherUnavailableBook).user(patron1).status(ReservationStatus.AVAILABLE).reservationDateTime(LocalDateTime.now()).expirationDateTime(LocalDateTime.now().plusHours(1)).build());
        reservationRepository.save(Reservation.builder().book(availableBook).user(patron1).status(ReservationStatus.CANCELED).reservationDateTime(LocalDateTime.now()).build());

        List<ReservationResponse> responses = reservationService.getMyActiveReservations(patron1);

        assertNotNull(responses);
        assertEquals(2, responses.size());
        assertTrue(responses.stream().anyMatch(r -> r.getStatus() == ReservationStatus.PENDING && r.getBookId().equals(unavailableBook.getId())));
        assertTrue(responses.stream().anyMatch(r -> r.getStatus() == ReservationStatus.AVAILABLE && r.getBookId().equals(anotherUnavailableBook.getId())));
    }

    @Test
    void getReservationsForBook_whenBookExists_shouldReturnPendingReservationsSorted() {
        loginUser(librarianUser);
        Reservation r1 = reservationRepository.save(Reservation.builder().book(unavailableBook).user(patron1).status(ReservationStatus.PENDING).reservationDateTime(LocalDateTime.now().minusHours(2)).build());
        Reservation r2 = reservationRepository.save(Reservation.builder().book(unavailableBook).user(patron2).status(ReservationStatus.PENDING).reservationDateTime(LocalDateTime.now().minusHours(1)).build());
        reservationRepository.save(Reservation.builder().book(unavailableBook).user(patron1).status(ReservationStatus.AVAILABLE).reservationDateTime(LocalDateTime.now()).expirationDateTime(LocalDateTime.now().plusHours(1)).build());

        List<ReservationResponse> responses = reservationService.getReservationsForBook(unavailableBook.getId());

        assertNotNull(responses);
        assertEquals(2, responses.size());
        assertEquals(r1.getId(), responses.get(0).getId());
        assertEquals(r2.getId(), responses.get(1).getId());
    }

    @Test
    @Transactional
    void processNextReservationForBook_whenPendingReservationExists_shouldMakeItAvailable() {
        System.out.println("TEST: reservationHoldDurationHours = " + this.reservationHoldDurationHours);

        Reservation pendingRes = reservationRepository.save(Reservation.builder()
                .book(unavailableBook).user(patron1).status(ReservationStatus.PENDING)
                .reservationDateTime(LocalDateTime.now().minusMinutes(5)).build());

        LocalDateTime beforeServiceCall = LocalDateTime.now();

        reservationService.processNextReservationForBook(unavailableBook);

        Reservation processedRes = reservationRepository.findById(pendingRes.getId()).orElseThrow();
        assertEquals(ReservationStatus.AVAILABLE, processedRes.getStatus());
        assertNotNull(processedRes.getExpirationDateTime());

        LocalDateTime expectedExpirationBase = beforeServiceCall.plusHours(this.reservationHoldDurationHours);
        LocalDateTime expectedMinExpiration = expectedExpirationBase.minusSeconds(5);
        LocalDateTime expectedMaxExpiration = expectedExpirationBase.plusSeconds(5);

        assertTrue(processedRes.getExpirationDateTime().isAfter(expectedMinExpiration) &&
                        processedRes.getExpirationDateTime().isBefore(expectedMaxExpiration),
                "ExpirationDateTime " + processedRes.getExpirationDateTime() +
                        " should be around " + expectedExpirationBase +
                        " (between " + expectedMinExpiration + " and " + expectedMaxExpiration + ")");
    }

    @Test
    void fulfillReservation_whenAvailableReservationExistsForUser_shouldMarkAsFulfilled() {
        loginUser(patron1);
        Reservation availableRes = reservationRepository.save(Reservation.builder()
                .book(unavailableBook).user(patron1).status(ReservationStatus.AVAILABLE)
                .reservationDateTime(LocalDateTime.now().minusHours(1))
                .expirationDateTime(LocalDateTime.now().plusHours(reservationHoldDurationHours - 1))
                .build());

        reservationService.fulfillReservation(unavailableBook, patron1);

        Reservation fulfilledRes = reservationRepository.findById(availableRes.getId()).orElseThrow();
        assertEquals(ReservationStatus.FULFILLED, fulfilledRes.getStatus());
    }

    @Test
    @Transactional
    void expireReservations_whenAvailableReservationExpired_shouldMarkAsExpiredAndProcessNext() {
        Reservation expiredRes = reservationRepository.save(Reservation.builder()
                .book(unavailableBook).user(patron1).status(ReservationStatus.AVAILABLE)
                .reservationDateTime(LocalDateTime.now().minusHours(reservationHoldDurationHours + 2))
                .expirationDateTime(LocalDateTime.now().minusHours(1))
                .build());

        Reservation pendingForPatron2 = reservationRepository.save(Reservation.builder()
                .book(unavailableBook).user(patron2).status(ReservationStatus.PENDING)
                .reservationDateTime(LocalDateTime.now().minusHours(reservationHoldDurationHours + 1))
                .build());

        reservationService.expireReservations();

        Reservation updatedExpiredRes = reservationRepository.findById(expiredRes.getId()).orElseThrow();
        assertEquals(ReservationStatus.EXPIRED, updatedExpiredRes.getStatus());

        Reservation nextProcessedRes = reservationRepository.findById(pendingForPatron2.getId()).orElseThrow();
        assertEquals(ReservationStatus.AVAILABLE, nextProcessedRes.getStatus());
        assertNotNull(nextProcessedRes.getExpirationDateTime());
    }
}
