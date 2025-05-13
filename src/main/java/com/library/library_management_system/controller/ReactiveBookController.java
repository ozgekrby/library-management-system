package com.library.library_management_system.controller;

import com.library.library_management_system.dto.response.BookResponse;
import com.library.library_management_system.service.BookService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/v1/reactive/books")
@RequiredArgsConstructor
@Tag(name = "Reactive Book Search", description = "APIs for reactively searching books")
public class ReactiveBookController {

    private final BookService bookService;

    @GetMapping(value = "/search", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "Reactively search for books (stream results)")
    public Flux<BookResponse> searchBooksReactive(
            @Parameter(description = "Search by title (case-insensitive, partial match)") @RequestParam(required = false) String title,
            @Parameter(description = "Search by author (case-insensitive, partial match)") @RequestParam(required = false) String author,
            @Parameter(description = "Search by ISBN (exact match)") @RequestParam(required = false) String isbn,
            @Parameter(description = "Search by genre (case-insensitive, partial match)") @RequestParam(required = false) String genre) {

        return bookService.searchBooksReactive(title, author, isbn, genre);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get book details by ID reactively")
    public Mono<ResponseEntity<BookResponse>> getBookByIdReactive(@PathVariable Long id) {
        return bookService.getBookByIdReactive(id)
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }
}
