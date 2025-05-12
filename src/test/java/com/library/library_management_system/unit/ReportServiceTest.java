package com.library.library_management_system.unit;

import com.library.library_management_system.dto.response.TopBorrowedBookResponse;
import com.library.library_management_system.dto.response.UserActivityReportResponse;
import com.library.library_management_system.entity.BorrowingRecord;
import com.library.library_management_system.entity.Role;
import com.library.library_management_system.entity.User;
import com.library.library_management_system.repository.BorrowingRecordRepository;
import com.library.library_management_system.repository.UserRepository;
import com.library.library_management_system.service.ReportService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.context.ActiveProfiles;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@ActiveProfiles("test")
class ReportServiceTest {

    @Mock
    private BorrowingRecordRepository borrowingRecordRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private ReportService reportService;

    private User user1;
    private User user2;
    private Pageable pageable;

    @BeforeEach
    void setUp() {
        user1 = User.builder().id(1L).username("userone").fullName("User One").role(Role.PATRON).build();
        user2 = User.builder().id(2L).username("usertwo").fullName("User Two").role(Role.PATRON).build();
        pageable = PageRequest.of(0, 10);
    }

    @Test
    void getTopBorrowedBooks_shouldReturnPageOfTopBorrowedBooks() {
        List<TopBorrowedBookResponse> topBooksList = Arrays.asList(
                new TopBorrowedBookResponse(1L, "Book A", "Author A", "ISBN-A", 10L),
                new TopBorrowedBookResponse(2L, "Book B", "Author B", "ISBN-B", 8L)
        );
        Page<TopBorrowedBookResponse> topBooksPage = new PageImpl<>(topBooksList, pageable, topBooksList.size());

        when(borrowingRecordRepository.findTopBorrowedBooks(pageable)).thenReturn(topBooksPage);

        Page<TopBorrowedBookResponse> result = reportService.getTopBorrowedBooks(pageable);

        assertNotNull(result);
        assertEquals(2, result.getTotalElements());
        assertEquals("Book A", result.getContent().get(0).getBookTitle());
        assertEquals(10L, result.getContent().get(0).getBorrowCount());
    }

    @Test
    void getTopBorrowedBooks_whenNoBooksBorrowed_shouldReturnEmptyPage() {
        Page<TopBorrowedBookResponse> emptyPage = new PageImpl<>(Collections.emptyList(), pageable, 0);
        when(borrowingRecordRepository.findTopBorrowedBooks(pageable)).thenReturn(emptyPage);

        Page<TopBorrowedBookResponse> result = reportService.getTopBorrowedBooks(pageable);

        assertNotNull(result);
        assertTrue(result.isEmpty());
        assertEquals(0, result.getTotalElements());
    }

    @Test
    void getUserActivityReport_shouldReturnPageOfUserActivities() {
        List<User> usersList = Arrays.asList(user1, user2);
        Page<User> usersPage = new PageImpl<>(usersList, pageable, usersList.size());

        when(borrowingRecordRepository.findByUserOrderByBorrowDateDesc(user1))
                .thenReturn(Arrays.asList(new BorrowingRecord(), new BorrowingRecord()));
        when(borrowingRecordRepository.findByUserAndReturnDateIsNull(user1))
                .thenReturn(Collections.singletonList(new BorrowingRecord()));

        when(borrowingRecordRepository.findByUserOrderByBorrowDateDesc(user2))
                .thenReturn(Collections.singletonList(new BorrowingRecord()));
        when(borrowingRecordRepository.findByUserAndReturnDateIsNull(user2))
                .thenReturn(Collections.emptyList());

        when(userRepository.findAll(pageable)).thenReturn(usersPage);

        Page<UserActivityReportResponse> result = reportService.getUserActivityReport(pageable);

        assertNotNull(result);
        assertEquals(2, result.getTotalElements());

        UserActivityReportResponse activity1 = result.getContent().stream()
                .filter(r -> r.getUserId().equals(user1.getId())).findFirst().orElse(null);
        assertNotNull(activity1);
        assertEquals(user1.getUsername(), activity1.getUsername());
        assertEquals(2L, activity1.getTotalBorrows());
        assertEquals(1L, activity1.getActiveBorrows());

        UserActivityReportResponse activity2 = result.getContent().stream()
                .filter(r -> r.getUserId().equals(user2.getId())).findFirst().orElse(null);
        assertNotNull(activity2);
        assertEquals(user2.getUsername(), activity2.getUsername());
        assertEquals(1L, activity2.getTotalBorrows());
        assertEquals(0L, activity2.getActiveBorrows());
    }

    @Test
    void getUserActivityReport_whenNoUsers_shouldReturnEmptyPage() {
        Page<User> emptyUserPage = new PageImpl<>(Collections.emptyList(), pageable, 0);
        when(userRepository.findAll(pageable)).thenReturn(emptyUserPage);

        Page<UserActivityReportResponse> result = reportService.getUserActivityReport(pageable);

        assertNotNull(result);
        assertTrue(result.isEmpty());
        assertEquals(0, result.getTotalElements());
    }

    @Test
    void getUserActivityReport_whenUserHasNoBorrows_shouldShowZeroCounts() {
        Page<User> singleUserPage = new PageImpl<>(Collections.singletonList(user1), pageable, 1);
        when(userRepository.findAll(pageable)).thenReturn(singleUserPage);

        when(borrowingRecordRepository.findByUserOrderByBorrowDateDesc(user1)).thenReturn(Collections.emptyList());
        when(borrowingRecordRepository.findByUserAndReturnDateIsNull(user1)).thenReturn(Collections.emptyList());

        Page<UserActivityReportResponse> result = reportService.getUserActivityReport(pageable);

        assertNotNull(result);
        assertEquals(1, result.getTotalElements());
        UserActivityReportResponse activity = result.getContent().get(0);
        assertEquals(user1.getId(), activity.getUserId());
        assertEquals(0L, activity.getTotalBorrows());
        assertEquals(0L, activity.getActiveBorrows());
    }
}
