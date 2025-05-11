package com.library.library_management_system.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TopBorrowedBookResponse {
    private Long bookId;
    private String bookTitle;
    private String author;
    private String isbn;
    private Long borrowCount;
}
