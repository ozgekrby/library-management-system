package com.library.library_management_system.repository;

import com.library.library_management_system.entity.*;
import org.springframework.data.jpa.repository.JpaRepository;
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

}
