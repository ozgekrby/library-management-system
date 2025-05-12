package com.library.library_management_system.unit;

import com.library.library_management_system.dto.response.FineResponse;
import com.library.library_management_system.entity.*;
import com.library.library_management_system.exception.ResourceNotFoundException;
import com.library.library_management_system.repository.FineRepository;
import com.library.library_management_system.repository.UserRepository;
import com.library.library_management_system.service.FineService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@ActiveProfiles("test")
class FineServiceTest {

    @Mock
    private FineRepository fineRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private FineService fineService;

    private User user;
    private Book book;
    private BorrowingRecord onTimeRecord;
    private BorrowingRecord overdueRecord;
    private BorrowingRecord withinGracePeriodRecord;
    private Fine pendingFine;
    private Fine paidFine;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(fineService, "dailyFineAmount", new BigDecimal("1.00"));
        ReflectionTestUtils.setField(fineService, "gracePeriodDays", 2);

        user = User.builder().id(1L).username("testuser").role(Role.PATRON).build();
        book = Book.builder().id(1L).title("Test Book").build();

        onTimeRecord = BorrowingRecord.builder()
                .id(1L)
                .user(user)
                .book(book)
                .borrowDate(LocalDate.now().minusDays(10))
                .dueDate(LocalDate.now().minusDays(3))
                .returnDate(LocalDate.now().minusDays(3))
                .build();

        overdueRecord = BorrowingRecord.builder()
                .id(2L)
                .user(user)
                .book(book)
                .borrowDate(LocalDate.now().minusDays(10))
                .dueDate(LocalDate.now().minusDays(7))
                .returnDate(LocalDate.now().minusDays(2))
                .build();

        withinGracePeriodRecord = BorrowingRecord.builder()
                .id(3L)
                .user(user)
                .book(book)
                .borrowDate(LocalDate.now().minusDays(10))
                .dueDate(LocalDate.now().minusDays(3))
                .returnDate(LocalDate.now().minusDays(2))
                .build();

        pendingFine = Fine.builder()
                .id(10L)
                .borrowingRecord(overdueRecord)
                .user(user)
                .amount(new BigDecimal("5.00"))
                .issueDate(LocalDate.now().minusDays(1))
                .status(FineStatus.PENDING)
                .build();

        paidFine = Fine.builder()
                .id(11L)
                .borrowingRecord(onTimeRecord)
                .user(user)
                .amount(new BigDecimal("2.00"))
                .issueDate(LocalDate.now().minusDays(5))
                .paidDate(LocalDate.now().minusDays(4))
                .status(FineStatus.PAID)
                .build();
    }

    @Test
    void createOrUpdateFineForOverdueBook_whenBookNotReturned_shouldThrowIllegalStateException() {
        BorrowingRecord notReturnedRecord = BorrowingRecord.builder().id(99L).returnDate(null).build();
        assertThrows(IllegalStateException.class,
                () -> fineService.createOrUpdateFineForOverdueBook(notReturnedRecord));
        verify(fineRepository, never()).save(any());
    }

    @Test
    void createOrUpdateFineForOverdueBook_whenReturnedOnTime_shouldReturnNullAndNotCreateFine() {
        FineResponse response = fineService.createOrUpdateFineForOverdueBook(onTimeRecord);

        assertNull(response);
        verify(fineRepository, never()).save(any(Fine.class));
    }

    @Test
    void createOrUpdateFineForOverdueBook_whenReturnedWithinGracePeriod_shouldReturnNullAndNotCreateFine() {
        FineResponse response = fineService.createOrUpdateFineForOverdueBook(withinGracePeriodRecord);
        assertNull(response);
        verify(fineRepository, never()).save(any(Fine.class));
    }

    @Test
    void createOrUpdateFineForOverdueBook_whenOverdueAndNoExistingFine_shouldCreateFine() {
        when(fineRepository.findByBorrowingRecordId(overdueRecord.getId())).thenReturn(Optional.empty());
        when(fineRepository.save(any(Fine.class))).thenAnswer(invocation -> {
            Fine fineToSave = invocation.getArgument(0);
            fineToSave.setId(30L);
            return fineToSave;
        });

        FineResponse response = fineService.createOrUpdateFineForOverdueBook(overdueRecord);

        assertNotNull(response);
        assertEquals(new BigDecimal("3.00"), response.getAmount());
        assertEquals(FineStatus.PENDING, response.getStatus());
        assertEquals(overdueRecord.getUser().getUsername(), response.getUsername());
        verify(fineRepository, times(1)).save(any(Fine.class));
    }

    @Test
    void createOrUpdateFineForOverdueBook_whenOverdueAndExistingPendingFineMatchesAmount_shouldReturnExistingFine() {
        pendingFine.setAmount(new BigDecimal("3.00"));
        pendingFine.setBorrowingRecord(overdueRecord);

        when(fineRepository.findByBorrowingRecordId(overdueRecord.getId())).thenReturn(Optional.of(pendingFine));

        FineResponse response = fineService.createOrUpdateFineForOverdueBook(overdueRecord);

        assertNotNull(response);
        assertEquals(pendingFine.getId(), response.getId());
        assertEquals(new BigDecimal("3.00"), response.getAmount());
        verify(fineRepository, never()).save(any(Fine.class));
    }

    @Test
    void createOrUpdateFineForOverdueBook_whenOverdueAndExistingPendingFineDiffersAmount_shouldUpdateExistingFine() {
        pendingFine.setAmount(new BigDecimal("1.00"));
        pendingFine.setBorrowingRecord(overdueRecord);

        when(fineRepository.findByBorrowingRecordId(overdueRecord.getId())).thenReturn(Optional.of(pendingFine));
        when(fineRepository.save(any(Fine.class))).thenAnswer(invocation -> invocation.getArgument(0));

        FineResponse response = fineService.createOrUpdateFineForOverdueBook(overdueRecord);

        assertNotNull(response);
        assertEquals(pendingFine.getId(), response.getId());
        assertEquals(new BigDecimal("3.00"), response.getAmount());
        verify(fineRepository, times(1)).save(pendingFine);
    }

    @Test
    void createOrUpdateFineForOverdueBook_whenExistingFineIsPaid_shouldReturnPaidFineAndNotModify() {
        paidFine.setBorrowingRecord(overdueRecord);
        when(fineRepository.findByBorrowingRecordId(overdueRecord.getId())).thenReturn(Optional.of(paidFine));

        FineResponse response = fineService.createOrUpdateFineForOverdueBook(overdueRecord);

        assertNotNull(response);
        assertEquals(paidFine.getId(), response.getId());
        assertEquals(FineStatus.PAID, response.getStatus());
        verify(fineRepository, never()).save(any(Fine.class));
    }

    @Test
    void payFine_whenFineExistsAndPending_shouldMarkAsPaid() {
        when(fineRepository.findById(pendingFine.getId())).thenReturn(Optional.of(pendingFine));
        when(fineRepository.save(any(Fine.class))).thenAnswer(invocation -> invocation.getArgument(0));

        FineResponse response = fineService.payFine(pendingFine.getId());

        assertNotNull(response);
        assertEquals(FineStatus.PAID, response.getStatus());
        assertEquals(LocalDate.now(), response.getPaidDate());
        verify(fineRepository, times(1)).save(pendingFine);
    }

    @Test
    void payFine_whenFineNotFound_shouldThrowResourceNotFoundException() {
        when(fineRepository.findById(99L)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> fineService.payFine(99L));
    }

    @Test
    void payFine_whenFineAlreadyPaid_shouldThrowIllegalStateException() {
        when(fineRepository.findById(paidFine.getId())).thenReturn(Optional.of(paidFine));
        assertThrows(IllegalStateException.class, () -> fineService.payFine(paidFine.getId()));
    }

    @Test
    void waiveFine_whenFineExistsAndPending_shouldMarkAsWaived() {
        when(fineRepository.findById(pendingFine.getId())).thenReturn(Optional.of(pendingFine));
        when(fineRepository.save(any(Fine.class))).thenAnswer(invocation -> invocation.getArgument(0));

        FineResponse response = fineService.waiveFine(pendingFine.getId());

        assertNotNull(response);
        assertEquals(FineStatus.WAIVED, response.getStatus());
        assertNull(response.getPaidDate());
        verify(fineRepository, times(1)).save(pendingFine);
    }

    @Test
    void waiveFine_whenFineAlreadyPaid_shouldThrowIllegalStateException() {
        when(fineRepository.findById(paidFine.getId())).thenReturn(Optional.of(paidFine));
        assertThrows(IllegalStateException.class, () -> fineService.waiveFine(paidFine.getId()));
    }

    @Test
    void getFinesForUser_whenUserExists_shouldReturnFineList() {
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        when(fineRepository.findByUser(user)).thenReturn(Collections.singletonList(pendingFine));

        List<FineResponse> responses = fineService.getFinesForUser(user.getId());

        assertNotNull(responses);
        assertEquals(1, responses.size());
        assertEquals(pendingFine.getId(), responses.get(0).getId());
    }

    @Test
    void getFinesForUserByStatus_whenUserExists_shouldReturnFilteredFineList() {
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        when(fineRepository.findByUserAndStatus(user, FineStatus.PENDING)).thenReturn(Collections.singletonList(pendingFine));

        List<FineResponse> responses = fineService.getFinesForUserByStatus(user.getId(), FineStatus.PENDING);

        assertNotNull(responses);
        assertEquals(1, responses.size());
        assertEquals(pendingFine.getId(), responses.get(0).getId());
        assertEquals(FineStatus.PENDING, responses.get(0).getStatus());
    }

    @Test
    void getAllFinesByStatus_shouldReturnFilteredFineList() {
        when(fineRepository.findByStatus(FineStatus.PAID)).thenReturn(Collections.singletonList(paidFine));

        List<FineResponse> responses = fineService.getAllFinesByStatus(FineStatus.PAID);

        assertNotNull(responses);
        assertEquals(1, responses.size());
        assertEquals(paidFine.getId(), responses.get(0).getId());
    }

    @Test
    void getAllFines_shouldReturnAllFines() {
        when(fineRepository.findAll()).thenReturn(List.of(pendingFine, paidFine));

        List<FineResponse> responses = fineService.getAllFines();

        assertNotNull(responses);
        assertEquals(2, responses.size());
    }
}
