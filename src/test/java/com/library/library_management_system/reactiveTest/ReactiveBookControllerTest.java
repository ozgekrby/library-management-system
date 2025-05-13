package com.library.library_management_system.reactiveTest;

import com.library.library_management_system.controller.ReactiveBookController;
import com.library.library_management_system.dto.response.BookResponse;
import com.library.library_management_system.service.BookService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.reactive.ReactiveSecurityAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.LocalDate;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@WebFluxTest(
        controllers = ReactiveBookController.class,
        excludeAutoConfiguration = {
                ReactiveSecurityAutoConfiguration.class,
        }
)
class ReactiveBookControllerTest {

    @Autowired
    private WebTestClient webTestClient;

    @MockBean
    private BookService bookService;

    private BookResponse book1;
    private BookResponse book2;

    @BeforeEach
    void setUp() {
        book1 = BookResponse.builder()
                .id(1L)
                .title("Reactive Programming Book")
                .author("Author One")
                .isbn("111-1111111111")
                .publicationDate(LocalDate.now().minusYears(1))
                .genre("Technology")
                .totalCopies(5)
                .availableCopies(3)
                .build();

        book2 = BookResponse.builder()
                .id(2L)
                .title("Another Reactive Book")
                .author("Author Two")
                .isbn("222-2222222222")
                .publicationDate(LocalDate.now().minusMonths(6))
                .genre("Technology")
                .totalCopies(10)
                .availableCopies(10)
                .build();
    }

    @Test
    @DisplayName("Search Books Reactively - Should return stream of books")
    void searchBooksReactive_shouldReturnFluxOfBooks() {
        String searchTerm = "Reactive";
        Flux<BookResponse> expectedFlux = Flux.just(book1, book2);
        when(bookService.searchBooksReactive(eq(searchTerm), any(), any(), any()))
                .thenReturn(expectedFlux);

        Flux<BookResponse> responseFlux = webTestClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/v1/reactive/books/search")
                        .queryParam("title", searchTerm)
                        .build())
                .accept(MediaType.TEXT_EVENT_STREAM)
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM)
                .returnResult(BookResponse.class)
                .getResponseBody();

        StepVerifier.create(responseFlux)
                .expectNext(book1)
                .expectNext(book2)
                .verifyComplete();
    }

    @Test
    @DisplayName("Search Books Reactively - Should return empty stream when no books found")
    void searchBooksReactive_shouldReturnEmptyFluxWhenNotFound() {
        String searchTerm = "NonExistent";
        when(bookService.searchBooksReactive(eq(searchTerm), any(), any(), any()))
                .thenReturn(Flux.empty());

        Flux<BookResponse> responseFlux = webTestClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/v1/reactive/books/search")
                        .queryParam("title", searchTerm)
                        .build())
                .accept(MediaType.TEXT_EVENT_STREAM)
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM)
                .returnResult(BookResponse.class)
                .getResponseBody();

        StepVerifier.create(responseFlux)
                .expectNextCount(0)
                .verifyComplete();
    }

    @Test
    @DisplayName("Get Book By ID Reactively - Should return book when found")
    void getBookByIdReactive_shouldReturnBookWhenFound() {
        Long bookId = 1L;
        when(bookService.getBookByIdReactive(bookId))
                .thenReturn(Mono.just(book1));

        webTestClient.get()
                .uri("/api/v1/reactive/books/{id}", bookId)
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_JSON)
                .expectBody(BookResponse.class)
                .isEqualTo(book1);
    }

    @Test
    @DisplayName("Get Book By ID Reactively - Should return 404 when not found")
    void getBookByIdReactive_shouldReturnNotFoundWhenBookDoesNotExist() {
        Long nonExistentId = 99L;
        when(bookService.getBookByIdReactive(nonExistentId))
                .thenReturn(Mono.empty());

        webTestClient.get()
                .uri("/api/v1/reactive/books/{id}", nonExistentId)
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus().isNotFound();
    }
}