package com.library.library_management_system.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserActivityReportResponse {
    private Long userId;
    private String username;
    private String fullName;
    private Long totalBorrows;
    private Long activeBorrows;
}
