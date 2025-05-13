package com.library.library_management_system.service;

import com.library.library_management_system.dto.response.TopBorrowedBookResponse;
import com.library.library_management_system.dto.response.UserActivityReportResponse;
import com.library.library_management_system.repository.BorrowingRecordRepository;
import com.library.library_management_system.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReportService {

    private final BorrowingRecordRepository borrowingRecordRepository;
    private final UserRepository userRepository;

    //Retrieves a paginated report of the most frequently borrowed books.
    public Page<TopBorrowedBookResponse> getTopBorrowedBooks(Pageable pageable) {
        log.info("Fetching top borrowed books report with pageable: {}", pageable);
        return borrowingRecordRepository.findTopBorrowedBooks(pageable);
    }

    //Retrieves a paginated report of user activity including total and active borrowings.
    public Page<UserActivityReportResponse> getUserActivityReport(Pageable pageable) {
        log.info("Fetching user activity report with pageable: {}", pageable);
        return userRepository.findAll(pageable).map(user -> {
            long totalBorrows = borrowingRecordRepository.findByUserOrderByBorrowDateDesc(user).size();
            long activeBorrows = borrowingRecordRepository.findByUserAndReturnDateIsNull(user).size();
            return new UserActivityReportResponse(user.getId(), user.getUsername(), user.getFullName(), totalBorrows, activeBorrows);
        });
    }
}
