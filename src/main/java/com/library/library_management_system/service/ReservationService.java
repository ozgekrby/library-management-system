package com.library.library_management_system.service;

import com.library.library_management_system.dto.request.CreateReservationRequest;
import com.library.library_management_system.dto.response.ReservationResponse;
import com.library.library_management_system.entity.*;
import com.library.library_management_system.exception.BookUnavailableException;
import com.library.library_management_system.exception.ResourceNotFoundException;
import com.library.library_management_system.repository.BookRepository;
import com.library.library_management_system.repository.ReservationRepository;
import com.library.library_management_system.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReservationService {

    private final ReservationRepository reservationRepository;
    private final BookRepository bookRepository;
    private final UserRepository userRepository;

    @Value("${app.reservation.hold-duration-hours:48}")
    private int reservationHoldDurationHours;

    @Transactional
    public ReservationResponse createReservation(CreateReservationRequest request, User currentUser) {
        Book book = bookRepository.findById(request.getBookId())
                .orElseThrow(() -> new ResourceNotFoundException("Book not found with id: " + request.getBookId()));

        if (book.getAvailableCopies() > 0) {
            throw new BookUnavailableException("Book '" + book.getTitle() + "' is currently available. Reservation is not needed.");
        }

        boolean alreadyReserved = reservationRepository.existsByUserAndBookAndStatusIn(
                currentUser,
                book,
                Arrays.asList(ReservationStatus.PENDING, ReservationStatus.AVAILABLE)
        );
        if (alreadyReserved) {
            throw new IllegalStateException("You already have an active reservation for the book: " + book.getTitle());
        }


        Reservation reservation = Reservation.builder()
                .book(book)
                .user(currentUser)
                .reservationDateTime(LocalDateTime.now())
                .status(ReservationStatus.PENDING)
                .build();

        Reservation savedReservation = reservationRepository.save(reservation);
        log.info("Reservation created for book '{}' by user '{}'", book.getTitle(), currentUser.getUsername());
        return mapToReservationResponse(savedReservation);
    }

    @Transactional
    public void cancelReservation(Long reservationId, User currentUser) {
        Reservation reservation = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new ResourceNotFoundException("Reservation not found with id: " + reservationId));

        if (!reservation.getUser().getId().equals(currentUser.getId()) && currentUser.getRole() != Role.LIBRARIAN) {
            throw new org.springframework.security.access.AccessDeniedException("You are not authorized to cancel this reservation.");
        }

        ReservationStatus originalStatus = reservation.getStatus();

        if (originalStatus != ReservationStatus.PENDING && originalStatus != ReservationStatus.AVAILABLE) {
            throw new IllegalStateException("Only PENDING or AVAILABLE reservations can be canceled. Current status: " + originalStatus);
        }

        reservation.setStatus(ReservationStatus.CANCELED);
        reservationRepository.save(reservation);
        log.info("Reservation with id {} canceled by user '{}'", reservationId, currentUser.getUsername());

        if (originalStatus == ReservationStatus.AVAILABLE) {
            log.debug("Canceled reservation (ID: {}) was in AVAILABLE state. Processing next reservation for book ID: {}", reservationId, reservation.getBook().getId());
            processNextReservationForBook(reservation.getBook());
        }
    }

    @Transactional(readOnly = true)
    public List<ReservationResponse> getMyActiveReservations(User currentUser) {
        List<ReservationStatus> activeStatuses = Arrays.asList(ReservationStatus.PENDING, ReservationStatus.AVAILABLE);
        return reservationRepository.findByUserAndStatusInOrderByReservationDateTimeAsc(currentUser, activeStatuses)
                .stream()
                .map(this::mapToReservationResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<ReservationResponse> getReservationsForBook(Long bookId) {
        Book book = bookRepository.findById(bookId)
                .orElseThrow(() -> new ResourceNotFoundException("Book not found with id: " + bookId));
        return reservationRepository.findByBookAndStatusOrderByReservationDateTimeAsc(book, ReservationStatus.PENDING)
                .stream()
                .map(this::mapToReservationResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<ReservationResponse> getAllPendingReservations() {
        return reservationRepository.findAll().stream()
                .filter(r -> r.getStatus() == ReservationStatus.PENDING || r.getStatus() == ReservationStatus.AVAILABLE)
                .map(this::mapToReservationResponse)
                .collect(Collectors.toList());
    }

    @Transactional
    public void processNextReservationForBook(Book book) {
        reservationRepository.findFirstByBookAndStatusOrderByReservationDateTimeAsc(book, ReservationStatus.PENDING)
                .ifPresent(nextReservation -> {
                    nextReservation.setStatus(ReservationStatus.AVAILABLE);
                    nextReservation.setExpirationDateTime(LocalDateTime.now().plusHours(reservationHoldDurationHours));
                    reservationRepository.save(nextReservation);
                    log.info("Book '{}' is now available for user '{}' (Reservation ID: {}). Expires at {}",
                            book.getTitle(), nextReservation.getUser().getUsername(), nextReservation.getId(), nextReservation.getExpirationDateTime());
                });
    }

    @Transactional
    public void fulfillReservation(Book book, User user) {
        reservationRepository.findByUserAndBookAndStatusIn(user, book, List.of(ReservationStatus.AVAILABLE))
                .stream().findFirst()
                .ifPresent(reservation -> {
                    reservation.setStatus(ReservationStatus.FULFILLED);
                    reservationRepository.save(reservation);
                    log.info("Reservation ID: {} for book '{}' by user '{}' has been fulfilled.",
                            reservation.getId(), book.getTitle(), user.getUsername());
                });
    }

    @Transactional
    public void expireReservations() {
        List<Reservation> availableReservations = reservationRepository.findAll().stream()
                .filter(r -> r.getStatus() == ReservationStatus.AVAILABLE && r.getExpirationDateTime() != null && r.getExpirationDateTime().isBefore(LocalDateTime.now()))
                .toList();

        for (Reservation reservation : availableReservations) {
            reservation.setStatus(ReservationStatus.EXPIRED);
            reservationRepository.save(reservation);
            log.warn("Reservation ID: {} for book '{}' by user '{}' has expired.",
                    reservation.getId(), reservation.getBook().getTitle(), reservation.getUser().getUsername());
            processNextReservationForBook(reservation.getBook());
        }
    }


    private ReservationResponse mapToReservationResponse(Reservation reservation) {
        return ReservationResponse.builder()
                .id(reservation.getId())
                .bookId(reservation.getBook().getId())
                .bookTitle(reservation.getBook().getTitle())
                .userId(reservation.getUser().getId())
                .username(reservation.getUser().getUsername())
                .reservationDateTime(reservation.getReservationDateTime())
                .status(reservation.getStatus())
                .expirationDateTime(reservation.getExpirationDateTime())
                .build();
    }
}
