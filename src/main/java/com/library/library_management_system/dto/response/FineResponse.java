package com.library.library_management_system.dto.response;

import com.library.library_management_system.entity.FineStatus;
import lombok.Builder;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@Builder
public class FineResponse {
    private Long id;
    private Long borrowingRecordId;
    private Long userId;
    private String username;
    private String bookTitle;
    private BigDecimal amount;
    private LocalDate issueDate;
    private LocalDate paidDate;
    private FineStatus status;
}
