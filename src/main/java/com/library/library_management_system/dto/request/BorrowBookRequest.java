package com.library.library_management_system.dto.request;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.time.LocalDate;

@Data
public class BorrowBookRequest {
    @NotNull(message = "Book ID cannot be null")
    private Long bookId;

    @Future(message = "Due date must be in the future")
    private LocalDate dueDate;
}
