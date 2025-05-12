package com.library.library_management_system.unit;

import com.library.library_management_system.controller.FineController;
import com.library.library_management_system.dto.response.FineResponse;
import com.library.library_management_system.entity.FineStatus;
import com.library.library_management_system.entity.Role;
import com.library.library_management_system.entity.User;
import com.library.library_management_system.exception.ResourceNotFoundException;
import com.library.library_management_system.service.FineService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FineControllerTest {

    @Mock
    private FineService fineService;

    @InjectMocks
    private FineController fineController;

    private User patronUser;
    private User librarianUser;
    private FineResponse fineResponsePending;
    private FineResponse fineResponsePaid;

    @BeforeEach
    void setUp() {
        patronUser = User.builder().id(1L).username("patronTest").role(Role.PATRON).build();
        librarianUser = User.builder().id(2L).username("librarianTest").role(Role.LIBRARIAN).build();

        fineResponsePending = FineResponse.builder()
                .id(101L)
                .userId(patronUser.getId())
                .username(patronUser.getUsername())
                .borrowingRecordId(201L)
                .amount(new BigDecimal("10.50"))
                .issueDate(LocalDate.now().minusDays(5))
                .status(FineStatus.PENDING)
                .build();

        fineResponsePaid = FineResponse.builder()
                .id(102L)
                .userId(patronUser.getId())
                .username(patronUser.getUsername())
                .borrowingRecordId(202L)
                .amount(new BigDecimal("5.00"))
                .issueDate(LocalDate.now().minusDays(10))
                .paidDate(LocalDate.now().minusDays(1))
                .status(FineStatus.PAID)
                .build();
    }

    @Test
    void getMyFines_whenCalled_shouldCallServiceAndReturnOk() {
        List<FineResponse> fines = Collections.singletonList(fineResponsePending);
        when(fineService.getFinesForUser(patronUser.getId())).thenReturn(fines);

        ResponseEntity<List<FineResponse>> responseEntity = fineController.getMyFines(patronUser);

        assertNotNull(responseEntity);
        assertEquals(HttpStatus.OK, responseEntity.getStatusCode());
        assertEquals(fines, responseEntity.getBody());
        verify(fineService, times(1)).getFinesForUser(patronUser.getId());
    }

    @Test
    void getFinesForUserByLibrarian_whenUserExists_shouldCallServiceAndReturnOk() {
        Long targetUserId = patronUser.getId();
        List<FineResponse> fines = Collections.singletonList(fineResponsePending);
        when(fineService.getFinesForUser(targetUserId)).thenReturn(fines);

        ResponseEntity<List<FineResponse>> responseEntity = fineController.getFinesForUserByLibrarian(targetUserId);

        assertNotNull(responseEntity);
        assertEquals(HttpStatus.OK, responseEntity.getStatusCode());
        assertEquals(fines, responseEntity.getBody());
        verify(fineService, times(1)).getFinesForUser(targetUserId);
    }

    @Test
    void getAllPendingFines_shouldCallServiceAndReturnOk() {
        List<FineResponse> pendingFines = Collections.singletonList(fineResponsePending);
        when(fineService.getAllFinesByStatus(FineStatus.PENDING)).thenReturn(pendingFines);

        ResponseEntity<List<FineResponse>> responseEntity = fineController.getAllPendingFines();

        assertNotNull(responseEntity);
        assertEquals(HttpStatus.OK, responseEntity.getStatusCode());
        assertEquals(pendingFines, responseEntity.getBody());
        verify(fineService, times(1)).getAllFinesByStatus(FineStatus.PENDING);
    }

    @Test
    void getAllFines_shouldCallServiceAndReturnOk() {
        List<FineResponse> allFines = List.of(fineResponsePending, fineResponsePaid);
        when(fineService.getAllFines()).thenReturn(allFines);

        ResponseEntity<List<FineResponse>> responseEntity = fineController.getAllFines();

        assertNotNull(responseEntity);
        assertEquals(HttpStatus.OK, responseEntity.getStatusCode());
        assertEquals(allFines, responseEntity.getBody());
        verify(fineService, times(1)).getAllFines();
    }

    @Test
    void payFine_whenFineExistsAndPending_shouldCallServiceAndReturnOk() {
        Long fineIdToPay = fineResponsePending.getId();
        FineResponse paidFineMock = FineResponse.builder()
                .id(fineIdToPay)
                .status(FineStatus.PAID)
                .paidDate(LocalDate.now())
                .amount(fineResponsePending.getAmount())
                .userId(fineResponsePending.getUserId())
                .username(fineResponsePending.getUsername())
                .borrowingRecordId(fineResponsePending.getBorrowingRecordId())
                .issueDate(fineResponsePending.getIssueDate())
                .build();
        when(fineService.payFine(fineIdToPay)).thenReturn(paidFineMock);

        ResponseEntity<FineResponse> responseEntity = fineController.payFine(fineIdToPay);

        assertNotNull(responseEntity);
        assertEquals(HttpStatus.OK, responseEntity.getStatusCode());
        assertNotNull(responseEntity.getBody());
        assertEquals(FineStatus.PAID, responseEntity.getBody().getStatus());
        assertEquals(fineIdToPay, responseEntity.getBody().getId());
        assertNotNull(responseEntity.getBody().getPaidDate());
        verify(fineService, times(1)).payFine(fineIdToPay);
    }

    @Test
    void payFine_whenServiceThrowsResourceNotFound_shouldPropagateException() {
        Long nonExistentFineId = 999L;
        when(fineService.payFine(nonExistentFineId))
                .thenThrow(new ResourceNotFoundException("Fine not found with id: " + nonExistentFineId));

        ResourceNotFoundException exception = assertThrows(ResourceNotFoundException.class, () -> {
            fineController.payFine(nonExistentFineId);
        });
        assertEquals("Fine not found with id: " + nonExistentFineId, exception.getMessage());
        verify(fineService, times(1)).payFine(nonExistentFineId);
    }

    @Test
    void payFine_whenServiceThrowsIllegalStateForAlreadyPaid_shouldPropagateException() {
        Long alreadyPaidFineId = fineResponsePaid.getId();
        when(fineService.payFine(alreadyPaidFineId))
                .thenThrow(new IllegalStateException("Fine with id " + alreadyPaidFineId + " has already been paid."));

        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> {
            fineController.payFine(alreadyPaidFineId);
        });
        assertEquals("Fine with id " + alreadyPaidFineId + " has already been paid.", exception.getMessage());
        verify(fineService, times(1)).payFine(alreadyPaidFineId);
    }


    @Test
    void waiveFine_whenFineExistsAndPending_shouldCallServiceAndReturnOk() {
        Long fineIdToWaive = fineResponsePending.getId();
        FineResponse waivedFineMock = FineResponse.builder()
                .id(fineIdToWaive)
                .status(FineStatus.WAIVED)
                .amount(fineResponsePending.getAmount())
                .userId(fineResponsePending.getUserId())
                .username(fineResponsePending.getUsername())
                .borrowingRecordId(fineResponsePending.getBorrowingRecordId())
                .issueDate(fineResponsePending.getIssueDate())
                .build();
        when(fineService.waiveFine(fineIdToWaive)).thenReturn(waivedFineMock);

        ResponseEntity<FineResponse> responseEntity = fineController.waiveFine(fineIdToWaive);

        assertNotNull(responseEntity);
        assertEquals(HttpStatus.OK, responseEntity.getStatusCode());
        assertNotNull(responseEntity.getBody());
        assertEquals(FineStatus.WAIVED, responseEntity.getBody().getStatus());
        assertEquals(fineIdToWaive, responseEntity.getBody().getId());
        verify(fineService, times(1)).waiveFine(fineIdToWaive);
    }

    @Test
    void waiveFine_whenServiceThrowsResourceNotFound_shouldPropagateException() {
        Long nonExistentFineId = 999L;
        when(fineService.waiveFine(nonExistentFineId))
                .thenThrow(new ResourceNotFoundException("Fine not found with id: " + nonExistentFineId));

        ResourceNotFoundException exception = assertThrows(ResourceNotFoundException.class, () -> {
            fineController.waiveFine(nonExistentFineId);
        });
        assertEquals("Fine not found with id: " + nonExistentFineId, exception.getMessage());
        verify(fineService, times(1)).waiveFine(nonExistentFineId);
    }
}

