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
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class FineControllerTest {

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
    private FineRepository fineRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private UserDetailsServiceImpl userDetailsServiceImpl;

    private User patronForFineTest;
    private User librarianForFineTest;
    private Book bookForFine;
    private BorrowingRecord overdueBorrowingRecord;
    private Fine pendingFine;
    private Fine paidFine;

    @BeforeEach
    void setUp() {
        fineRepository.deleteAllInBatch();
        borrowingRecordRepository.deleteAllInBatch();
        bookRepository.deleteAllInBatch();
        userRepository.deleteAllInBatch();

        patronForFineTest = User.builder()
                .username("finePatron1")
                .password(passwordEncoder.encode("password"))
                .email("finepatron1@example.com")
                .fullName("Fine Patron One")
                .role(Role.PATRON)
                .build();
        userRepository.saveAndFlush(patronForFineTest);

        librarianForFineTest = User.builder()
                .username("fineLibrarian")
                .password(passwordEncoder.encode("password"))
                .email("finelibrarian@example.com")
                .fullName("Fine Librarian")
                .role(Role.LIBRARIAN)
                .build();
        userRepository.saveAndFlush(librarianForFineTest);

        bookForFine = Book.builder().title("Book for Fine Test").author("Author Fine").isbn("999888777XYZ")
                .publicationDate(LocalDate.now().minusYears(1)).genre("Test").totalCopies(1).availableCopies(0).build();
        bookRepository.saveAndFlush(bookForFine);

        overdueBorrowingRecord = BorrowingRecord.builder().user(patronForFineTest).book(bookForFine)
                .borrowDate(LocalDate.now().minusDays(20)).dueDate(LocalDate.now().minusDays(10))
                .returnDate(LocalDate.now().minusDays(5)).build();
        borrowingRecordRepository.saveAndFlush(overdueBorrowingRecord);

        pendingFine = Fine.builder().borrowingRecord(overdueBorrowingRecord).user(patronForFineTest)
                .amount(new BigDecimal("25.00")).issueDate(LocalDate.now().minusDays(5))
                .status(FineStatus.PENDING).build();
        fineRepository.saveAndFlush(pendingFine);

        User anotherPatron = User.builder().username("anotherPatron").password(passwordEncoder.encode("p")).email("ap@e.c").fullName("AP").role(Role.PATRON).build();
        userRepository.saveAndFlush(anotherPatron);
        Book anotherBook = Book.builder().title("Another Book").author("AA").isbn("666555444").publicationDate(LocalDate.now()).genre("G").totalCopies(1).availableCopies(1).build();
        bookRepository.saveAndFlush(anotherBook);
        BorrowingRecord recordForPaid = BorrowingRecord.builder().user(anotherPatron).book(anotherBook).borrowDate(LocalDate.now().minusDays(30)).dueDate(LocalDate.now().minusDays(20)).returnDate(LocalDate.now().minusDays(19)).build();
        borrowingRecordRepository.saveAndFlush(recordForPaid);
        paidFine = Fine.builder().borrowingRecord(recordForPaid).user(anotherPatron)
                .amount(new BigDecimal("5.00")).issueDate(LocalDate.now().minusDays(19))
                .paidDate(LocalDate.now().minusDays(18)).status(FineStatus.PAID).build();
        fineRepository.saveAndFlush(paidFine);
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
    void getMyFines_whenPatronHasFines_shouldReturnTheirFines() throws Exception {
        setupSecurityContext("finePatron1");

        mockMvc.perform(get("/api/fines/me")
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(pendingFine.getId().intValue()))
                .andExpect(jsonPath("$[0].amount").value(25.00));
    }

    @Test
    void getFinesForUserByLibrarian_whenUserExists_shouldReturnUserFines() throws Exception {
        setupSecurityContext("fineLibrarian");

        mockMvc.perform(get("/api/fines/user/" + patronForFineTest.getId())
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(pendingFine.getId().intValue()));
    }

    @Test
    void getAllPendingFines_whenCalledByLibrarian_shouldReturnPendingFines() throws Exception {
        setupSecurityContext("fineLibrarian");

        mockMvc.perform(get("/api/fines/pending")
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].status").value(FineStatus.PENDING.toString()));
    }

    @Test
    void getAllFines_whenCalledByLibrarian_shouldReturnAllFines() throws Exception {
        setupSecurityContext("fineLibrarian");

        mockMvc.perform(get("/api/fines/all")
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)));
    }

    @Test
    void payFine_whenLibrarianPaysExistingPendingFine_shouldReturnPaidFine() throws Exception {
        setupSecurityContext("fineLibrarian");

        mockMvc.perform(put("/api/fines/" + pendingFine.getId() + "/pay")
                        .with(csrf()))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(pendingFine.getId().intValue()))
                .andExpect(jsonPath("$.status").value(FineStatus.PAID.toString()))
                .andExpect(jsonPath("$.paidDate").value(LocalDate.now().toString()));
    }

    @Test
    void payFine_whenPatronTriesToPayFine_shouldReturnForbidden() throws Exception {
        setupSecurityContext("finePatron1");

        mockMvc.perform(put("/api/fines/" + pendingFine.getId() + "/pay")
                        .with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    void waiveFine_whenLibrarianWaivesExistingPendingFine_shouldReturnWaivedFine() throws Exception {
        setupSecurityContext("fineLibrarian");

        mockMvc.perform(put("/api/fines/" + pendingFine.getId() + "/waive")
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(pendingFine.getId().intValue()))
                .andExpect(jsonPath("$.status").value(FineStatus.WAIVED.toString()));
    }

    @Test
    void payFine_whenFineNotFound_shouldReturnNotFound() throws Exception {
        setupSecurityContext("fineLibrarian");
        long nonExistentFineId = 9999L;

        mockMvc.perform(put("/api/fines/" + nonExistentFineId + "/pay")
                        .with(csrf()))
                .andExpect(status().isNotFound());
    }

    @Test
    void payFine_whenFineAlreadyPaid_shouldReturnBadRequest() throws Exception {
        setupSecurityContext("fineLibrarian");

        mockMvc.perform(put("/api/fines/" + paidFine.getId() + "/pay")
                        .with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", is("Fine with id " + paidFine.getId() + " has already been paid.")));
    }
}