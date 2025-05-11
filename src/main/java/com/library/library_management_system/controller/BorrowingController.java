package com.library.library_management_system.controller;

import com.library.library_management_system.dto.request.BorrowBookRequest;
import com.library.library_management_system.dto.response.BorrowingRecordResponse;
import com.library.library_management_system.entity.User;
import com.library.library_management_system.service.BorrowingService;
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
@RequestMapping("/api/borrow")
@RequiredArgsConstructor
@Tag(name = "Borrowing Management", description = "APIs for borrowing and returning books")
@SecurityRequirement(name = "bearerAuth")
public class BorrowingController {

    private final BorrowingService borrowingService;

    @PostMapping
    @PreAuthorize("hasAnyRole('PATRON', 'LIBRARIAN')")
    @Operation(summary = "Borrow a book (Patron or Librarian for self)")
    public ResponseEntity<BorrowingRecordResponse> borrowBook(
            @Valid @RequestBody BorrowBookRequest borrowRequest,
            @AuthenticationPrincipal User currentUser) {
        BorrowingRecordResponse response = borrowingService.borrowBook(borrowRequest, currentUser);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    @PutMapping("/return/{borrowingRecordId}")
    @PreAuthorize("hasAnyRole('PATRON', 'LIBRARIAN')")
    @Operation(summary = "Return a borrowed book (Patron for own, Librarian for any)")
    public ResponseEntity<BorrowingRecordResponse> returnBook(
            @PathVariable Long borrowingRecordId,
            @AuthenticationPrincipal User currentUser) {
        BorrowingRecordResponse response;
        if (currentUser.getRole().name().equals("LIBRARIAN")) {
            response = borrowingService.returnBookByLibrarian(borrowingRecordId);
        } else {
            response = borrowingService.returnBook(borrowingRecordId, currentUser);
        }
        return ResponseEntity.ok(response);
    }

    @GetMapping("/history/me")
    @PreAuthorize("hasAnyRole('PATRON', 'LIBRARIAN')")
    @Operation(summary = "Get borrowing history for the authenticated user")
    public ResponseEntity<List<BorrowingRecordResponse>> getMyBorrowingHistory(
            @AuthenticationPrincipal User currentUser) {
        List<BorrowingRecordResponse> history = borrowingService.getUserBorrowingHistory(currentUser);
        return ResponseEntity.ok(history);
    }

    @GetMapping("/history/user/{userId}")
    @PreAuthorize("hasRole('LIBRARIAN')")
    @Operation(summary = "Get borrowing history for a specific user (Librarian only)")
    public ResponseEntity<List<BorrowingRecordResponse>> getUserBorrowingHistoryByLibrarian(
            @PathVariable Long userId) {
        List<BorrowingRecordResponse> history = borrowingService.getBorrowingHistoryForUserByLibrarian(userId);
        return ResponseEntity.ok(history);
    }

    @GetMapping("/history/all")
    @PreAuthorize("hasRole('LIBRARIAN')")
    @Operation(summary = "Get all borrowing history (Librarian only)")
    public ResponseEntity<List<BorrowingRecordResponse>> getAllBorrowingHistory() {
        List<BorrowingRecordResponse> history = borrowingService.getAllBorrowingHistory();
        return ResponseEntity.ok(history);
    }

    @GetMapping("/overdue")
    @PreAuthorize("hasRole('LIBRARIAN')")
    @Operation(summary = "Get all overdue books (Librarian only)")
    public ResponseEntity<List<BorrowingRecordResponse>> getOverdueBooks() {
        List<BorrowingRecordResponse> overdueBooks = borrowingService.getOverdueBooks();
        return ResponseEntity.ok(overdueBooks);
    }
}
