package com.library.library_management_system.controller;

import com.library.library_management_system.dto.request.CreateReservationRequest;
import com.library.library_management_system.dto.response.ReservationResponse;
import com.library.library_management_system.entity.User;
import com.library.library_management_system.service.ReservationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/reservations")
@RequiredArgsConstructor
@Tag(name = "Reservation Management", description = "APIs for managing book reservations")
@SecurityRequirement(name = "bearerAuth")
public class ReservationController {

    private final ReservationService reservationService;

    @PostMapping
    @PreAuthorize("hasRole('PATRON')")
    @Operation(summary = "Create a new reservation for a book")
    public ResponseEntity<ReservationResponse> createReservation(
            @Valid @RequestBody CreateReservationRequest request,
            @AuthenticationPrincipal User currentUser) {
        ReservationResponse response = reservationService.createReservation(request, currentUser);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    @DeleteMapping("/{reservationId}")
    @PreAuthorize("hasAnyRole('PATRON', 'LIBRARIAN')")
    @Operation(summary = "Cancel a reservation")
    public ResponseEntity<Void> cancelReservation(
            @PathVariable Long reservationId,
            @AuthenticationPrincipal User currentUser) {
        reservationService.cancelReservation(reservationId, currentUser);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me")
    @PreAuthorize("hasRole('PATRON')")
    @Operation(summary = "Get active reservations for the authenticated user")
    public ResponseEntity<List<ReservationResponse>> getMyActiveReservations(
            @AuthenticationPrincipal User currentUser) {
        List<ReservationResponse> reservations = reservationService.getMyActiveReservations(currentUser);
        return ResponseEntity.ok(reservations);
    }

    @GetMapping("/book/{bookId}")
    @PreAuthorize("hasRole('LIBRARIAN')")
    @Operation(summary = "Get all PENDING reservations for a specific book (Librarian only)")
    public ResponseEntity<List<ReservationResponse>> getReservationsForBook(
            @PathVariable Long bookId) {
        List<ReservationResponse> reservations = reservationService.getReservationsForBook(bookId);
        return ResponseEntity.ok(reservations);
    }

    @GetMapping("/active")
    @PreAuthorize("hasRole('LIBRARIAN')")
    @Operation(summary = "Get all active (PENDING or AVAILABLE) reservations (Librarian only)")
    public ResponseEntity<List<ReservationResponse>> getAllActiveReservations() {
        List<ReservationResponse> reservations = reservationService.getAllPendingReservations();
        return ResponseEntity.ok(reservations);
    }
    @PostMapping("/expire-check")
    @PreAuthorize("hasRole('LIBRARIAN')")
    @Operation(summary = "Manually trigger check for expired reservations (Librarian only)")
    public ResponseEntity<String> manuallyExpireReservations() {
        reservationService.expireReservations();
        return ResponseEntity.ok("Expired reservations check completed.");
    }
}
