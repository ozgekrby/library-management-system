package com.library.library_management_system.repository;

import com.library.library_management_system.entity.*;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;

public interface ReservationRepository extends JpaRepository<Reservation, Long> {
    boolean existsByUserAndBookAndStatusIn(User user, Book book, List<ReservationStatus> statuses);
    List<Reservation> findByUserAndStatusInOrderByReservationDateTimeAsc(User user, List<ReservationStatus> statuses);
    List<Reservation> findByBookAndStatusOrderByReservationDateTimeAsc(Book book, ReservationStatus status);
    Optional<Reservation> findFirstByBookAndStatusOrderByReservationDateTimeAsc(Book book, ReservationStatus status);
    List<Reservation> findByUserAndBookAndStatusIn(User user, Book book, List<ReservationStatus> statuses);
}