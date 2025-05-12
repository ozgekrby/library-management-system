package com.library.library_management_system.unit;

import com.library.library_management_system.controller.BookController;
import com.library.library_management_system.dto.request.CreateBookRequest;
import com.library.library_management_system.dto.request.UpdateBookRequest;
import com.library.library_management_system.dto.response.BookResponse;
import com.library.library_management_system.exception.DuplicateIsbnException;
import com.library.library_management_system.exception.ResourceNotFoundException;
import com.library.library_management_system.service.BookService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.LocalDate;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BookControllerTest {

    @Mock
    private BookService bookService;

    @InjectMocks
    private BookController bookController;

    private CreateBookRequest createBookRequest;
    private UpdateBookRequest updateBookRequest;
    private BookResponse bookResponse;
    private Pageable pageable;

    @BeforeEach
    void setUp() {
        createBookRequest = new CreateBookRequest();
        createBookRequest.setTitle("Effective Java");
        createBookRequest.setAuthor("Joshua Bloch");
        createBookRequest.setIsbn("978-0134685991");
        createBookRequest.setPublicationDate(LocalDate.of(2018, 1, 6));
        createBookRequest.setGenre("Programming");
        createBookRequest.setTotalCopies(10);

        updateBookRequest = new UpdateBookRequest();
        updateBookRequest.setTitle("Effective Java 3rd Edition");
        updateBookRequest.setTotalCopies(15);

        bookResponse = BookResponse.builder()
                .id(1L)
                .title("Effective Java")
                .author("Joshua Bloch")
                .isbn("978-0134685991")
                .publicationDate(LocalDate.of(2018, 1, 6))
                .genre("Programming")
                .totalCopies(10)
                .availableCopies(10)
                .build();

        pageable = PageRequest.of(0, 10);
    }

    @Test
    void addBook_withValidRequest_shouldReturnCreatedAndBookResponse() {

        when(bookService.addBook(any(CreateBookRequest.class))).thenReturn(bookResponse);

        ResponseEntity<BookResponse> responseEntity = bookController.addBook(createBookRequest);

        assertNotNull(responseEntity);
        assertEquals(HttpStatus.CREATED, responseEntity.getStatusCode());
        assertNotNull(responseEntity.getBody());
        assertEquals(bookResponse.getTitle(), responseEntity.getBody().getTitle());
        verify(bookService, times(1)).addBook(createBookRequest);
    }

    @Test
    void addBook_whenServiceThrowsDuplicateIsbnException_shouldPropagateException() {

        when(bookService.addBook(any(CreateBookRequest.class)))
                .thenThrow(new DuplicateIsbnException("ISBN already exists"));

        DuplicateIsbnException exception = assertThrows(DuplicateIsbnException.class, () -> {
            bookController.addBook(createBookRequest);
        });
        assertEquals("ISBN already exists", exception.getMessage());
        verify(bookService, times(1)).addBook(createBookRequest);
    }

    @Test
    void getBookById_whenBookExists_shouldReturnOkAndBookResponse() {

        Long bookId = 1L;
        when(bookService.getBookById(bookId)).thenReturn(bookResponse);

        ResponseEntity<BookResponse> responseEntity = bookController.getBookById(bookId);

        assertNotNull(responseEntity);
        assertEquals(HttpStatus.OK, responseEntity.getStatusCode());
        assertNotNull(responseEntity.getBody());
        assertEquals(bookResponse.getId(), responseEntity.getBody().getId());
        verify(bookService, times(1)).getBookById(bookId);
    }

    @Test
    void getBookById_whenBookNotFound_shouldPropagateResourceNotFoundException() {

        Long bookId = 99L;
        when(bookService.getBookById(bookId)).thenThrow(new ResourceNotFoundException("Book not found"));

        ResourceNotFoundException exception = assertThrows(ResourceNotFoundException.class, () -> {
            bookController.getBookById(bookId);
        });
        assertEquals("Book not found", exception.getMessage());
        verify(bookService, times(1)).getBookById(bookId);
    }

    @Test
    void searchBooks_withParameters_shouldReturnOkAndPageOfBookResponses() {

        Page<BookResponse> bookPage = new PageImpl<>(Collections.singletonList(bookResponse), pageable, 1);

        when(bookService.searchBooks(
                eq("Java"),
                eq("Bloch"),
                isNull(),
                eq("Programming"),
                eq(pageable)
        )).thenReturn(bookPage);

        ResponseEntity<Page<BookResponse>> responseEntity = bookController.searchBooks(
                "Java", "Bloch", null, "Programming", pageable
        );

        assertNotNull(responseEntity);
        assertEquals(HttpStatus.OK, responseEntity.getStatusCode());
        assertNotNull(responseEntity.getBody());
        assertEquals(1, responseEntity.getBody().getTotalElements());
        assertEquals(bookResponse.getTitle(), responseEntity.getBody().getContent().get(0).getTitle());

        verify(bookService, times(1)).searchBooks(
                eq("Java"),
                eq("Bloch"),
                isNull(),
                eq("Programming"),
                eq(pageable)
        );
    }

    @Test
    void updateBook_whenBookExistsAndRequestValid_shouldReturnOkAndUpdatedBookResponse() {

        Long bookId = 1L;
        BookResponse updatedBookResponse = BookResponse.builder()
                .id(bookId)
                .title(updateBookRequest.getTitle())
                .author(bookResponse.getAuthor())
                .isbn(bookResponse.getIsbn())
                .publicationDate(bookResponse.getPublicationDate())
                .genre(bookResponse.getGenre())
                .totalCopies(updateBookRequest.getTotalCopies())
                .availableCopies(bookResponse.getAvailableCopies() + (updateBookRequest.getTotalCopies() - bookResponse.getTotalCopies()))
                .build();
        when(bookService.updateBook(eq(bookId), any(UpdateBookRequest.class))).thenReturn(updatedBookResponse);

        ResponseEntity<BookResponse> responseEntity = bookController.updateBook(bookId, updateBookRequest);

        assertNotNull(responseEntity);
        assertEquals(HttpStatus.OK, responseEntity.getStatusCode());
        assertNotNull(responseEntity.getBody());
        assertEquals(updatedBookResponse.getTitle(), responseEntity.getBody().getTitle());
        assertEquals(updatedBookResponse.getTotalCopies(), responseEntity.getBody().getTotalCopies());
        verify(bookService, times(1)).updateBook(bookId, updateBookRequest);
    }

    @Test
    void updateBook_whenBookNotFound_shouldPropagateResourceNotFoundException() {

        Long bookId = 99L;
        when(bookService.updateBook(eq(bookId), any(UpdateBookRequest.class)))
                .thenThrow(new ResourceNotFoundException("Book to update not found"));

        ResourceNotFoundException exception = assertThrows(ResourceNotFoundException.class, () -> {
            bookController.updateBook(bookId, updateBookRequest);
        });
        assertEquals("Book to update not found", exception.getMessage());
        verify(bookService, times(1)).updateBook(bookId, updateBookRequest);
    }

    @Test
    void deleteBook_whenBookExists_shouldReturnNoContent() {

        Long bookId = 1L;
        doNothing().when(bookService).deleteBook(bookId);
        ResponseEntity<Void> responseEntity = bookController.deleteBook(bookId);

        assertNotNull(responseEntity);
        assertEquals(HttpStatus.NO_CONTENT, responseEntity.getStatusCode());
        verify(bookService, times(1)).deleteBook(bookId);
    }

    @Test
    void deleteBook_whenBookNotFound_shouldPropagateResourceNotFoundException() {

        Long bookId = 99L;
        doThrow(new ResourceNotFoundException("Book to delete not found")).when(bookService).deleteBook(bookId);

        ResourceNotFoundException exception = assertThrows(ResourceNotFoundException.class, () -> {
            bookController.deleteBook(bookId);
        });
        assertEquals("Book to delete not found", exception.getMessage());
        verify(bookService, times(1)).deleteBook(bookId);
    }

    @Test
    void deleteBook_whenServiceThrowsIllegalStateException_shouldPropagateException() {

        Long bookId = 1L;
        doThrow(new IllegalStateException("Book is currently borrowed")).when(bookService).deleteBook(bookId);

        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> {
            bookController.deleteBook(bookId);
        });
        assertEquals("Book is currently borrowed", exception.getMessage());
        verify(bookService, times(1)).deleteBook(bookId);
    }

    @Test
    void searchBooks_withNoOptionalParameters_shouldReturnOkAndPageOfBookResponses() {

        Page<BookResponse> bookPage = new PageImpl<>(Collections.singletonList(bookResponse), pageable, 1);
        when(bookService.searchBooks(
                isNull(),
                isNull(),
                isNull(),
                isNull(),
                eq(pageable)
        )).thenReturn(bookPage);

        ResponseEntity<Page<BookResponse>> responseEntity = bookController.searchBooks(
                null, null, null, null, pageable
        );

        assertNotNull(responseEntity);
        assertEquals(HttpStatus.OK, responseEntity.getStatusCode());
        assertNotNull(responseEntity.getBody());
        assertEquals(1, responseEntity.getBody().getTotalElements());
        assertEquals(bookResponse.getTitle(), responseEntity.getBody().getContent().get(0).getTitle());

        verify(bookService, times(1)).searchBooks(
                isNull(), isNull(), isNull(), isNull(), eq(pageable)
        );
    }

    @Test
    void updateBook_whenServiceThrowsDuplicateIsbnException_shouldPropagateException() {

        Long bookId = 1L;
        when(bookService.updateBook(eq(bookId), any(UpdateBookRequest.class)))
                .thenThrow(new DuplicateIsbnException("Updated ISBN already exists"));

        DuplicateIsbnException exception = assertThrows(DuplicateIsbnException.class, () -> {
            bookController.updateBook(bookId, updateBookRequest);
        });
        assertEquals("Updated ISBN already exists", exception.getMessage());
        verify(bookService, times(1)).updateBook(bookId, updateBookRequest);
    }

    @Test
    void updateBook_whenServiceThrowsIllegalArgumentException_shouldPropagateException() {

        Long bookId = 1L;
        when(bookService.updateBook(eq(bookId), any(UpdateBookRequest.class)))
                .thenThrow(new IllegalArgumentException("Invalid argument for update"));

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            bookController.updateBook(bookId, updateBookRequest);
        });
        assertEquals("Invalid argument for update", exception.getMessage());
        verify(bookService, times(1)).updateBook(bookId, updateBookRequest);
    }

    @Test
    void updateBook_whenServiceThrowsIllegalStateException_shouldPropagateException() {

        Long bookId = 1L;
        when(bookService.updateBook(eq(bookId), any(UpdateBookRequest.class)))
                .thenThrow(new IllegalStateException("Invalid state for update"));

        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> {
            bookController.updateBook(bookId, updateBookRequest);
        });
        assertEquals("Invalid state for update", exception.getMessage());
        verify(bookService, times(1)).updateBook(bookId, updateBookRequest);
    }
}
