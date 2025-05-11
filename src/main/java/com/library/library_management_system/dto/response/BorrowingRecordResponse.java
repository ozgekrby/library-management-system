package com.library.library_management_system.dto.response;

import lombok.Builder;
import lombok.Data;
import java.time.LocalDate;

@Data
@Builder
public class BorrowingRecordResponse {
    private Long id;
    private Long bookId;
    private String bookTitle;
    private Long userId;
    private String username;
    private LocalDate borrowDate;
    private LocalDate dueDate;
    private LocalDate returnDate;
}
