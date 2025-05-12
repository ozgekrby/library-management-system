package com.library.library_management_system.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.library.library_management_system.dto.request.CreateBookRequest;
import com.library.library_management_system.dto.request.UpdateBookRequest;
import com.library.library_management_system.entity.Book;
import com.library.library_management_system.entity.BorrowingRecord;
import com.library.library_management_system.entity.Role;
import com.library.library_management_system.entity.User;
import com.library.library_management_system.repository.BookRepository;
import com.library.library_management_system.repository.BorrowingRecordRepository;
import com.library.library_management_system.repository.UserRepository;
import com.library.library_management_system.security.JwtTokenProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class BookControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private BookRepository bookRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private JwtTokenProvider jwtTokenProvider;
    @Autowired(required = false) private BorrowingRecordRepository borrowingRecordRepository;

    private String librarianToken;
    private String patronToken;
    private User librarian;
    private User patron;
    private Book book1;
    private Book book2;

    @BeforeEach
    void setUp() {

        if (borrowingRecordRepository != null) {
            borrowingRecordRepository.deleteAllInBatch();
        }
        bookRepository.deleteAllInBatch();
        userRepository.deleteAllInBatch();

        librarian = User.builder()
                .username("librarianTest")
                .password(passwordEncoder.encode("password123"))
                .email("librarian.test@example.com")
                .fullName("Lib Rarian")
                .role(Role.LIBRARIAN)
                .build();
        patron = User.builder()
                .username("patronTest")
                .password(passwordEncoder.encode("password123"))
                .email("patron.test@example.com")
                .fullName("Pat Ron")
                .role(Role.PATRON)
                .build();
        userRepository.saveAll(List.of(librarian, patron));

        librarianToken = "Bearer " + jwtTokenProvider.generateTokenForUser(librarian);
        patronToken = "Bearer " + jwtTokenProvider.generateTokenForUser(patron);

        book1 = Book.builder().title("The Lord of the Rings").author("J.R.R. Tolkien").isbn("9783161484100").publicationDate(LocalDate.of(1954, 7, 29)).genre("Fantasy").totalCopies(5).availableCopies(5).build();
        book2 = Book.builder().title("Pride and Prejudice").author("Jane Austen").isbn("978-3-16-148410-0").publicationDate(LocalDate.of(1813, 1, 28)).genre("Romance").totalCopies(3).availableCopies(3).build();
        bookRepository.saveAll(List.of(book1, book2));
    }

    @AfterEach
    void tearDown() {
        if (borrowingRecordRepository != null) {
            borrowingRecordRepository.deleteAllInBatch();
        }
        bookRepository.deleteAllInBatch();
        userRepository.deleteAllInBatch();
    }

    @Test
    void addBook_whenLibrarianAndValidData_shouldReturnCreated() throws Exception {
        CreateBookRequest request = new CreateBookRequest();
        request.setTitle("Test Book");
        request.setAuthor("Test Author");
        request.setIsbn("978-1-56619-909-4");
        request.setPublicationDate(LocalDate.now().minusYears(1));
        request.setGenre("Fiction");
        request.setTotalCopies(5);

        mockMvc.perform(post("/api/books")
                        .header("Authorization", librarianToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title", is("Test Book")))
                .andExpect(jsonPath("$.isbn", is("978-1-56619-909-4")))
                .andExpect(jsonPath("$.id", is(notNullValue())));
    }

    @Test
    void addBook_whenPatron_shouldReturnForbidden() throws Exception {
        CreateBookRequest request = new CreateBookRequest();
        request.setTitle("Patron's Invalid Book Attempt");
        request.setAuthor("Pat Ron");
        request.setIsbn("978-1111111111");
        request.setPublicationDate(LocalDate.now().minusDays(10));
        request.setGenre("Forbidden Genre");
        request.setTotalCopies(1);

        mockMvc.perform(post("/api/books")
                        .header("Authorization", patronToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    void addBook_whenNoAuth_shouldReturnForbidden() throws Exception {
        CreateBookRequest request = new CreateBookRequest();
        request.setTitle("No Auth Book Attempt");
        request.setAuthor("Anonymous");
        request.setIsbn("978-2222222222");
        request.setPublicationDate(LocalDate.now().minusDays(5));
        request.setGenre("Unauthorized Genre");
        request.setTotalCopies(2);

        mockMvc.perform(post("/api/books")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }


    @Test
    void getBookById_shouldReturnBook() throws Exception {
        mockMvc.perform(get("/api/books/" + book1.getId())
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id", is(book1.getId().intValue())))
                .andExpect(jsonPath("$.title", is(book1.getTitle())))
                .andExpect(jsonPath("$.author", is(book1.getAuthor())));
    }

    @Test
    void getBookById_whenBookNotFound_shouldReturnNotFound() throws Exception {
        mockMvc.perform(get("/api/books/999999")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound());
    }

    @Test
    void updateBook_whenLibrarianAndValidData_shouldReturnOk() throws Exception {
        UpdateBookRequest request = new UpdateBookRequest();
        request.setTitle("Updated Title");
        request.setAuthor("Updated Author");
        request.setGenre("Updated Genre");
        request.setTotalCopies(10);

        mockMvc.perform(put("/api/books/" + book1.getId())
                        .header("Authorization", librarianToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(book1.getId().intValue())))
                .andExpect(jsonPath("$.title", is("Updated Title")))
                .andExpect(jsonPath("$.author", is("Updated Author")))
                .andExpect(jsonPath("$.genre", is("Updated Genre")))
                .andExpect(jsonPath("$.totalCopies", is(10)));
    }

    @Test
    void updateBook_whenPatron_shouldReturnForbidden() throws Exception {
        UpdateBookRequest request = new UpdateBookRequest();
        request.setTitle("Forbidden Update");
        request.setTotalCopies(1);

        mockMvc.perform(put("/api/books/" + book1.getId())
                        .header("Authorization", patronToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    void updateBook_whenNoAuth_shouldReturnForbidden() throws Exception {
        UpdateBookRequest request = new UpdateBookRequest();
        request.setTitle("Unauthorized Update");
        request.setTotalCopies(1);

        mockMvc.perform(put("/api/books/" + book1.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    void deleteBook_whenLibrarian_shouldReturnNoContent() throws Exception {
        mockMvc.perform(delete("/api/books/" + book1.getId())
                        .header("Authorization", librarianToken))
                .andExpect(status().isNoContent());

        assertThat(bookRepository.existsById(book1.getId())).isFalse();
    }

    @Test
    void deleteBook_whenPatron_shouldReturnForbidden() throws Exception {
        mockMvc.perform(delete("/api/books/" + book1.getId())
                        .header("Authorization", patronToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void deleteBook_whenNoAuth_shouldReturnForbidden() throws Exception {
        mockMvc.perform(delete("/api/books/" + book1.getId()))
                .andExpect(status().isForbidden());
    }

    @Test
    void addBook_withNullTitle_shouldReturnBadRequest() throws Exception {
        CreateBookRequest request = new CreateBookRequest();
        request.setTitle(null);
        request.setAuthor("Valid Author");
        request.setIsbn("978-1111111111");
        request.setPublicationDate(LocalDate.now());
        request.setGenre("Test");
        request.setTotalCopies(1);

        mockMvc.perform(post("/api/books")
                        .header("Authorization", librarianToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void addBook_withBlankTitle_shouldReturnBadRequest() throws Exception {
        CreateBookRequest request = new CreateBookRequest();
        request.setTitle("   ");
        request.setAuthor("Valid Author");
        request.setIsbn("978-2222222222");
        request.setPublicationDate(LocalDate.now());
        request.setGenre("Test");
        request.setTotalCopies(1);

        mockMvc.perform(post("/api/books")
                        .header("Authorization", librarianToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void addBook_withNullTotalCopies_shouldReturnBadRequest() throws Exception {
        CreateBookRequest request = new CreateBookRequest();
        request.setTitle("Valid Title");
        request.setAuthor("Valid Author");
        request.setIsbn("978-3333333333");
        request.setPublicationDate(LocalDate.now());
        request.setGenre("Test");
        request.setTotalCopies(null);

        mockMvc.perform(post("/api/books")
                        .header("Authorization", librarianToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void addBook_withInvalidIsbnFormat_shouldReturnBadRequest() throws Exception {
        CreateBookRequest request = new CreateBookRequest();
        request.setTitle("Invalid ISBN Book");
        request.setAuthor("Test Author");
        request.setIsbn("INVALID-ISBN");
        request.setPublicationDate(LocalDate.now().minusYears(1));
        request.setGenre("Fiction");
        request.setTotalCopies(5);

        mockMvc.perform(post("/api/books")
                        .header("Authorization", librarianToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.isbn", containsString("Invalid ISBN format")));
    }

    @Test
    void updateBook_withInvalidIsbnFormat_shouldReturnBadRequest() throws Exception {
        UpdateBookRequest request = new UpdateBookRequest();
        request.setTitle("Valid Update Title");
        request.setIsbn("INVALID-ISBN-FOR-UPDATE");

        mockMvc.perform(put("/api/books/" + book1.getId())
                        .header("Authorization", librarianToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.isbn", containsString("Invalid ISBN format")));
    }

    @Test
    void addBook_whenIsbnAlreadyExists_shouldReturnConflict() throws Exception {
        CreateBookRequest request = new CreateBookRequest();
        request.setTitle("Duplicate ISBN Book");
        request.setAuthor("Another Author");
        request.setIsbn(book1.getIsbn());
        request.setPublicationDate(LocalDate.now());
        request.setGenre("Duplicate Test");
        request.setTotalCopies(2);

        mockMvc.perform(post("/api/books")
                        .header("Authorization", librarianToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict());
    }

    @Test
    void updateBook_whenIsbnAlreadyExistsForAnotherBook_shouldReturnConflict() throws Exception {
        UpdateBookRequest request = new UpdateBookRequest();
        request.setTitle("Some Title");
        request.setAuthor("Some Author");
        request.setIsbn(book1.getIsbn());
        request.setTotalCopies(book2.getTotalCopies());

        mockMvc.perform(put("/api/books/" + book2.getId())
                        .header("Authorization", librarianToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict());
    }

    @Test
    void updateBook_whenBookNotFound_shouldReturnNotFound() throws Exception {
        UpdateBookRequest request = new UpdateBookRequest();
        request.setTitle("Update Non Existent");

        mockMvc.perform(put("/api/books/999999")
                        .header("Authorization", librarianToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound());
    }

    @Test
    void deleteBook_whenBookNotFound_shouldReturnNotFound() throws Exception {
        mockMvc.perform(delete("/api/books/999999")
                        .header("Authorization", librarianToken))
                .andExpect(status().isNotFound());
    }


    @Test
    void deleteBook_whenBookIsBorrowed_shouldReturnBadRequest() throws Exception {
        if (borrowingRecordRepository == null) {
            System.out.println("WARN: Skipping deleteBook_whenBookIsBorrowed_shouldReturnConflict due to missing BorrowingRecordRepository.");
            return;
        }

        BorrowingRecord record = BorrowingRecord.builder()
                .book(book1)
                .user(patron)
                .borrowDate(LocalDate.now().minusDays(5))
                .dueDate(LocalDate.now().plusDays(15))
                .returnDate(null)
                .build();
        borrowingRecordRepository.save(record);

        mockMvc.perform(delete("/api/books/" + book1.getId())
                        .header("Authorization", librarianToken))
                .andExpect(status().isBadRequest());

        assertThat(bookRepository.existsById(book1.getId())).isTrue();
    }

    @Test
    void searchBooks_byExactIsbn_shouldReturnOneBook() throws Exception {
        mockMvc.perform(get("/api/books")
                        .param("isbn", book1.getIsbn())
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].isbn", is(book1.getIsbn())));
    }

    @Test
    void searchBooks_byPartialTitle_shouldReturnMatchingBooks() throws Exception {
        mockMvc.perform(get("/api/books")
                        .param("title", "of the")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].title", is(book1.getTitle())));
    }

    @Test
    void searchBooks_byPartialAuthorAndGenre_shouldReturnFilteredBooks() throws Exception {
        Book book3 = Book.builder().title("Sense and Sensibility").author("Jane Austen").isbn("978-0141439662").publicationDate(LocalDate.of(1811, 1, 1)).genre("Romance").totalCopies(2).availableCopies(2).build();
        bookRepository.save(book3);

        mockMvc.perform(get("/api/books")
                        .param("author", "Austen")
                        .param("genre", "Romance")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(2)))
                .andExpect(jsonPath("$.totalElements", is(2)));
    }

    @Test
    void searchBooks_whenNoResultsMatch_shouldReturnEmptyPage() throws Exception {
        mockMvc.perform(get("/api/books")
                        .param("title", "NonExistentXYZ")
                        .param("author", "UnknownAuthor")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(0)))
                .andExpect(jsonPath("$.totalElements", is(0)))
                .andExpect(jsonPath("$.totalPages", is(0)));
    }

    @Test
    void searchBooks_withPagination_shouldReturnCorrectPage() throws Exception {
        Book book3 = Book.builder().title("Book C").author("Author C").isbn("978-1234567890").publicationDate(LocalDate.now()).genre("Test").totalCopies(1).availableCopies(1).build();
        bookRepository.save(book3);

        mockMvc.perform(get("/api/books")
                        .param("page", "1")
                        .param("size", "1")
                        .param("sort", "title,asc")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.totalElements", is(3)))
                .andExpect(jsonPath("$.totalPages", is(3)))
                .andExpect(jsonPath("$.number", is(1)))
                .andExpect(jsonPath("$.content[0].title", is(book2.getTitle())));
    }

    @Test
    void searchBooks_withSortingByAuthorDesc_shouldReturnSortedResults() throws Exception {
        mockMvc.perform(get("/api/books")
                        .param("sort", "author,desc")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(2)))
                .andExpect(jsonPath("$.content[0].author", is("Jane Austen")))
                .andExpect(jsonPath("$.content[1].author", is("J.R.R. Tolkien")));
    }

    @Test
    void addBook_withInvalidTokenFormat_shouldReturnForbidden() throws Exception {
        CreateBookRequest request = new CreateBookRequest();
        request.setTitle("Bad Token Book");
        request.setAuthor("Test");
        request.setIsbn("978-7777777777");
        request.setPublicationDate(LocalDate.now());
        request.setGenre("Test");
        request.setTotalCopies(1);

        mockMvc.perform(post("/api/books")
                        .header("Authorization", "InvalidTokenStructure")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    void updateBook_withExpiredOrInvalidSignatureToken_shouldReturnForbidden() throws Exception {
        UpdateBookRequest request = new UpdateBookRequest();
        request.setTitle("Expired Token Update");
        String invalidJwt = "Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjM0NTY3ODkwIiwibmFtZSI6IkpvaG4gRG9lIiwiaWF0IjoxNTE2MjM5MDIyfQ.SflKxwRJSMeKKF2QT4fwpMeJf36POk6yJV_adQssw5c";

        mockMvc.perform(put("/api/books/" + book1.getId())
                        .header("Authorization", invalidJwt)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    void deleteBook_withInvalidTokenFormat_shouldReturnForbidden() throws Exception {
        mockMvc.perform(delete("/api/books/" + book1.getId())
                        .header("Authorization", "Bearer invalid.token.format"))
                .andExpect(status().isForbidden());
    }
}