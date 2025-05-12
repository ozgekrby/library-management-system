package com.library.library_management_system.integration;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.library.library_management_system.dto.request.BorrowBookRequest;
import com.library.library_management_system.entity.Book;
import com.library.library_management_system.entity.BorrowingRecord;
import com.library.library_management_system.entity.Role;
import com.library.library_management_system.entity.User;
import com.library.library_management_system.repository.BookRepository;
import com.library.library_management_system.repository.BorrowingRecordRepository;
import com.library.library_management_system.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

import static org.hamcrest.Matchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;


@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class BorrowingControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private BookRepository bookRepository;
    @Autowired private BorrowingRecordRepository borrowingRecordRepository;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private PasswordEncoder passwordEncoder;

    private User patronUser;
    private User librarianUser;
    private Book availableBook1;
    private Book unavailableBook;
    private Book availableBook2;


    @BeforeEach
    void setUp() {
        borrowingRecordRepository.deleteAllInBatch();
        bookRepository.deleteAllInBatch();
        userRepository.deleteAllInBatch();

        patronUser = User.builder()
                .username("patronTestUser")
                .password(passwordEncoder.encode("password123"))
                .role(Role.PATRON)
                .email("patron.test@example.com")
                .fullName("Patron Test User")
                .build();
        patronUser = userRepository.save(patronUser);

        librarianUser = User.builder()
                .username("librarianTestUser")
                .password(passwordEncoder.encode("password123"))
                .role(Role.LIBRARIAN)
                .email("librarian.test@example.com")
                .fullName("Librarian Test User")
                .build();
        librarianUser = userRepository.save(librarianUser);

        availableBook1 = Book.builder()
                .title("Available Book 1")
                .author("Author A")
                .isbn("978-1111111111")
                .totalCopies(5)
                .availableCopies(5)
                .genre("Fiction")
                .publicationDate(LocalDate.of(2020, 1, 1))
                .build();
        availableBook1 = bookRepository.save(availableBook1);

        availableBook2 = Book.builder()
                .title("Available Book 2")
                .author("Author B")
                .isbn("978-2222222222")
                .totalCopies(3)
                .availableCopies(3)
                .genre("Science")
                .publicationDate(LocalDate.of(2021, 1, 1))
                .build();
        availableBook2 = bookRepository.save(availableBook2);

        unavailableBook = Book.builder()
                .title("Unavailable Book")
                .author("Author C")
                .isbn("978-3333333333")
                .totalCopies(1)
                .availableCopies(0)
                .genre("Mystery")
                .publicationDate(LocalDate.of(2019, 1, 1))
                .build();
        unavailableBook = bookRepository.save(unavailableBook);
    }

    @AfterEach
    void tearDown() {
    }
    @Test
    void borrowBook_asPatron_withAvailableBook_shouldSucceed() throws Exception {
        BorrowBookRequest request = new BorrowBookRequest();
        request.setBookId(availableBook1.getId());
        request.setDueDate(LocalDate.now().plusDays(14));

        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                patronUser,
                patronUser.getPassword(),
                List.of(new SimpleGrantedAuthority("ROLE_PATRON"))
        );
        SecurityContextHolder.getContext().setAuthentication(auth);

        mockMvc.perform(post("/api/borrow")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.bookId", is(availableBook1.getId().intValue())))
                .andExpect(jsonPath("$.userId", is(patronUser.getId().intValue())))
                .andExpect(jsonPath("$.returnDate", is(nullValue())));
    }
    @Test
    void borrowBook_asLibrarian_shouldSucceed() throws Exception {
        BorrowBookRequest request = new BorrowBookRequest();
        request.setBookId(availableBook1.getId());
        request.setDueDate(LocalDate.now().plusDays(14));

        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                librarianUser,
                librarianUser.getPassword(),
                List.of(new SimpleGrantedAuthority("ROLE_LIBRARIAN"))
        );
        SecurityContextHolder.getContext().setAuthentication(auth);

        mockMvc.perform(post("/api/borrow")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.userId", is(librarianUser.getId().intValue())));
    }

    @Test
    @WithMockUser(username = "patronTestUser", roles = {"PATRON"})
    void borrowBook_whenBookNotAvailable_shouldReturnBadRequest() throws Exception {
        BorrowBookRequest request = new BorrowBookRequest();
        request.setBookId(unavailableBook.getId());
        request.setDueDate(LocalDate.now().plusDays(14));

        mockMvc.perform(post("/api/borrow")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(username = "patronTestUser", roles = {"PATRON"})
    void borrowBook_whenBookNotFound_shouldReturnNotFound() throws Exception {
        BorrowBookRequest request = new BorrowBookRequest();
        request.setBookId(9999L);
        request.setDueDate(LocalDate.now().plusDays(14));

        mockMvc.perform(post("/api/borrow")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(username = "patronTestUser", roles = {"PATRON"})
    void borrowBook_withInvalidDueDate_shouldReturnBadRequest() throws Exception {
        BorrowBookRequest request = new BorrowBookRequest();
        request.setBookId(availableBook1.getId());
        request.setDueDate(LocalDate.now().minusDays(1));

        mockMvc.perform(post("/api/borrow")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void borrowBook_asUnauthenticatedUser_shouldReturnForbiddenOrUnauthorized() throws Exception {
        BorrowBookRequest request = new BorrowBookRequest();
        request.setBookId(availableBook1.getId());
        request.setDueDate(LocalDate.now().plusDays(14));

        mockMvc.perform(post("/api/borrow")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    private BorrowingRecord createBorrowingRecord(Book book, User user, LocalDate borrowDate, LocalDate dueDate, LocalDate returnDate) {
        BorrowingRecord record = BorrowingRecord.builder()
                .book(book)
                .user(user)
                .borrowDate(borrowDate)
                .dueDate(dueDate)
                .returnDate(returnDate)
                .build();
        return borrowingRecordRepository.save(record);
    }

    @Test
    void returnBook_asPatron_forOwnBorrowing_shouldSucceed() throws Exception {
        BorrowingRecord record = createBorrowingRecord(availableBook1, patronUser, LocalDate.now().minusDays(5), LocalDate.now().plusDays(10), null);

        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                patronUser,
                patronUser.getPassword(),
                List.of(new SimpleGrantedAuthority("ROLE_PATRON"))
        );
        SecurityContextHolder.getContext().setAuthentication(auth);

        mockMvc.perform(put("/api/borrow/return/" + record.getId())
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(record.getId().intValue())))
                .andExpect(jsonPath("$.returnDate", is(notNullValue())));
    }

    @Test
    void returnBook_asLibrarian_forAnyBorrowing_shouldSucceed() throws Exception {
        BorrowingRecord record = createBorrowingRecord(availableBook1, patronUser, LocalDate.now().minusDays(5), LocalDate.now().plusDays(10), null);

        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                librarianUser,
                librarianUser.getPassword(),
                List.of(new SimpleGrantedAuthority("ROLE_LIBRARIAN"))
        );
        SecurityContextHolder.getContext().setAuthentication(auth);

        mockMvc.perform(put("/api/borrow/return/" + record.getId())
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(record.getId().intValue())))
                .andExpect(jsonPath("$.returnDate", is(notNullValue())));
    }

    @Test
    void returnBook_asPatron_forAnotherUsersBorrowing_shouldReturnForbiddenOrNotFound() throws Exception {
        BorrowingRecord librariansRecord = createBorrowingRecord(availableBook2, librarianUser, LocalDate.now().minusDays(3), LocalDate.now().plusDays(10), null);

        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                patronUser,
                patronUser.getPassword(),
                List.of(new SimpleGrantedAuthority("ROLE_PATRON"))
        );
        SecurityContextHolder.getContext().setAuthentication(auth);

        mockMvc.perform(put("/api/borrow/return/" + librariansRecord.getId())
                        .with(csrf()))
                .andExpect(status().isNotFound());
    }

    @Test
    void returnBook_whenRecordNotFound_shouldReturnNotFound() throws Exception {
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                patronUser,
                patronUser.getPassword(),
                List.of(new SimpleGrantedAuthority("ROLE_PATRON"))
        );
        SecurityContextHolder.getContext().setAuthentication(auth);

        mockMvc.perform(put("/api/borrow/return/99999")
                        .with(csrf()))
                .andExpect(status().isNotFound());
    }

    @Test
    void returnBook_whenBookAlreadyReturned_shouldReturnNotFound() throws Exception {
        BorrowingRecord record = createBorrowingRecord(availableBook1, patronUser, LocalDate.now().minusDays(5), LocalDate.now().plusDays(10), LocalDate.now().minusDays(1));

        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                patronUser,
                patronUser.getPassword(),
                List.of(new SimpleGrantedAuthority("ROLE_PATRON"))
        );
        SecurityContextHolder.getContext().setAuthentication(auth);

        mockMvc.perform(put("/api/borrow/return/" + record.getId())
                        .with(csrf()))
                .andExpect(status().isNotFound());
    }

    @Test
    void returnBook_asUnauthenticatedUser_shouldReturnForbiddenOrUnauthorized() throws Exception {
        BorrowingRecord record = createBorrowingRecord(availableBook1, patronUser, LocalDate.now().minusDays(5), LocalDate.now().plusDays(10), null);
        mockMvc.perform(put("/api/borrow/return/" + record.getId())
                        .with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    void getMyBorrowingHistory_asPatron_shouldReturnOwnHistory() throws Exception {
        createBorrowingRecord(availableBook1, patronUser, LocalDate.now().minusDays(10), LocalDate.now().minusDays(3), LocalDate.now().minusDays(1));
        createBorrowingRecord(availableBook2, patronUser, LocalDate.now().minusDays(5), LocalDate.now().plusDays(10), null);

        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                patronUser,
                patronUser.getPassword(),
                List.of(new SimpleGrantedAuthority("ROLE_PATRON"))
        );
        SecurityContextHolder.getContext().setAuthentication(auth);

        mockMvc.perform(get("/api/borrow/history/me")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[?(@.bookTitle == 'Available Book 1')]").exists())
                .andExpect(jsonPath("$[?(@.bookTitle == 'Available Book 2')]").exists());
    }

    @Test
    @WithMockUser(username = "patronTestUser", roles = {"PATRON"})
    void getMyBorrowingHistory_whenNoHistory_shouldReturnEmptyList() throws Exception {
        mockMvc.perform(get("/api/borrow/history/me")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }


    @Test
    @WithMockUser(username = "librarianTestUser", roles = {"LIBRARIAN"})
    void getUserBorrowingHistoryByLibrarian_shouldSucceed() throws Exception {
        createBorrowingRecord(availableBook1, patronUser, LocalDate.now().minusDays(5), LocalDate.now().plusDays(10), null);

        mockMvc.perform(get("/api/borrow/history/user/" + patronUser.getId())
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].userId", is(patronUser.getId().intValue())));
    }

    @Test
    @WithMockUser(username = "librarianTestUser", roles = {"LIBRARIAN"})
    void getUserBorrowingHistoryByLibrarian_whenUserNotFound_shouldReturnNotFound() throws Exception {
        mockMvc.perform(get("/api/borrow/history/user/99999")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound());
    }


    @Test
    @WithMockUser(username = "patronTestUser", roles = {"PATRON"})
    void getUserBorrowingHistoryByLibrarian_asPatron_shouldReturnForbidden() throws Exception {
        mockMvc.perform(get("/api/borrow/history/user/" + librarianUser.getId()))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "librarianTestUser", roles = {"LIBRARIAN"})
    void getAllBorrowingHistory_asLibrarian_shouldSucceed() throws Exception {
        createBorrowingRecord(availableBook1, patronUser, LocalDate.now().minusDays(10), LocalDate.now().plusDays(5), null);
        createBorrowingRecord(availableBook2, librarianUser, LocalDate.now().minusDays(2), LocalDate.now().plusDays(12), null);

        mockMvc.perform(get("/api/borrow/history/all")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)));
    }

    @Test
    @WithMockUser(username = "patronTestUser", roles = {"PATRON"})
    void getAllBorrowingHistory_asPatron_shouldReturnForbidden() throws Exception {
        mockMvc.perform(get("/api/borrow/history/all"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "librarianTestUser", roles = {"LIBRARIAN"})
    void getOverdueBooks_asLibrarian_shouldReturnOverdueBooks() throws Exception {
        createBorrowingRecord(availableBook1, patronUser, LocalDate.now().minusDays(20), LocalDate.now().minusDays(5), null);
        createBorrowingRecord(availableBook2, librarianUser, LocalDate.now().minusDays(2), LocalDate.now().plusDays(12), null);

        mockMvc.perform(get("/api/borrow/overdue")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].bookTitle", is(availableBook1.getTitle())));
    }

    @Test
    @WithMockUser(username = "librarianTestUser", roles = {"LIBRARIAN"})
    void getOverdueBooks_asLibrarian_whenNoOverdueBooks_shouldReturnEmptyList() throws Exception {
        createBorrowingRecord(availableBook2, librarianUser, LocalDate.now().minusDays(2), LocalDate.now().plusDays(12), null);

        mockMvc.perform(get("/api/borrow/overdue")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    @WithMockUser(username = "patronTestUser", roles = {"PATRON"})
    void getOverdueBooks_asPatron_shouldReturnForbidden() throws Exception {
        mockMvc.perform(get("/api/borrow/overdue"))
                .andExpect(status().isForbidden());
    }

    @Test
    void getOverdueBooks_asUnauthenticatedUser_shouldReturnForbiddenOrUnauthorized() throws Exception {
        mockMvc.perform(get("/api/borrow/overdue"))
                .andExpect(status().isForbidden());
    }
}