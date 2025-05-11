package com.library.library_management_system.repository;

import com.library.library_management_system.entity.*;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;

public interface FineRepository extends JpaRepository<Fine, Long> {
    List<Fine> findByUserAndStatus(User user, FineStatus status);
    List<Fine> findByUser(User user);
    List<Fine> findByStatus(FineStatus status);
    Optional<Fine> findByBorrowingRecordId(Long borrowingRecordId);
}
