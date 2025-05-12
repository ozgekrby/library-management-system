package com.library.library_management_system.unit;

import com.library.library_management_system.controller.ReportController;
import com.library.library_management_system.dto.response.TopBorrowedBookResponse;
import com.library.library_management_system.dto.response.UserActivityReportResponse;
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
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReportControllerTest {

    @Mock
    private ReportService reportService;

    @InjectMocks
    private ReportController reportController;

    private Pageable pageable;
    private TopBorrowedBookResponse topBookResponse;
    private UserActivityReportResponse userActivityResponse;

    @BeforeEach
    void setUp() {
        pageable = PageRequest.of(0, 10);

        topBookResponse = new TopBorrowedBookResponse(1L, "Popular Book", "A. Writer", "111000111", 5L);
        userActivityResponse = UserActivityReportResponse.builder()
                .userId(1L)
                .username("testUser")
                .fullName("Test User FullName")
                .totalBorrows(3L)
                .activeBorrows(1L)
                .build();
    }

    @Test
    void getTopBorrowedBooks_shouldCallServiceAndReturnOk() {
        List<TopBorrowedBookResponse> topBooksList = Collections.singletonList(topBookResponse);
        Page<TopBorrowedBookResponse> topBooksPage = new PageImpl<>(topBooksList, pageable, topBooksList.size());
        when(reportService.getTopBorrowedBooks(any(Pageable.class))).thenReturn(topBooksPage);

        ResponseEntity<Page<TopBorrowedBookResponse>> responseEntity = reportController.getTopBorrowedBooks(pageable);

        assertNotNull(responseEntity);
        assertEquals(HttpStatus.OK, responseEntity.getStatusCode());
        assertNotNull(responseEntity.getBody());
        assertEquals(1, responseEntity.getBody().getTotalElements());
        assertEquals(topBookResponse.getBookTitle(), responseEntity.getBody().getContent().get(0).getBookTitle());
        verify(reportService, times(1)).getTopBorrowedBooks(pageable);
    }

    @Test
    void getTopBorrowedBooks_whenServiceReturnsEmptyPage_shouldReturnOkWithEmptyPage() {
        Page<TopBorrowedBookResponse> emptyPage = new PageImpl<>(Collections.emptyList(), pageable, 0);
        when(reportService.getTopBorrowedBooks(any(Pageable.class))).thenReturn(emptyPage);

        ResponseEntity<Page<TopBorrowedBookResponse>> responseEntity = reportController.getTopBorrowedBooks(pageable);

        assertNotNull(responseEntity);
        assertEquals(HttpStatus.OK, responseEntity.getStatusCode());
        assertNotNull(responseEntity.getBody());
        assertTrue(responseEntity.getBody().isEmpty());
        assertEquals(0, responseEntity.getBody().getTotalElements());
        verify(reportService, times(1)).getTopBorrowedBooks(pageable);
    }

    @Test
    void getUserActivityReport_shouldCallServiceAndReturnOk() {
        List<UserActivityReportResponse> userActivitiesList = Collections.singletonList(userActivityResponse);
        Page<UserActivityReportResponse> userActivityPage = new PageImpl<>(userActivitiesList, pageable, userActivitiesList.size());
        when(reportService.getUserActivityReport(any(Pageable.class))).thenReturn(userActivityPage);

        ResponseEntity<Page<UserActivityReportResponse>> responseEntity = reportController.getUserActivity(pageable);

        assertNotNull(responseEntity);
        assertEquals(HttpStatus.OK, responseEntity.getStatusCode());
        assertNotNull(responseEntity.getBody());
        assertEquals(1, responseEntity.getBody().getTotalElements());
        assertEquals(userActivityResponse.getUsername(), responseEntity.getBody().getContent().get(0).getUsername());
        verify(reportService, times(1)).getUserActivityReport(pageable);
    }

    @Test
    void getUserActivityReport_whenServiceReturnsEmptyPage_shouldReturnOkWithEmptyPage() {
        Page<UserActivityReportResponse> emptyPage = new PageImpl<>(Collections.emptyList(), pageable, 0);
        when(reportService.getUserActivityReport(any(Pageable.class))).thenReturn(emptyPage);

        ResponseEntity<Page<UserActivityReportResponse>> responseEntity = reportController.getUserActivity(pageable);

        assertNotNull(responseEntity);
        assertEquals(HttpStatus.OK, responseEntity.getStatusCode());
        assertNotNull(responseEntity.getBody());
        assertTrue(responseEntity.getBody().isEmpty());
        assertEquals(0, responseEntity.getBody().getTotalElements());
        verify(reportService, times(1)).getUserActivityReport(pageable);
    }
}
