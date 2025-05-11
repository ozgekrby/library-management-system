package com.library.library_management_system.repository;

import com.library.library_management_system.dto.response.TopBorrowedBookResponse;
import com.library.library_management_system.entity.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.util.*;

public interface BorrowingRecordRepository extends JpaRepository<BorrowingRecord, Long> {
    Optional<BorrowingRecord> findByBookAndUserAndReturnDateIsNull(Book book, User user);
    List<BorrowingRecord> findByUserAndReturnDateIsNull(User user);
    List<BorrowingRecord> findByReturnDateIsNullAndDueDateBefore(LocalDate today);
    boolean existsByBookAndReturnDateIsNull(Book book);
    boolean existsByUserAndReturnDateIsNull(User user);
    Optional<BorrowingRecord> findByIdAndReturnDateIsNull(Long id);
    Optional<BorrowingRecord> findByIdAndUserAndReturnDateIsNull(Long id, User user);
    List<BorrowingRecord> findByUserOrderByBorrowDateDesc(User user);
    boolean existsByBookAndUserAndReturnDateIsNull(Book book, User user);
    @Query("SELECT new com.library.library_management_system.dto.response.TopBorrowedBookResponse(b.id, b.title, b.author, b.isbn, COUNT(br.id)) " +
            "FROM BorrowingRecord br JOIN br.book b " +
            "GROUP BY b.id, b.title, b.author, b.isbn " +
            "ORDER BY COUNT(br.id) DESC")
    Page<TopBorrowedBookResponse> findTopBorrowedBooks(Pageable pageable);
}
