package com.library.library_management_system.integration;

import com.library.library_management_system.dto.request.CreateBookRequest;
import com.library.library_management_system.dto.request.UpdateBookRequest;
import com.library.library_management_system.dto.response.BookResponse;
import com.library.library_management_system.entity.Book;
import com.library.library_management_system.entity.BorrowingRecord;
import com.library.library_management_system.entity.Role;
import com.library.library_management_system.entity.User;
import com.library.library_management_system.exception.DuplicateIsbnException;
import com.library.library_management_system.exception.ResourceNotFoundException;
import com.library.library_management_system.repository.BookRepository;
import com.library.library_management_system.repository.BorrowingRecordRepository;
import com.library.library_management_system.repository.UserRepository;
import com.library.library_management_system.service.BookService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
public class BookServiceTest {

    @Autowired
    private BookService bookService;

    @Autowired
    private BookRepository bookRepository;

    @Autowired
    private BorrowingRecordRepository borrowingRecordRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private CreateBookRequest createBookRequest1;
    private CreateBookRequest createBookRequest2;
    private User dummyUser;

    @BeforeEach
    void setUp() {
        borrowingRecordRepository.deleteAll();
        bookRepository.deleteAll();
        userRepository.deleteAll();

        dummyUser = User.builder()
                .username("borrow_user")
                .password(passwordEncoder.encode("password"))
                .email("borrow@example.com")
                .fullName("Borrow User")
                .role(Role.PATRON)
                .build();
        userRepository.save(dummyUser);

        createBookRequest1 = new CreateBookRequest();
        createBookRequest1.setTitle("Effective Java");
        createBookRequest1.setAuthor("Joshua Bloch");
        createBookRequest1.setIsbn("978-0134685991");
        createBookRequest1.setPublicationDate(LocalDate.of(2018, 1, 6));
        createBookRequest1.setGenre("Programming");
        createBookRequest1.setTotalCopies(10);

        createBookRequest2 = new CreateBookRequest();
        createBookRequest2.setTitle("Clean Code");
        createBookRequest2.setAuthor("Robert C. Martin");
        createBookRequest2.setIsbn("978-0132350884");
        createBookRequest2.setPublicationDate(LocalDate.of(2008, 8, 1));
        createBookRequest2.setGenre("Software Engineering");
        createBookRequest2.setTotalCopies(5);
    }

    @AfterEach
    void tearDown() {
    }

    @Test
    void addBook_success() {
        BookResponse response = bookService.addBook(createBookRequest1);

        assertNotNull(response);
        assertNotNull(response.getId());
        assertEquals(createBookRequest1.getTitle(), response.getTitle());
        assertEquals(createBookRequest1.getIsbn(), response.getIsbn());
        assertEquals(createBookRequest1.getTotalCopies(), response.getTotalCopies());
        assertEquals(createBookRequest1.getTotalCopies(), response.getAvailableCopies());

        Optional<Book> savedBookOpt = bookRepository.findById(response.getId());
        assertTrue(savedBookOpt.isPresent());
        Book savedBook = savedBookOpt.get();
        assertEquals(createBookRequest1.getTitle(), savedBook.getTitle());
        assertEquals(createBookRequest1.getTotalCopies(), savedBook.getTotalCopies());
        assertEquals(createBookRequest1.getTotalCopies(), savedBook.getAvailableCopies());
    }

    @Test
    void addBook_throwsDuplicateIsbnException_whenIsbnExists() {
        bookService.addBook(createBookRequest1);

        CreateBookRequest duplicateIsbnRequest = new CreateBookRequest();
        duplicateIsbnRequest.setIsbn(createBookRequest1.getIsbn());
        duplicateIsbnRequest.setTitle("Another Book");
        duplicateIsbnRequest.setAuthor("Another Author");
        duplicateIsbnRequest.setPublicationDate(LocalDate.now());
        duplicateIsbnRequest.setGenre("Test");
        duplicateIsbnRequest.setTotalCopies(1);

        DuplicateIsbnException exception = assertThrows(DuplicateIsbnException.class,
                () -> bookService.addBook(duplicateIsbnRequest));
        assertEquals("Book with ISBN " + createBookRequest1.getIsbn() + " already exists.", exception.getMessage());
        assertEquals(1, bookRepository.count());
    }

    @Test
    void getBookById_success() {
        BookResponse addedBook = bookService.addBook(createBookRequest1);
        BookResponse foundBook = bookService.getBookById(addedBook.getId());

        assertNotNull(foundBook);
        assertEquals(addedBook.getId(), foundBook.getId());
        assertEquals(createBookRequest1.getTitle(), foundBook.getTitle());
    }

    @Test
    void getBookById_throwsResourceNotFoundException_whenBookNotExists() {
        ResourceNotFoundException exception = assertThrows(ResourceNotFoundException.class,
                () -> bookService.getBookById(999L));
        assertEquals("Book not found with id: 999", exception.getMessage());
    }
    @Test
    void addBook_success_withValidDetails() {
        CreateBookRequest createBookRequest = new CreateBookRequest();
        createBookRequest.setTitle("Java Design Patterns");
        createBookRequest.setAuthor("Erich Gamma");
        createBookRequest.setIsbn("978-0201633610");
        createBookRequest.setPublicationDate(LocalDate.of(1994, 1, 1));
        createBookRequest.setGenre("Software Engineering");
        createBookRequest.setTotalCopies(15);

        BookResponse response = bookService.addBook(createBookRequest);

        assertNotNull(response);
        assertNotNull(response.getId());
        assertEquals("Java Design Patterns", response.getTitle());
        assertEquals("978-0201633610", response.getIsbn());
        assertEquals(15, response.getTotalCopies());
    }

    @Test
    void updateBook_shouldNotAllowNegativeTotalCopies() {
        BookResponse addedBook = bookService.addBook(createBookRequest1);
        Long bookId = addedBook.getId();

        UpdateBookRequest updateRequest = new UpdateBookRequest();
        updateRequest.setTotalCopies(-5);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> bookService.updateBook(bookId, updateRequest));

        assertEquals("Total copies cannot be negative.", exception.getMessage());
    }

    @Test
    void deleteBook_shouldThrowException_ifCurrentlyBorrowed() {
        BookResponse addedBookResponse = bookService.addBook(createBookRequest1);
        Book bookEntity = bookRepository.findById(addedBookResponse.getId()).orElseThrow();

        BorrowingRecord borrowingRecord = new BorrowingRecord();
        borrowingRecord.setBook(bookEntity);
        borrowingRecord.setUser(dummyUser);
        borrowingRecord.setBorrowDate(LocalDate.now().minusDays(2));
        borrowingRecord.setDueDate(LocalDate.now().plusDays(5));
        borrowingRecord.setReturnDate(null);
        borrowingRecordRepository.save(borrowingRecord);

        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> bookService.deleteBook(bookEntity.getId()));
        assertEquals("Cannot delete book 'Effective Java'. It is currently borrowed by one or more users.", exception.getMessage());
    }
    @Test
    void updateBook_success_noIsbnChange_fieldsUpdated() {
        BookResponse addedBook = bookService.addBook(createBookRequest1);
        Long bookId = addedBook.getId();

        UpdateBookRequest updateRequest = new UpdateBookRequest();
        updateRequest.setTitle("Effective Java - 3rd Edition");
        updateRequest.setAuthor("J. Bloch");
        updateRequest.setTotalCopies(12);

        BookResponse updatedBookResponse = bookService.updateBook(bookId, updateRequest);

        assertEquals(bookId, updatedBookResponse.getId());
        assertEquals("Effective Java - 3rd Edition", updatedBookResponse.getTitle());
        assertEquals("J. Bloch", updatedBookResponse.getAuthor());
        assertEquals(createBookRequest1.getIsbn(), updatedBookResponse.getIsbn());
        assertEquals(12, updatedBookResponse.getTotalCopies());
        assertEquals(10, updatedBookResponse.getAvailableCopies());
    }

    @Test
    void updateBook_success_isbnChangeToNewUnique() {
        BookResponse addedBook = bookService.addBook(createBookRequest1);
        Long bookId = addedBook.getId();
        String newUniqueIsbn = "000-1234567890";

        UpdateBookRequest updateRequest = new UpdateBookRequest();
        updateRequest.setIsbn(newUniqueIsbn);

        BookResponse updatedBookResponse = bookService.updateBook(bookId, updateRequest);
        assertEquals(newUniqueIsbn, updatedBookResponse.getIsbn());
    }

    @Test
    void updateBook_throwsDuplicateIsbnException_whenUpdatingToExistingIsbnOfAnotherBook() {
        bookService.addBook(createBookRequest1);
        BookResponse book2Response = bookService.addBook(createBookRequest2);
        Long book2Id = book2Response.getId();

        UpdateBookRequest updateRequest = new UpdateBookRequest();
        updateRequest.setIsbn(createBookRequest1.getIsbn());

        DuplicateIsbnException exception = assertThrows(DuplicateIsbnException.class,
                () -> bookService.updateBook(book2Id, updateRequest));
        assertEquals("Another book with ISBN " + createBookRequest1.getIsbn() + " already exists.", exception.getMessage());
    }


    @Test
    void updateBook_increaseTotalCopies_availableCopiesUnchanged() {
        BookResponse addedBook = bookService.addBook(createBookRequest1);
        Long bookId = addedBook.getId();

        UpdateBookRequest updateRequest = new UpdateBookRequest();
        updateRequest.setTotalCopies(12);

        BookResponse updatedBookResponse = bookService.updateBook(bookId, updateRequest);
        assertEquals(12, updatedBookResponse.getTotalCopies());
        assertEquals(10, updatedBookResponse.getAvailableCopies());
    }

    @Test
    void updateBook_decreaseTotalCopies_availableCopiesCapped() {
        BookResponse addedBook = bookService.addBook(createBookRequest1);
        Long bookId = addedBook.getId();

        UpdateBookRequest updateRequest = new UpdateBookRequest();
        updateRequest.setTotalCopies(8);

        BookResponse updatedBookResponse = bookService.updateBook(bookId, updateRequest);
        assertEquals(8, updatedBookResponse.getTotalCopies());
        assertEquals(8, updatedBookResponse.getAvailableCopies());
    }

    @Test
    void updateBook_decreaseTotalCopies_availableCopiesAlreadyLowerThanNewTotal() {
        Book book = Book.builder()
                .title("Test Book")
                .author("Test Author")
                .isbn("unique-isbn-for-test-123")
                .publicationDate(LocalDate.now())
                .genre("Test")
                .totalCopies(10)
                .availableCopies(7)
                .build();
        Book savedBook = bookRepository.save(book);
        Long bookId = savedBook.getId();

        UpdateBookRequest updateRequest = new UpdateBookRequest();
        updateRequest.setTotalCopies(8);

        BookResponse updatedBookResponse = bookService.updateBook(bookId, updateRequest);
        assertEquals(8, updatedBookResponse.getTotalCopies());
        assertEquals(7, updatedBookResponse.getAvailableCopies());
    }

    @Test
    void updateBook_decreaseTotalCopies_belowBorrowed_throwsIllegalStateException() {
        BookResponse addedBookResponse = bookService.addBook(createBookRequest1);
        Book bookEntity = bookRepository.findById(addedBookResponse.getId()).orElseThrow();
        bookEntity.setAvailableCopies(7);
        bookRepository.save(bookEntity);

        UpdateBookRequest updateRequest = new UpdateBookRequest();
        updateRequest.setTotalCopies(2);

        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> bookService.updateBook(bookEntity.getId(), updateRequest));
        assertEquals("Total copies cannot be less than the number of currently borrowed copies (3).", exception.getMessage());
    }


    @Test
    void updateBook_throwsResourceNotFoundException_whenBookNotExists() {
        UpdateBookRequest updateRequest = new UpdateBookRequest();
        ResourceNotFoundException exception = assertThrows(ResourceNotFoundException.class,
                () -> bookService.updateBook(999L, updateRequest));
        assertEquals("Book not found with id: 999", exception.getMessage());
    }

    @Test
    void deleteBook_success_whenNotBorrowed() {
        BookResponse addedBook = bookService.addBook(createBookRequest1);
        Long bookId = addedBook.getId();
        assertDoesNotThrow(() -> bookService.deleteBook(bookId));
        assertFalse(bookRepository.existsById(bookId));
    }

    @Test
    void deleteBook_throwsResourceNotFoundException_whenBookNotExists() {
        ResourceNotFoundException exception = assertThrows(ResourceNotFoundException.class,
                () -> bookService.deleteBook(999L));
        assertEquals("Book not found with id: 999", exception.getMessage());
    }

    @Test
    void deleteBook_throwsIllegalStateException_whenBookIsCurrentlyBorrowed() {
        BookResponse addedBookResponse = bookService.addBook(createBookRequest1);
        Book bookEntity = bookRepository.findById(addedBookResponse.getId()).orElseThrow();

        BorrowingRecord borrowingRecord = new BorrowingRecord();
        borrowingRecord.setBook(bookEntity);
        borrowingRecord.setUser(dummyUser);
        borrowingRecord.setBorrowDate(LocalDate.now().minusDays(5));
        borrowingRecord.setDueDate(LocalDate.now().plusDays(10));
        borrowingRecord.setReturnDate(null);
        borrowingRecordRepository.save(borrowingRecord);

        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> bookService.deleteBook(bookEntity.getId()));
        assertEquals("Cannot delete book '" + bookEntity.getTitle() + "'. It is currently borrowed by one or more users.", exception.getMessage());
        assertTrue(bookRepository.existsById(bookEntity.getId()));
    }

    @Test
    void searchBooks_byTitle() {
        bookService.addBook(createBookRequest1);
        bookService.addBook(createBookRequest2);

        Pageable pageable = PageRequest.of(0, 10);
        Page<BookResponse> results = bookService.searchBooks("Effective", null, null, null, pageable);
        assertEquals(1, results.getTotalElements());
        assertEquals("Effective Java", results.getContent().get(0).getTitle());
    }

    @Test
    void searchBooks_byAuthor() {
        bookService.addBook(createBookRequest1);
        bookService.addBook(createBookRequest2);
        Pageable pageable = PageRequest.of(0, 10);
        Page<BookResponse> results = bookService.searchBooks(null, "Martin", null, null, pageable);
        assertEquals(1, results.getTotalElements());
        assertEquals("Robert C. Martin", results.getContent().get(0).getAuthor());
    }

    @Test
    void searchBooks_byIsbn() {
        bookService.addBook(createBookRequest1);
        bookService.addBook(createBookRequest2);
        Pageable pageable = PageRequest.of(0, 10);
        Page<BookResponse> results = bookService.searchBooks(null, null, "978-0134685991", null, pageable);
        assertEquals(1, results.getTotalElements());
        assertEquals("978-0134685991", results.getContent().get(0).getIsbn());
    }

    @Test
    void searchBooks_byGenre() {
        bookService.addBook(createBookRequest1);
        bookService.addBook(createBookRequest2);
        Pageable pageable = PageRequest.of(0, 10);
        Page<BookResponse> results = bookService.searchBooks(null, null, null, "Programming", pageable);
        assertEquals(1, results.getTotalElements());
        assertEquals("Programming", results.getContent().get(0).getGenre());
    }

    @Test
    void searchBooks_noCriteria_returnsAllBooksPaginated() {
        bookService.addBook(createBookRequest1);
        bookService.addBook(createBookRequest2);

        Pageable pageable = PageRequest.of(0, 1);
        Page<BookResponse> resultsPage1 = bookService.searchBooks(null, null, null, null, pageable);
        assertEquals(2, resultsPage1.getTotalElements());
        assertEquals(1, resultsPage1.getContent().size());

        pageable = PageRequest.of(1, 1);
        Page<BookResponse> resultsPage2 = bookService.searchBooks(null, null, null, null, pageable);
        assertEquals(2, resultsPage2.getTotalElements());
        assertEquals(1, resultsPage2.getContent().size());
        assertNotEquals(resultsPage1.getContent().get(0).getId(), resultsPage2.getContent().get(0).getId());
    }

    @Test
    void searchBooks_combinedCriteria() {
        bookService.addBook(createBookRequest1);
        CreateBookRequest anotherJavaBook = new CreateBookRequest();
        anotherJavaBook.setTitle("Java Concurrency");
        anotherJavaBook.setAuthor("Joshua Bloch");
        anotherJavaBook.setIsbn("123-4567890123");
        anotherJavaBook.setPublicationDate(LocalDate.now());
        anotherJavaBook.setGenre("Programming");
        anotherJavaBook.setTotalCopies(5);
        bookService.addBook(anotherJavaBook);

        bookService.addBook(createBookRequest2);

        Pageable pageable = PageRequest.of(0, 10);
        Page<BookResponse> results = bookService.searchBooks("Java", "Bloch", null, "Programming", pageable);

        assertEquals(2, results.getTotalElements());
        List<BookResponse> content = results.getContent();
        assertTrue(content.stream().anyMatch(b -> b.getTitle().equals("Effective Java")));
        assertTrue(content.stream().anyMatch(b -> b.getTitle().equals("Java Concurrency")));

    }
}