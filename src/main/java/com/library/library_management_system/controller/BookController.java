package com.library.library_management_system.controller;

import com.library.library_management_system.dto.request.CreateBookRequest;
import com.library.library_management_system.dto.request.UpdateBookRequest;
import com.library.library_management_system.dto.response.BookResponse;
import com.library.library_management_system.service.BookService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/books")
@RequiredArgsConstructor
@Tag(name = "Book Management", description = "APIs for managing books")
public class BookController {

    private final BookService bookService;

    @PostMapping
    @PreAuthorize("hasRole('LIBRARIAN')")
    @Operation(summary = "Add a new book", security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<BookResponse> addBook(@Valid @RequestBody CreateBookRequest request) {
        BookResponse bookResponse = bookService.addBook(request);
        return new ResponseEntity<>(bookResponse, HttpStatus.CREATED);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get book details by ID")
    public ResponseEntity<BookResponse> getBookById(@PathVariable Long id) {
        BookResponse bookResponse = bookService.getBookById(id);
        return ResponseEntity.ok(bookResponse);
    }

    @GetMapping
    @Operation(summary = "Search for books with pagination")
    public ResponseEntity<Page<BookResponse>> searchBooks(
            @Parameter(description = "Search by title (case-insensitive, partial match)") @RequestParam(required = false) String title,
            @Parameter(description = "Search by author (case-insensitive, partial match)") @RequestParam(required = false) String author,
            @Parameter(description = "Search by ISBN (exact match)") @RequestParam(required = false) String isbn,
            @Parameter(description = "Search by genre (case-insensitive, partial match)") @RequestParam(required = false) String genre,
            @PageableDefault(size = 10, sort = "title") Pageable pageable) {
        Page<BookResponse> books = bookService.searchBooks(title, author, isbn, genre, pageable);
        return ResponseEntity.ok(books);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('LIBRARIAN')")
    @Operation(summary = "Update book information", security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<BookResponse> updateBook(@PathVariable Long id, @Valid @RequestBody UpdateBookRequest request) {
        BookResponse bookResponse = bookService.updateBook(id, request);
        return ResponseEntity.ok(bookResponse);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('LIBRARIAN')")
    @Operation(summary = "Delete a book", security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<Void> deleteBook(@PathVariable Long id) {
        bookService.deleteBook(id);
        return ResponseEntity.noContent().build();
    }
}
