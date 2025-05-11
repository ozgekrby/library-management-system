package com.library.library_management_system.controller;

import com.library.library_management_system.dto.response.FineResponse;
import com.library.library_management_system.entity.FineStatus;
import com.library.library_management_system.entity.User;
import com.library.library_management_system.service.FineService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/fines")
@RequiredArgsConstructor
@Tag(name = "Fine Management", description = "APIs for managing library fines")
@SecurityRequirement(name = "bearerAuth")
public class FineController {

    private final FineService fineService;

    @GetMapping("/me")
    @PreAuthorize("hasAnyRole('PATRON', 'LIBRARIAN')")
    @Operation(summary = "Get all fines for the authenticated user")
    public ResponseEntity<List<FineResponse>> getMyFines(@AuthenticationPrincipal User currentUser) {
        List<FineResponse> fines = fineService.getFinesForUser(currentUser.getId());
        return ResponseEntity.ok(fines);
    }

    @GetMapping("/user/{userId}")
    @PreAuthorize("hasRole('LIBRARIAN')")
    @Operation(summary = "Get all fines for a specific user (Librarian only)")
    public ResponseEntity<List<FineResponse>> getFinesForUserByLibrarian(@PathVariable Long userId) {
        List<FineResponse> fines = fineService.getFinesForUser(userId);
        return ResponseEntity.ok(fines);
    }

    @GetMapping("/pending")
    @PreAuthorize("hasRole('LIBRARIAN')")
    @Operation(summary = "Get all pending fines (Librarian only)")
    public ResponseEntity<List<FineResponse>> getAllPendingFines() {
        List<FineResponse> fines = fineService.getAllFinesByStatus(FineStatus.PENDING);
        return ResponseEntity.ok(fines);
    }

    @GetMapping("/all")
    @PreAuthorize("hasRole('LIBRARIAN')")
    @Operation(summary = "Get all fines in the system (Librarian only)")
    public ResponseEntity<List<FineResponse>> getAllFines() {
        List<FineResponse> fines = fineService.getAllFines();
        return ResponseEntity.ok(fines);
    }

    @PutMapping("/{fineId}/pay")
    @PreAuthorize("hasRole('LIBRARIAN')")
    @Operation(summary = "Mark a fine as paid (Librarian only)")
    public ResponseEntity<FineResponse> payFine(@PathVariable Long fineId) {
        FineResponse paidFine = fineService.payFine(fineId);
        return ResponseEntity.ok(paidFine);
    }

    @PutMapping("/{fineId}/waive")
    @PreAuthorize("hasRole('LIBRARIAN')")
    @Operation(summary = "Waive a fine (Librarian only)")
    public ResponseEntity<FineResponse> waiveFine(@PathVariable Long fineId) {
        FineResponse waivedFine = fineService.waiveFine(fineId);
        return ResponseEntity.ok(waivedFine);
    }
}
