package com.library.library_management_system.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.library.library_management_system.entity.*;
import com.library.library_management_system.repository.*;
import com.library.library_management_system.security.UserDetailsServiceImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Arrays;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ReportControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private BookRepository bookRepository;

    @Autowired
    private BorrowingRecordRepository borrowingRecordRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private UserDetailsServiceImpl userDetailsServiceImpl;

    private User librarian;
    private User patron;
    private Book book1, book2;

    @BeforeEach
    void setUp() {
        borrowingRecordRepository.deleteAllInBatch();
        bookRepository.deleteAllInBatch();
        userRepository.deleteAllInBatch();

        librarian = User.builder().username("reportLibrarian").password(passwordEncoder.encode("pass"))
                .email("reportlib@mail.com").fullName("Report Librarian").role(Role.LIBRARIAN).build();
        userRepository.saveAndFlush(librarian);

        patron = User.builder().username("reportPatron").password(passwordEncoder.encode("pass"))
                .email("reportpatron@mail.com").fullName("Report Patron").role(Role.PATRON).build();
        userRepository.saveAndFlush(patron);

        book1 = Book.builder().title("Popular Book").author("A. Writer").isbn("111000111")
                .publicationDate(LocalDate.now()).genre("Fiction").totalCopies(2).availableCopies(0).build();
        bookRepository.saveAndFlush(book1);

        book2 = Book.builder().title("Less Popular Book").author("B. Scribe").isbn("222000222")
                .publicationDate(LocalDate.now()).genre("History").totalCopies(1).availableCopies(1).build();
        bookRepository.saveAndFlush(book2);

        BorrowingRecord br1 = BorrowingRecord.builder().book(book1).user(patron).borrowDate(LocalDate.now().minusDays(10)).dueDate(LocalDate.now().minusDays(3)).returnDate(LocalDate.now().minusDays(1)).build();
        BorrowingRecord br2 = BorrowingRecord.builder().book(book1).user(librarian).borrowDate(LocalDate.now().minusDays(5)).dueDate(LocalDate.now().plusDays(2)).returnDate(LocalDate.now()).build();
        BorrowingRecord br3 = BorrowingRecord.builder().book(book2).user(patron).borrowDate(LocalDate.now().minusDays(2)).dueDate(LocalDate.now().plusDays(5)).returnDate(null).build();
        borrowingRecordRepository.saveAllAndFlush(Arrays.asList(br1, br2, br3));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private void setupSecurityContext(String username) {
        UserDetails userDetails = userDetailsServiceImpl.loadUserByUsername(username);
        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                userDetails, null, userDetails.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    @Test
    void getTopBorrowedBooks_whenLibrarian_shouldReturnPaginatedReport() throws Exception {
        setupSecurityContext("reportLibrarian");

        mockMvc.perform(get("/api/reports/top-borrowed-books")
                        .param("page", "0")
                        .param("size", "5")
                        .with(csrf()))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.content", hasSize(2)))
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[0].bookTitle").value("Popular Book"))
                .andExpect(jsonPath("$.content[0].borrowCount").value(2))
                .andExpect(jsonPath("$.content[1].bookTitle").value("Less Popular Book"))
                .andExpect(jsonPath("$.content[1].borrowCount").value(1));
    }

    @Test
    void getUserActivityReport_whenLibrarian_shouldReturnPaginatedReport() throws Exception {
        setupSecurityContext("reportLibrarian");

        mockMvc.perform(get("/api/reports/user-activity")
                        .param("page", "0")
                        .param("size", "5")
                        .param("sort", "username,asc")
                        .with(csrf()))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.content", hasSize(2)))
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[0].username").value(librarian.getUsername()))
                .andExpect(jsonPath("$.content[0].totalBorrows").value(1))
                .andExpect(jsonPath("$.content[0].activeBorrows").value(0))
                .andExpect(jsonPath("$.content[1].username").value(patron.getUsername()))
                .andExpect(jsonPath("$.content[1].totalBorrows").value(2))
                .andExpect(jsonPath("$.content[1].activeBorrows").value(1));
    }

    @Test
    void getTopBorrowedBooks_whenPatron_shouldReturnForbidden() throws Exception {
        setupSecurityContext("reportPatron");

        mockMvc.perform(get("/api/reports/top-borrowed-books")
                        .with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    void getUserActivityReport_whenPatron_shouldReturnForbidden() throws Exception {
        setupSecurityContext("reportPatron");

        mockMvc.perform(get("/api/reports/user-activity")
                        .with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    void getTopBorrowedBooks_whenUnauthenticated_shouldReturnUnauthorized() throws Exception {
        mockMvc.perform(get("/api/reports/top-borrowed-books")
                        .with(csrf()))
                .andExpect(status().isForbidden());
    }
}
