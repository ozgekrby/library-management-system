package com.library.library_management_system.controller;

import com.library.library_management_system.dto.response.TopBorrowedBookResponse;
import com.library.library_management_system.dto.response.UserActivityReportResponse;
import com.library.library_management_system.service.ReportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/reports")
@RequiredArgsConstructor
@Tag(name = "Reports", description = "APIs for generating library reports (Librarian access only)")
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("hasRole('LIBRARIAN')")
public class ReportController {

    private final ReportService reportService;

    @GetMapping("/top-borrowed-books")
    @Operation(summary = "Get a report of the most borrowed books",
            description = "Returns a paginated list of books ordered by their borrow count in descending order.")
    public ResponseEntity<Page<TopBorrowedBookResponse>> getTopBorrowedBooks(
            @PageableDefault(size = 10)
            @Parameter(description = "Pagination and sorting information") Pageable pageable) {
        Page<TopBorrowedBookResponse> report = reportService.getTopBorrowedBooks(pageable);
        return ResponseEntity.ok(report);
    }

    @GetMapping("/user-activity")
    @Operation(summary = "Get a report of user activity",
            description = "Returns a paginated list of users with their total and active borrow counts.")
    public ResponseEntity<Page<UserActivityReportResponse>> getUserActivity(
            @PageableDefault(size = 10, sort = "username")
            @Parameter(description = "Pagination and sorting information") Pageable pageable) {
        Page<UserActivityReportResponse> report = reportService.getUserActivityReport(pageable);
        return ResponseEntity.ok(report);
    }
}
