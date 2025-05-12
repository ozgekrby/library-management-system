package com.library.library_management_system.integration;

import com.library.library_management_system.dto.response.TopBorrowedBookResponse;
import com.library.library_management_system.dto.response.UserActivityReportResponse;
import com.library.library_management_system.entity.Book;
import com.library.library_management_system.entity.BorrowingRecord;
import com.library.library_management_system.entity.Role;
import com.library.library_management_system.entity.User;
import com.library.library_management_system.repository.BookRepository;
import com.library.library_management_system.repository.BorrowingRecordRepository;
import com.library.library_management_system.repository.FineRepository;
import com.library.library_management_system.repository.UserRepository;
import com.library.library_management_system.service.ReportService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
public class ReportServiceTest {

    @Autowired
    private ReportService reportService;

    @Autowired
    private BookRepository bookRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private BorrowingRecordRepository borrowingRecordRepository;

    @Autowired
    private FineRepository fineRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private User user1, user2, user3;
    private Book book1, book2, book3;

    @BeforeEach
    void setUp() {
        fineRepository.deleteAll();
        borrowingRecordRepository.deleteAll();
        bookRepository.deleteAll();
        userRepository.deleteAll();

        user1 = userRepository.save(User.builder().username("userone").fullName("User One FullName").password(passwordEncoder.encode("pass1")).email("u1@mail.com").role(Role.PATRON).build());
        user2 = userRepository.save(User.builder().username("usertwo").fullName("User Two FullName").password(passwordEncoder.encode("pass2")).email("u2@mail.com").role(Role.PATRON).build());
        user3 = userRepository.save(User.builder().username("userthree").fullName("User Three FullName").password(passwordEncoder.encode("pass3")).email("u3@mail.com").role(Role.PATRON).build());

        book1 = bookRepository.save(Book.builder().title("Book A").author("Author A").isbn("ISBN-A").publicationDate(LocalDate.now().minusYears(1)).genre("Fiction").totalCopies(5).availableCopies(5).build());
        book2 = bookRepository.save(Book.builder().title("Book B").author("Author B").isbn("ISBN-B").publicationDate(LocalDate.now().minusYears(2)).genre("Science").totalCopies(3).availableCopies(3).build());
        book3 = bookRepository.save(Book.builder().title("Book C").author("Author C").isbn("ISBN-C").publicationDate(LocalDate.now().minusMonths(6)).genre("History").totalCopies(4).availableCopies(4).build());

        createBorrowing(user1, book1, LocalDate.now().minusDays(30), LocalDate.now().minusDays(15));
        createBorrowing(user2, book1, LocalDate.now().minusDays(20), LocalDate.now().minusDays(5));
        createBorrowing(user3, book1, LocalDate.now().minusDays(10), null);
        createBorrowing(user1, book2, LocalDate.now().minusDays(25), null);
        createBorrowing(user2, book2, LocalDate.now().minusDays(5), null);

        createBorrowing(user3, book3, LocalDate.now().minusDays(15), LocalDate.now().minusDays(1));
    }

    private BorrowingRecord createBorrowing(User user, Book book, LocalDate borrowDate, LocalDate returnDate) {
        if (returnDate == null && book.getAvailableCopies() > 0) {
            book.setAvailableCopies(book.getAvailableCopies() - 1);
            bookRepository.save(book);
        } else if (returnDate != null && book.getAvailableCopies() < book.getTotalCopies()){
        }

        return borrowingRecordRepository.save(BorrowingRecord.builder()
                .user(user)
                .book(book)
                .borrowDate(borrowDate)
                .dueDate(borrowDate.plusWeeks(2))
                .returnDate(returnDate)
                .build());
    }


    @AfterEach
    void tearDown() {
    }

    @Test
    void getTopBorrowedBooks_shouldReturnPageOfTopBorrowedBooksSorted() {
        Pageable pageable = PageRequest.of(0, 10);
        Page<TopBorrowedBookResponse> result = reportService.getTopBorrowedBooks(pageable);

        assertNotNull(result);
        assertFalse(result.isEmpty());
        assertEquals(3, result.getTotalElements());

        List<TopBorrowedBookResponse> content = result.getContent();
        assertEquals("Book A", content.get(0).getBookTitle());
        assertEquals(3L, content.get(0).getBorrowCount());
        assertEquals("Book B", content.get(1).getBookTitle());
        assertEquals(2L, content.get(1).getBorrowCount());

        assertEquals("Book C", content.get(2).getBookTitle());
        assertEquals(1L, content.get(2).getBorrowCount());
    }

    @Test
    void getTopBorrowedBooks_withPagination_shouldReturnCorrectPage() {
        Pageable pageable = PageRequest.of(0, 1);
        Page<TopBorrowedBookResponse> result = reportService.getTopBorrowedBooks(pageable);

        assertNotNull(result);
        assertEquals(1, result.getContent().size());
        assertEquals(3, result.getTotalElements());
        assertEquals("Book A", result.getContent().get(0).getBookTitle());
        assertEquals(3L, result.getContent().get(0).getBorrowCount());

        Pageable pageableSecond = PageRequest.of(1, 1);
        Page<TopBorrowedBookResponse> resultSecond = reportService.getTopBorrowedBooks(pageableSecond);
        assertNotNull(resultSecond);
        assertEquals(1, resultSecond.getContent().size());
        assertEquals("Book B", resultSecond.getContent().get(0).getBookTitle());
    }

    @Test
    void getTopBorrowedBooks_whenNoBooksBorrowed_shouldReturnEmptyPage() {
        borrowingRecordRepository.deleteAll();
        Pageable pageable = PageRequest.of(0, 10);
        Page<TopBorrowedBookResponse> result = reportService.getTopBorrowedBooks(pageable);

        assertNotNull(result);
        assertTrue(result.isEmpty());
        assertEquals(0, result.getTotalElements());
    }

    @Test
    void getUserActivityReport_shouldReturnPageOfUserActivitiesSortedByUsername() {
        Pageable pageable = PageRequest.of(0, 10, Sort.by("username").ascending());
        Page<UserActivityReportResponse> result = reportService.getUserActivityReport(pageable);

        assertNotNull(result);
        assertEquals(3, result.getTotalElements());

        List<UserActivityReportResponse> content = result.getContent();
        assertEquals(user1.getId(), content.get(0).getUserId(), "İlk kullanıcı user1 (userone) olmalı");
        assertEquals(user1.getUsername(), content.get(0).getUsername());
        assertEquals(user3.getId(), content.get(1).getUserId(), "İkinci kullanıcı user3 (userthree) olmalı");
        assertEquals(user3.getUsername(), content.get(1).getUsername());
        assertEquals(user2.getId(), content.get(2).getUserId(), "Üçüncü kullanıcı user2 (usertwo) olmalı");
        assertEquals(user2.getUsername(), content.get(2).getUsername());

        UserActivityReportResponse activity1 = content.get(0);
        assertEquals(user1.getFullName(), activity1.getFullName());
        assertEquals(2L, activity1.getTotalBorrows());
        assertEquals(1L, activity1.getActiveBorrows());

        UserActivityReportResponse activity3 = content.get(1);
        assertEquals(user3.getFullName(), activity3.getFullName());
        assertEquals(user3.getUsername(), activity3.getUsername());
        assertEquals(2L, activity3.getTotalBorrows());
        assertEquals(1L, activity3.getActiveBorrows());
        UserActivityReportResponse activity2 = content.get(2);
        assertEquals(user2.getFullName(), activity2.getFullName());
        assertEquals(user2.getUsername(), activity2.getUsername());
        assertEquals(2L, activity2.getTotalBorrows());
        assertEquals(1L, activity2.getActiveBorrows());
    }

    @Test
    void getUserActivityReport_withPagination_shouldReturnCorrectPage() {
        Pageable pageable = PageRequest.of(0, 1, Sort.by("id").ascending());
        Page<UserActivityReportResponse> result = reportService.getUserActivityReport(pageable);
        assertNotNull(result);
        assertEquals(1, result.getContent().size());
        assertEquals(3, result.getTotalElements());
        assertEquals(user1.getId(), result.getContent().get(0).getUserId());
    }


    @Test
    void getUserActivityReport_whenNoUsers_shouldReturnEmptyPage() {
        fineRepository.deleteAll();
        borrowingRecordRepository.deleteAll();
        bookRepository.deleteAll();
        userRepository.deleteAll();

        Pageable pageable = PageRequest.of(0, 10);
        Page<UserActivityReportResponse> result = reportService.getUserActivityReport(pageable);

        assertNotNull(result);
        assertTrue(result.isEmpty());
        assertEquals(0, result.getTotalElements());
    }

    @Test
    void getUserActivityReport_whenUserHasNoBorrows_shouldShowZeroCounts() {
        fineRepository.deleteAll();
        borrowingRecordRepository.deleteAll();
        userRepository.deleteById(user2.getId());
        userRepository.deleteById(user3.getId());


        Pageable pageable = PageRequest.of(0, 10);
        Page<UserActivityReportResponse> result = reportService.getUserActivityReport(pageable);

        assertNotNull(result);
        assertEquals(1, result.getTotalElements());
        UserActivityReportResponse activity = result.getContent().get(0);
        assertEquals(user1.getId(), activity.getUserId());
        assertEquals(0L, activity.getTotalBorrows());
        assertEquals(0L, activity.getActiveBorrows());
    }
}
