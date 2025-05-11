package com.library.library_management_system.dto.response;

import com.library.library_management_system.entity.ReservationStatus;
import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Builder
public class ReservationResponse {
    private Long id;
    private Long bookId;
    private String bookTitle;
    private Long userId;
    private String username;
    private LocalDateTime reservationDateTime;
    private ReservationStatus status;
    private LocalDateTime expirationDateTime;
}
