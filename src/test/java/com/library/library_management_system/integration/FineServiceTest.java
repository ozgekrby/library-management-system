package com.library.library_management_system.integration;

import com.library.library_management_system.dto.response.FineResponse;
import com.library.library_management_system.entity.*;
import com.library.library_management_system.exception.ResourceNotFoundException;
import com.library.library_management_system.repository.BookRepository;
import com.library.library_management_system.repository.BorrowingRecordRepository;
import com.library.library_management_system.repository.FineRepository;
import com.library.library_management_system.repository.UserRepository;
import com.library.library_management_system.service.FineService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
@TestPropertySource(properties = {
        "library.fine.amount-per-day=1.00",
        "library.fine.grace-period-days=2"
})
class FineServiceTest {

    @Autowired
    private FineService fineService;

    @Autowired
    private FineRepository fineRepository;

    @Autowired
    private BorrowingRecordRepository borrowingRecordRepository;

    @Autowired
    private BookRepository bookRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private User user1;
    private User user2;
    private Book book1;
    private BorrowingRecord record1_user1_overdue;
    private BorrowingRecord record2_user1_onTime;
    private BorrowingRecord record3_user1_grace;
    private BorrowingRecord record4_user2_overdue;

    @BeforeEach
    void setUp() {
        fineRepository.deleteAllInBatch();
        borrowingRecordRepository.deleteAllInBatch();
        bookRepository.deleteAllInBatch();
        userRepository.deleteAllInBatch();

        user1 = User.builder().username("user1_fine_test").password(passwordEncoder.encode("pass123"))
                .email("user1fine@example.com").fullName("User One Fine").role(Role.PATRON).build();
        user2 = User.builder().username("user2_fine_test").password(passwordEncoder.encode("pass123"))
                .email("user2fine@example.com").fullName("User Two Fine").role(Role.PATRON).build();
        userRepository.saveAll(List.of(user1, user2));

        book1 = Book.builder().title("Fine Test Book").author("Author").isbn("978-0000000001")
                .totalCopies(5).availableCopies(5).genre("Test").publicationDate(LocalDate.now().minusYears(1)).build();
        bookRepository.save(book1);

        record1_user1_overdue = BorrowingRecord.builder().book(book1).user(user1)
                .borrowDate(LocalDate.now().minusDays(15))
                .dueDate(LocalDate.now().minusDays(5))
                .returnDate(LocalDate.now())
                .build();
        borrowingRecordRepository.save(record1_user1_overdue);

        record2_user1_onTime = BorrowingRecord.builder().book(book1).user(user1)
                .borrowDate(LocalDate.now().minusDays(10))
                .dueDate(LocalDate.now().minusDays(1))
                .returnDate(LocalDate.now().minusDays(1))
                .build();
        borrowingRecordRepository.save(record2_user1_onTime);

        record3_user1_grace = BorrowingRecord.builder().book(book1).user(user1)
                .borrowDate(LocalDate.now().minusDays(8))
                .dueDate(LocalDate.now().minusDays(3))
                .returnDate(LocalDate.now().minusDays(2))
                .build();
        borrowingRecordRepository.save(record3_user1_grace);

        record4_user2_overdue = BorrowingRecord.builder().book(book1).user(user2)
                .borrowDate(LocalDate.now().minusDays(12))
                .dueDate(LocalDate.now().minusDays(4))
                .returnDate(LocalDate.now())
                .build();
        borrowingRecordRepository.save(record4_user2_overdue);
    }

    @AfterEach
    void tearDown() {
    }

    @Test
    void createOrUpdateFineForOverdueBook_whenBookNotReturned_shouldThrowIllegalStateException() {
        BorrowingRecord notReturnedRecord = BorrowingRecord.builder()
                .book(book1).user(user1)
                .borrowDate(LocalDate.now().minusDays(1))
                .dueDate(LocalDate.now().plusDays(1))
                .returnDate(null)
                .build();

        assertThrows(IllegalStateException.class,
                () -> fineService.createOrUpdateFineForOverdueBook(notReturnedRecord));
        assertEquals(0, fineRepository.count());
    }

    @Test
    void createOrUpdateFineForOverdueBook_whenReturnedOnTime_shouldReturnNullAndNotCreateFine() {
        FineResponse response = fineService.createOrUpdateFineForOverdueBook(record2_user1_onTime);
        assertNull(response);
        assertFalse(fineRepository.findByBorrowingRecordId(record2_user1_onTime.getId()).isPresent());
    }

    @Test
    void createOrUpdateFineForOverdueBook_whenReturnedWithinGracePeriod_shouldReturnNullAndNotCreateFine() {
        FineResponse response = fineService.createOrUpdateFineForOverdueBook(record3_user1_grace);
        assertNull(response);
        assertFalse(fineRepository.findByBorrowingRecordId(record3_user1_grace.getId()).isPresent());
    }

    @Test
    void createOrUpdateFineForOverdueBook_whenOverdueAndNoExistingFine_shouldCreateFine() {
        FineResponse response = fineService.createOrUpdateFineForOverdueBook(record1_user1_overdue);

        assertNotNull(response);
        assertEquals(new BigDecimal("3.00"), response.getAmount());
        assertEquals(FineStatus.PENDING, response.getStatus());
        assertEquals(user1.getId(), response.getUserId());
        assertEquals(record1_user1_overdue.getId(), response.getBorrowingRecordId());

        Optional<Fine> createdFineOpt = fineRepository.findByBorrowingRecordId(record1_user1_overdue.getId());
        assertTrue(createdFineOpt.isPresent());
        assertEquals(new BigDecimal("3.00"), createdFineOpt.get().getAmount());
    }

    @Test
    void createOrUpdateFineForOverdueBook_whenOverdueAndExistingPendingFineMatchesAmount_shouldReturnExistingFineWithoutUpdate() {
        Fine existingFine = Fine.builder()
                .borrowingRecord(record1_user1_overdue)
                .user(user1)
                .amount(new BigDecimal("3.00"))
                .issueDate(LocalDate.now().minusDays(1))
                .status(FineStatus.PENDING)
                .build();
        fineRepository.save(existingFine);
        long initialFineCount = fineRepository.count();
        LocalDate initialIssueDate = existingFine.getIssueDate();

        FineResponse response = fineService.createOrUpdateFineForOverdueBook(record1_user1_overdue);

        assertNotNull(response);
        assertEquals(existingFine.getId(), response.getId());
        assertEquals(new BigDecimal("3.00"), response.getAmount());
        assertEquals(initialIssueDate, response.getIssueDate());
        assertEquals(initialFineCount, fineRepository.count());
    }

    @Test
    void createOrUpdateFineForOverdueBook_whenOverdueAndExistingPendingFineDiffersAmount_shouldUpdateExistingFine() {
        Fine existingFine = Fine.builder()
                .borrowingRecord(record1_user1_overdue)
                .user(user1)
                .amount(new BigDecimal("1.00"))
                .issueDate(LocalDate.now().minusDays(1))
                .status(FineStatus.PENDING)
                .build();
        Fine savedExistingFine = fineRepository.save(existingFine);

        FineResponse response = fineService.createOrUpdateFineForOverdueBook(record1_user1_overdue);

        assertNotNull(response);
        assertEquals(savedExistingFine.getId(), response.getId());
        assertEquals(new BigDecimal("3.00"), response.getAmount());
        assertEquals(LocalDate.now(), response.getIssueDate());
        assertEquals(FineStatus.PENDING, response.getStatus());

        Optional<Fine> updatedFineOpt = fineRepository.findById(savedExistingFine.getId());
        assertTrue(updatedFineOpt.isPresent());
        assertEquals(new BigDecimal("3.00"), updatedFineOpt.get().getAmount());
        assertEquals(LocalDate.now(), updatedFineOpt.get().getIssueDate());
    }

    @Test
    void createOrUpdateFineForOverdueBook_whenExistingFineIsPaid_shouldReturnPaidFineAndNotModify() {
        Fine paidExistingFine = Fine.builder()
                .borrowingRecord(record1_user1_overdue)
                .user(user1)
                .amount(new BigDecimal("3.00"))
                .issueDate(LocalDate.now().minusDays(1))
                .paidDate(LocalDate.now().minusDays(1))
                .status(FineStatus.PAID)
                .build();
        fineRepository.save(paidExistingFine);
        long initialFineCount = fineRepository.count();

        FineResponse response = fineService.createOrUpdateFineForOverdueBook(record1_user1_overdue);

        assertNotNull(response);
        assertEquals(paidExistingFine.getId(), response.getId());
        assertEquals(FineStatus.PAID, response.getStatus());
        assertEquals(new BigDecimal("3.00"), response.getAmount());
        assertEquals(initialFineCount, fineRepository.count());
    }

    @Test
    void payFine_success() {
        Fine fineToPay = fineRepository.save(Fine.builder().borrowingRecord(record1_user1_overdue).user(user1)
                .amount(new BigDecimal("5.00")).issueDate(LocalDate.now()).status(FineStatus.PENDING).build());

        FineResponse response = fineService.payFine(fineToPay.getId());
        assertNotNull(response);
        assertEquals(FineStatus.PAID, response.getStatus());
        assertEquals(LocalDate.now(), response.getPaidDate());

        Optional<Fine> paidFineOpt = fineRepository.findById(fineToPay.getId());
        assertTrue(paidFineOpt.isPresent());
        assertEquals(FineStatus.PAID, paidFineOpt.get().getStatus());
        assertEquals(LocalDate.now(), paidFineOpt.get().getPaidDate());
    }

    @Test
    void payFine_whenFineNotFound_shouldThrowResourceNotFound() {
        assertThrows(ResourceNotFoundException.class, () -> fineService.payFine(999L));
    }

    @Test
    void payFine_whenAlreadyPaid_shouldThrowIllegalState() {
        Fine alreadyPaidFine = fineRepository.save(Fine.builder().borrowingRecord(record1_user1_overdue).user(user1)
                .amount(new BigDecimal("5.00")).issueDate(LocalDate.now()).status(FineStatus.PAID).paidDate(LocalDate.now().minusDays(1)).build());
        assertThrows(IllegalStateException.class, () -> fineService.payFine(alreadyPaidFine.getId()));
    }

    @Test
    void waiveFine_success() {
        Fine fineToWaive = fineRepository.save(Fine.builder().borrowingRecord(record1_user1_overdue).user(user1)
                .amount(new BigDecimal("5.00")).issueDate(LocalDate.now()).status(FineStatus.PENDING).build());

        FineResponse response = fineService.waiveFine(fineToWaive.getId());
        assertNotNull(response);
        assertEquals(FineStatus.WAIVED, response.getStatus());
        assertNull(response.getPaidDate());

        Optional<Fine> waivedFineOpt = fineRepository.findById(fineToWaive.getId());
        assertTrue(waivedFineOpt.isPresent());
        assertEquals(FineStatus.WAIVED, waivedFineOpt.get().getStatus());
    }

    @Test
    void waiveFine_whenAlreadyPaid_shouldThrowIllegalState() {
        Fine alreadyPaidFine = fineRepository.save(Fine.builder().borrowingRecord(record1_user1_overdue).user(user1)
                .amount(new BigDecimal("5.00")).issueDate(LocalDate.now()).status(FineStatus.PAID).paidDate(LocalDate.now().minusDays(1)).build());
        assertThrows(IllegalStateException.class, () -> fineService.waiveFine(alreadyPaidFine.getId()));
    }

    @Test
    void waiveFine_whenAlreadyWaived_shouldReturnWaivedFineWithoutChange() {
        Fine alreadyWaivedFine = fineRepository.save(Fine.builder().borrowingRecord(record1_user1_overdue).user(user1)
                .amount(new BigDecimal("5.00")).issueDate(LocalDate.now()).status(FineStatus.WAIVED).build());
        long initialCount = fineRepository.count();

        FineResponse response = fineService.waiveFine(alreadyWaivedFine.getId());
        assertNotNull(response);
        assertEquals(FineStatus.WAIVED, response.getStatus());
        assertEquals(initialCount, fineRepository.count());
    }

    @Test
    void getFinesForUser_shouldReturnUserFines() {
        fineService.createOrUpdateFineForOverdueBook(record1_user1_overdue);
        fineService.createOrUpdateFineForOverdueBook(record4_user2_overdue);

        List<FineResponse> user1Fines = fineService.getFinesForUser(user1.getId());
        assertEquals(1, user1Fines.size());
        assertEquals(user1.getId(), user1Fines.get(0).getUserId());
        assertEquals(new BigDecimal("3.00"), user1Fines.get(0).getAmount());

        List<FineResponse> user2Fines = fineService.getFinesForUser(user2.getId());
        assertEquals(1, user2Fines.size());
        assertEquals(user2.getId(), user2Fines.get(0).getUserId());
        assertEquals(new BigDecimal("2.00"), user2Fines.get(0).getAmount());
    }

    @Test
    void getFinesForUserByStatus_shouldReturnFilteredFines() {
        fineService.createOrUpdateFineForOverdueBook(record1_user1_overdue);
        Optional<Fine> pendingFineOpt = fineRepository.findByBorrowingRecordId(record1_user1_overdue.getId());
        assertTrue(pendingFineOpt.isPresent());
        Fine user1PendingFine = pendingFineOpt.get();

        BorrowingRecord anotherRecordUser1 = borrowingRecordRepository.save(BorrowingRecord.builder()
                .book(book1).user(user1).borrowDate(LocalDate.now().minusDays(20))
                .dueDate(LocalDate.now().minusDays(15)).returnDate(LocalDate.now().minusDays(14)).build());
        Fine user1PaidFine = fineRepository.save(Fine.builder().borrowingRecord(anotherRecordUser1).user(user1)
                .amount(new BigDecimal("1.00")).issueDate(LocalDate.now().minusDays(14))
                .status(FineStatus.PAID).paidDate(LocalDate.now().minusDays(13)).build());


        List<FineResponse> pendingFines = fineService.getFinesForUserByStatus(user1.getId(), FineStatus.PENDING);
        assertEquals(1, pendingFines.size());
        assertEquals(user1PendingFine.getId(), pendingFines.get(0).getId());

        List<FineResponse> paidFines = fineService.getFinesForUserByStatus(user1.getId(), FineStatus.PAID);
        assertEquals(1, paidFines.size());
        assertEquals(user1PaidFine.getId(), paidFines.get(0).getId());
    }

    @Test
    void getAllFinesByStatus_shouldReturnFilteredFines() {
        fineService.createOrUpdateFineForOverdueBook(record1_user1_overdue);
        fineService.createOrUpdateFineForOverdueBook(record4_user2_overdue);
        Fine paidFine = Fine.builder()
                .borrowingRecord(record2_user1_onTime)
                .user(user1)
                .amount(new BigDecimal("1.00"))
                .issueDate(LocalDate.now().minusDays(1))
                .status(FineStatus.PAID)
                .paidDate(LocalDate.now())
                .build();
        Fine savedPaidFine = fineRepository.save(paidFine);

        List<FineResponse> pendingFines = fineService.getAllFinesByStatus(FineStatus.PENDING);
        assertEquals(2, pendingFines.size());

        List<FineResponse> paidFinesList = fineService.getAllFinesByStatus(FineStatus.PAID);
        assertEquals(1, paidFinesList.size());
        assertEquals(savedPaidFine.getId(), paidFinesList.get(0).getId());
        assertEquals(new BigDecimal("1.00"), paidFinesList.get(0).getAmount());
    }

    @Test
    void getAllFines_shouldReturnAllFines() {
        fineService.createOrUpdateFineForOverdueBook(record1_user1_overdue);
        fineService.createOrUpdateFineForOverdueBook(record4_user2_overdue);

        List<FineResponse> allFines = fineService.getAllFines();
        assertEquals(2, allFines.size());
    }
}