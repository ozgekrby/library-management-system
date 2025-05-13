package com.library.library_management_system.service;

import com.library.library_management_system.dto.response.FineResponse;
import com.library.library_management_system.entity.*;
import com.library.library_management_system.exception.ResourceNotFoundException;
import com.library.library_management_system.repository.FineRepository;
import com.library.library_management_system.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class FineService {

    private final FineRepository fineRepository;
    private final UserRepository userRepository;

    @Value("${library.fine.amount-per-day:1.00}")
    private BigDecimal dailyFineAmount;

    @Value("${library.fine.grace-period-days:0}")
    private int gracePeriodDays;

    //Creates or updates a fine for an overdue returned book.
    @Transactional
    public FineResponse createOrUpdateFineForOverdueBook(BorrowingRecord borrowingRecord) {
        if (borrowingRecord.getReturnDate() == null) {
            log.warn("Attempted to create fine for a book not yet returned. Borrowing ID: {}", borrowingRecord.getId());
            throw new IllegalStateException("Fine can only be calculated for returned books.");
        }

        long overdueDays = ChronoUnit.DAYS.between(borrowingRecord.getDueDate(), borrowingRecord.getReturnDate());

        if (overdueDays <= gracePeriodDays) {
            log.info("Book returned within grace period or on time. No fine for borrowing ID: {}", borrowingRecord.getId());
            return null;
        }
        long actualOverdueDays = Math.max(0, overdueDays - gracePeriodDays);


        BigDecimal calculatedAmount = dailyFineAmount.multiply(BigDecimal.valueOf(actualOverdueDays));
        Fine existingFine = fineRepository.findByBorrowingRecordId(borrowingRecord.getId()).orElse(null);

        if (existingFine != null) {
            if (existingFine.getStatus() == FineStatus.PAID) {
                log.info("Fine for borrowing ID {} was already paid. No new fine created.", borrowingRecord.getId());
                return mapToFineResponse(existingFine);
            }

            log.info("Fine already exists for borrowing ID {}. Current amount: {}", borrowingRecord.getId(), existingFine.getAmount());
            if (calculatedAmount.compareTo(existingFine.getAmount()) != 0 && actualOverdueDays > 0) {
                log.warn("Recalculated fine amount {} differs from existing fine amount {} for borrowing ID {}. Updating.",
                        calculatedAmount, existingFine.getAmount(), borrowingRecord.getId());
                existingFine.setAmount(calculatedAmount);
                existingFine.setIssueDate(LocalDate.now());
                Fine updatedFine = fineRepository.save(existingFine);
                return mapToFineResponse(updatedFine);
            }
            return mapToFineResponse(existingFine);
        }

        if (actualOverdueDays <= 0) {
            log.info("Book returned on time or within grace period after recalculation. No fine for borrowing ID: {}", borrowingRecord.getId());
            return null;
        }


        Fine newFine = Fine.builder()
                .borrowingRecord(borrowingRecord)
                .user(borrowingRecord.getUser())
                .amount(calculatedAmount)
                .issueDate(LocalDate.now())
                .status(FineStatus.PENDING)
                .build();

        Fine savedFine = fineRepository.save(newFine);
        log.info("Fine created for borrowing ID {}. Amount: {}, User: {}",
                savedFine.getBorrowingRecord().getId(), savedFine.getAmount(), savedFine.getUser().getUsername());
        return mapToFineResponse(savedFine);
    }

    //Marks a fine as paid.
    @Transactional
    public FineResponse payFine(Long fineId) {
        Fine fine = fineRepository.findById(fineId)
                .orElseThrow(() -> new ResourceNotFoundException("Fine not found with id: " + fineId));

        if (fine.getStatus() == FineStatus.PAID) {
            throw new IllegalStateException("Fine with id " + fineId + " has already been paid.");
        }

        fine.setStatus(FineStatus.PAID);
        fine.setPaidDate(LocalDate.now());
        Fine paidFine = fineRepository.save(fine);
        log.info("Fine with id {} marked as PAID for user {}", paidFine.getId(), paidFine.getUser().getUsername());
        return mapToFineResponse(paidFine);
    }
    //Waives (forgives) a fine if it is still pending.
    @Transactional
    public FineResponse waiveFine(Long fineId) {
        Fine fine = fineRepository.findById(fineId)
                .orElseThrow(() -> new ResourceNotFoundException("Fine not found with id: " + fineId));

        if (fine.getStatus() == FineStatus.PAID) {
            throw new IllegalStateException("Cannot waive a fine (id: " + fineId + ") that has already been paid.");
        }
        if (fine.getStatus() == FineStatus.WAIVED) {
            log.info("Fine with id {} was already waived.", fineId);
            return mapToFineResponse(fine);
        }


        fine.setStatus(FineStatus.WAIVED);
        Fine waivedFine = fineRepository.save(fine);
        log.info("Fine with id {} WAIVED for user {}", waivedFine.getId(), waivedFine.getUser().getUsername());
        return mapToFineResponse(waivedFine);
    }

    //Retrieves all fines for a specific user.
    @Transactional(readOnly = true)
    public List<FineResponse> getFinesForUser(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + userId));
        return fineRepository.findByUser(user).stream()
                .map(this::mapToFineResponse)
                .collect(Collectors.toList());
    }

    //Retrieves all fines for a specific user by fine status.
    @Transactional(readOnly = true)
    public List<FineResponse> getFinesForUserByStatus(Long userId, FineStatus status) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + userId));
        return fineRepository.findByUserAndStatus(user, status).stream()
                .map(this::mapToFineResponse)
                .collect(Collectors.toList());
    }

    //Retrieves all fines in the system with a specific status.
    @Transactional(readOnly = true)
    public List<FineResponse> getAllFinesByStatus(FineStatus status) {
        return fineRepository.findByStatus(status).stream()
                .map(this::mapToFineResponse)
                .collect(Collectors.toList());
    }

    //Retrieves all fines in the system regardless of their status.
    @Transactional(readOnly = true)
    public List<FineResponse> getAllFines() {
        return fineRepository.findAll().stream()
                .map(this::mapToFineResponse)
                .collect(Collectors.toList());
    }

    //Maps a Fine entity to a FineResponse DTO.
    private FineResponse mapToFineResponse(Fine fine) {
        return FineResponse.builder()
                .id(fine.getId())
                .borrowingRecordId(fine.getBorrowingRecord().getId())
                .userId(fine.getUser().getId())
                .username(fine.getUser().getUsername())
                .bookTitle(fine.getBorrowingRecord().getBook().getTitle())
                .amount(fine.getAmount())
                .issueDate(fine.getIssueDate())
                .paidDate(fine.getPaidDate())
                .status(fine.getStatus())
                .build();
    }
}
