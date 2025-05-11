package com.library.library_management_system.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CreateReservationRequest {
    @NotNull(message = "Book ID cannot be null for reservation")
    private Long bookId;
}
