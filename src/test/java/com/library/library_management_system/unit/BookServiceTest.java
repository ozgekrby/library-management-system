package com.library.library_management_system.unit;

import com.library.library_management_system.dto.request.CreateBookRequest;
import com.library.library_management_system.dto.request.UpdateBookRequest;
import com.library.library_management_system.dto.response.BookResponse;
import com.library.library_management_system.entity.Book;
import com.library.library_management_system.exception.DuplicateIsbnException;
import com.library.library_management_system.exception.ResourceNotFoundException;
import com.library.library_management_system.repository.BookRepository;
import com.library.library_management_system.repository.BorrowingRecordRepository;
import com.library.library_management_system.service.BookService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@ActiveProfiles("test")
class BookServiceTest {

    @Mock
    private BookRepository bookRepository;
    @Mock
    private BorrowingRecordRepository borrowingRecordRepository;

    @InjectMocks
    private BookService bookService;

    @Captor
    ArgumentCaptor<Book> bookArgumentCaptor;
    @Captor
    ArgumentCaptor<Specification<Book>> specificationArgumentCaptor;


    private CreateBookRequest createBookRequest;
    private Book book;
    private String existingIsbn = "978-0134685991";
    private String newUniqueIsbn = "978-1234567890";
    private String anotherExistingIsbn = "978-9876543210";


    @BeforeEach
    void setUp() {
        createBookRequest = new CreateBookRequest();
        createBookRequest.setTitle("Effective Java");
        createBookRequest.setAuthor("Joshua Bloch");
        createBookRequest.setIsbn(existingIsbn);
        createBookRequest.setPublicationDate(LocalDate.of(2018, 1, 6));
        createBookRequest.setGenre("Programming");
        createBookRequest.setTotalCopies(10);

        book = Book.builder()
                .id(1L)
                .title(createBookRequest.getTitle())
                .author(createBookRequest.getAuthor())
                .isbn(createBookRequest.getIsbn())
                .publicationDate(createBookRequest.getPublicationDate())
                .genre(createBookRequest.getGenre())
                .totalCopies(createBookRequest.getTotalCopies())
                .availableCopies(createBookRequest.getTotalCopies())
                .build();
    }

    @Test
    void addBook_success() {
        when(bookRepository.existsByIsbn(createBookRequest.getIsbn())).thenReturn(false);
        when(bookRepository.save(any(Book.class))).thenReturn(book);

        BookResponse response = bookService.addBook(createBookRequest);

        assertNotNull(response);
        assertThat(response.getTitle()).isEqualTo(book.getTitle());
        assertThat(response.getIsbn()).isEqualTo(book.getIsbn());
        assertThat(response.getAvailableCopies()).isEqualTo(book.getAvailableCopies());
        assertThat(response.getTotalCopies()).isEqualTo(book.getTotalCopies());

        verify(bookRepository).existsByIsbn(createBookRequest.getIsbn());
        verify(bookRepository).save(bookArgumentCaptor.capture());
        Book savedBook = bookArgumentCaptor.getValue();
        assertThat(savedBook.getTitle()).isEqualTo(createBookRequest.getTitle());
        assertThat(savedBook.getIsbn()).isEqualTo(createBookRequest.getIsbn());
        assertThat(savedBook.getTotalCopies()).isEqualTo(createBookRequest.getTotalCopies());
        assertThat(savedBook.getAvailableCopies()).isEqualTo(createBookRequest.getTotalCopies());
    }

    @Test
    void addBook_throwsDuplicateIsbnException_whenIsbnExists() {
        when(bookRepository.existsByIsbn(createBookRequest.getIsbn())).thenReturn(true);

        DuplicateIsbnException exception = assertThrows(DuplicateIsbnException.class, () -> {
            bookService.addBook(createBookRequest);
        });
        assertThat(exception.getMessage()).contains(createBookRequest.getIsbn());
        verify(bookRepository).existsByIsbn(createBookRequest.getIsbn());
        verify(bookRepository, never()).save(any(Book.class));
    }

    @Test
    void getBookById_success() {
        when(bookRepository.findById(1L)).thenReturn(Optional.of(book));

        BookResponse response = bookService.getBookById(1L);

        assertNotNull(response);
        assertThat(response.getId()).isEqualTo(book.getId());
        assertThat(response.getTitle()).isEqualTo(book.getTitle());
        verify(bookRepository).findById(1L);
    }

    @Test
    void getBookById_throwsResourceNotFoundException_whenBookNotExists() {
        when(bookRepository.findById(99L)).thenReturn(Optional.empty());

        ResourceNotFoundException exception = assertThrows(ResourceNotFoundException.class, () -> {
            bookService.getBookById(99L);
        });
        assertThat(exception.getMessage()).contains("99");
        verify(bookRepository).findById(99L);
    }

    @Test
    void updateBook_success_updateTitleAndCopies() {
        UpdateBookRequest updateRequest = new UpdateBookRequest();
        updateRequest.setTitle("Effective Java 3rd Edition");
        updateRequest.setTotalCopies(15);

        book.setAvailableCopies(8);

        int expectedAvailableCopiesAfterUpdate = book.getAvailableCopies() + (updateRequest.getTotalCopies() - book.getTotalCopies());
        Book updatedBookEntity = Book.builder()
                .id(book.getId())
                .title(updateRequest.getTitle())
                .author(book.getAuthor())
                .isbn(book.getIsbn())
                .publicationDate(book.getPublicationDate())
                .genre(book.getGenre())
                .totalCopies(updateRequest.getTotalCopies())
                .availableCopies(expectedAvailableCopiesAfterUpdate)
                .build();

        when(bookRepository.findById(1L)).thenReturn(Optional.of(book));
        when(bookRepository.save(any(Book.class))).thenReturn(updatedBookEntity);

        BookResponse response = bookService.updateBook(1L, updateRequest);

        assertNotNull(response);
        assertThat(response.getTitle()).isEqualTo(updateRequest.getTitle());
        assertThat(response.getTotalCopies()).isEqualTo(updateRequest.getTotalCopies());
        assertThat(response.getAvailableCopies()).isEqualTo(expectedAvailableCopiesAfterUpdate);

        verify(bookRepository).findById(1L);
        verify(bookRepository).save(bookArgumentCaptor.capture());

        Book bookToSave = bookArgumentCaptor.getValue();
        assertThat(bookToSave.getId()).isEqualTo(book.getId());
        assertThat(bookToSave.getTitle()).isEqualTo(updateRequest.getTitle());
        assertThat(bookToSave.getTotalCopies()).isEqualTo(updateRequest.getTotalCopies());
        assertThat(bookToSave.getAuthor()).isEqualTo(book.getAuthor());
        assertThat(bookToSave.getIsbn()).isEqualTo(book.getIsbn());

    }


    @Test
    void updateBook_throwsResourceNotFoundException_whenBookNotExists() {
        UpdateBookRequest updateRequest = new UpdateBookRequest();
        updateRequest.setTitle("New Title");
        when(bookRepository.findById(99L)).thenReturn(Optional.empty());

        ResourceNotFoundException exception = assertThrows(ResourceNotFoundException.class, () -> {
            bookService.updateBook(99L, updateRequest);
        });
        assertThat(exception.getMessage()).contains("99");
        verify(bookRepository).findById(99L);
        verify(bookRepository, never()).save(any(Book.class));
    }

    @Test
    void deleteBook_success_whenNotBorrowed() {
        when(bookRepository.findById(1L)).thenReturn(Optional.of(book));
        when(borrowingRecordRepository.existsByBookAndReturnDateIsNull(book)).thenReturn(false);
        doNothing().when(bookRepository).delete(book);

        bookService.deleteBook(1L);

        verify(bookRepository).findById(1L);
        verify(borrowingRecordRepository).existsByBookAndReturnDateIsNull(book);
        verify(bookRepository).delete(book);
    }

    @Test
    void searchBooks_returnsPagedBooks_withTitleFilter() {
        Pageable pageable = PageRequest.of(0, 10);
        Page<Book> bookPage = new PageImpl<>(Collections.singletonList(book), pageable, 1);
        when(bookRepository.findAll(any(Specification.class), eq(pageable))).thenReturn(bookPage);

        Page<BookResponse> responsePage = bookService.searchBooks("Effective", null, null, null, pageable);

        assertNotNull(responsePage);
        assertThat(responsePage.getTotalElements()).isEqualTo(1);
        assertThat(responsePage.getContent()).hasSize(1);
        assertThat(responsePage.getContent().get(0).getTitle()).isEqualTo(book.getTitle());
        verify(bookRepository).findAll(specificationArgumentCaptor.capture(), eq(pageable));

    }


    @Test
    void deleteBook_throwsIllegalStateException_whenBookIsCurrentlyBorrowed() {
        when(bookRepository.findById(1L)).thenReturn(Optional.of(book));
        when(borrowingRecordRepository.existsByBookAndReturnDateIsNull(book)).thenReturn(true);

        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> {
            bookService.deleteBook(1L);
        });
        assertThat(exception.getMessage()).contains(book.getTitle());
        assertThat(exception.getMessage()).contains("currently borrowed");

        verify(bookRepository).findById(1L);
        verify(borrowingRecordRepository).existsByBookAndReturnDateIsNull(book);
        verify(bookRepository, never()).delete(any(Book.class));
    }

    @Test
    void updateBook_success_updateIsbnToNewUniqueValue() {
        UpdateBookRequest updateRequest = new UpdateBookRequest();
        updateRequest.setIsbn(newUniqueIsbn);

        Book updatedBookEntity = Book.builder()
                .id(book.getId())
                .title(book.getTitle())
                .author(book.getAuthor())
                .isbn(newUniqueIsbn)
                .publicationDate(book.getPublicationDate())
                .genre(book.getGenre())
                .totalCopies(book.getTotalCopies())
                .availableCopies(book.getAvailableCopies())
                .build();

        when(bookRepository.findById(1L)).thenReturn(Optional.of(book));
        when(bookRepository.existsByIsbn(newUniqueIsbn)).thenReturn(false);
        when(bookRepository.save(any(Book.class))).thenReturn(updatedBookEntity);

        BookResponse response = bookService.updateBook(1L, updateRequest);

        assertNotNull(response);
        assertThat(response.getIsbn()).isEqualTo(newUniqueIsbn);
        assertThat(response.getId()).isEqualTo(book.getId());

        verify(bookRepository).findById(1L);
        verify(bookRepository).existsByIsbn(newUniqueIsbn);
        verify(bookRepository).save(bookArgumentCaptor.capture());

        Book bookToSave = bookArgumentCaptor.getValue();
        assertThat(bookToSave.getIsbn()).isEqualTo(newUniqueIsbn);
    }


    @Test
    void updateBook_throwsDuplicateIsbnException_whenUpdatingToExistingIsbn() {
        UpdateBookRequest updateRequest = new UpdateBookRequest();
        updateRequest.setIsbn(anotherExistingIsbn);

        when(bookRepository.findById(1L)).thenReturn(Optional.of(book));
        when(bookRepository.existsByIsbn(anotherExistingIsbn)).thenReturn(true);

        DuplicateIsbnException exception = assertThrows(DuplicateIsbnException.class, () -> {
            bookService.updateBook(1L, updateRequest);
        });

        assertThat(exception.getMessage()).contains(anotherExistingIsbn);
        assertThat(exception.getMessage()).contains("Another book with ISBN");

        verify(bookRepository).findById(1L);
        verify(bookRepository).existsByIsbn(anotherExistingIsbn);
        verify(bookRepository, never()).save(any(Book.class));
    }

    @Test
    void updateBook_throwsIllegalArgumentException_whenTotalCopiesIsNegative() {
        UpdateBookRequest updateRequest = new UpdateBookRequest();
        updateRequest.setTotalCopies(-5);

        when(bookRepository.findById(1L)).thenReturn(Optional.of(book));

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            bookService.updateBook(1L, updateRequest);
        });

        assertThat(exception.getMessage()).contains("Total copies cannot be negative");

        verify(bookRepository).findById(1L);
        verify(bookRepository, never()).existsByIsbn(anyString());
        verify(bookRepository, never()).save(any(Book.class));
    }


    @Test
    void updateBook_throwsIllegalStateException_whenTotalCopiesLessThanCurrentlyBorrowed() {
        book.setAvailableCopies(5);
        int borrowedCopies = book.getTotalCopies() - book.getAvailableCopies();

        UpdateBookRequest updateRequest = new UpdateBookRequest();
        updateRequest.setTotalCopies(4);

        when(bookRepository.findById(1L)).thenReturn(Optional.of(book));

        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> {
            bookService.updateBook(1L, updateRequest);
        });

        assertThat(exception.getMessage()).contains("Total copies cannot be less than the number of currently borrowed copies");
        assertThat(exception.getMessage()).contains("(" + borrowedCopies + ")");

        verify(bookRepository).findById(1L);
        verify(bookRepository, never()).existsByIsbn(anyString());
        verify(bookRepository, never()).save(any(Book.class));
    }

    @Test
    void updateBook_success_decreasingTotalCopiesButMoreThanBorrowed() {
        book.setAvailableCopies(8);
        int borrowedCopies = book.getTotalCopies() - book.getAvailableCopies();

        UpdateBookRequest updateRequest = new UpdateBookRequest();
        updateRequest.setTotalCopies(5);

        int expectedAvailableAfterUpdate = 5;

        Book updatedBookEntity = Book.builder()
                .id(book.getId())
                .title(book.getTitle())
                .author(book.getAuthor())
                .isbn(book.getIsbn())
                .publicationDate(book.getPublicationDate())
                .genre(book.getGenre())
                .totalCopies(updateRequest.getTotalCopies())
                .availableCopies(expectedAvailableAfterUpdate)
                .build();

        when(bookRepository.findById(1L)).thenReturn(Optional.of(book));
        when(bookRepository.save(any(Book.class))).thenReturn(updatedBookEntity);

        BookResponse response = bookService.updateBook(1L, updateRequest);

        assertNotNull(response);
        assertThat(response.getTotalCopies()).isEqualTo(updateRequest.getTotalCopies());
        assertThat(response.getAvailableCopies()).isEqualTo(expectedAvailableAfterUpdate);

        verify(bookRepository).findById(1L);
        verify(bookRepository).save(bookArgumentCaptor.capture());

        Book bookToSave = bookArgumentCaptor.getValue();
        assertThat(bookToSave.getTotalCopies()).isEqualTo(updateRequest.getTotalCopies());
        assertThat(bookToSave.getAvailableCopies()).isEqualTo(expectedAvailableAfterUpdate);
    }


    @Test
    void searchBooks_withMultipleParameters_returnsMatchingBooks() {
        Pageable pageable = PageRequest.of(0, 5);
        Book anotherBook = Book.builder().id(2L).title("Effective Java").author("Someone Else").isbn("111").totalCopies(1).availableCopies(1).build();
        Page<Book> bookPage = new PageImpl<>(Collections.singletonList(book), pageable, 1);

        when(bookRepository.findAll(any(Specification.class), eq(pageable))).thenReturn(bookPage);

        Page<BookResponse> responsePage = bookService.searchBooks("Effective Java", "Joshua Bloch", null, null, pageable);

        assertNotNull(responsePage);
        assertThat(responsePage.getTotalElements()).isEqualTo(1);
        assertThat(responsePage.getContent()).hasSize(1);
        assertThat(responsePage.getContent().get(0).getTitle()).isEqualTo(book.getTitle());
        assertThat(responsePage.getContent().get(0).getAuthor()).isEqualTo(book.getAuthor());

        verify(bookRepository).findAll(specificationArgumentCaptor.capture(), eq(pageable));
    }

    @Test
    void searchBooks_returnsEmptyPage_whenNoBooksMatchCriteria() {
        Pageable pageable = PageRequest.of(0, 10);
        Page<Book> emptyPage = Page.empty(pageable);

        when(bookRepository.findAll(any(Specification.class), eq(pageable))).thenReturn(emptyPage);

        Page<BookResponse> responsePage = bookService.searchBooks("NonExistentTitle", "NonExistentAuthor", null, null, pageable);

        assertNotNull(responsePage);
        assertThat(responsePage.getTotalElements()).isEqualTo(0);
        assertThat(responsePage.getContent()).isEmpty();
        verify(bookRepository).findAll(any(Specification.class), eq(pageable));
    }

    @Test
    void searchBooks_withNoFilters_returnsAllBooksPaged() {
        Pageable pageable = PageRequest.of(0, 10);
        Book book2 = Book.builder().id(2L).title("Clean Code").author("Robert C. Martin").isbn("123").totalCopies(5).availableCopies(5).build();
        List<Book> allBooks = List.of(book, book2);
        Page<Book> bookPage = new PageImpl<>(allBooks, pageable, allBooks.size());

        when(bookRepository.findAll(any(Specification.class), eq(pageable))).thenReturn(bookPage);

        Page<BookResponse> responsePage = bookService.searchBooks(null, null, null, null, pageable);

        assertNotNull(responsePage);
        assertThat(responsePage.getTotalElements()).isEqualTo(2);
        assertThat(responsePage.getContent()).hasSize(2);
        assertThat(responsePage.getContent()).extracting(BookResponse::getId).containsExactlyInAnyOrder(book.getId(), book2.getId());

        verify(bookRepository).findAll(specificationArgumentCaptor.capture(), eq(pageable));
    }

    @Test
    void deleteBook_throwsResourceNotFoundException_whenBookToDeleteNotExists() {
        long nonExistentId = 99L;
        when(bookRepository.findById(nonExistentId)).thenReturn(Optional.empty());

        ResourceNotFoundException exception = assertThrows(ResourceNotFoundException.class, () -> {
            bookService.deleteBook(nonExistentId);
        });

        assertThat(exception.getMessage()).contains("Book not found with id: " + nonExistentId);

        verify(bookRepository).findById(nonExistentId);
        verify(borrowingRecordRepository, never()).existsByBookAndReturnDateIsNull(any(Book.class));
        verify(bookRepository, never()).delete(any(Book.class));
    }
}