package com.library.library_management_system.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class PayFineRequest {
    @NotNull(message = "Fine ID cannot be null")
    private Long fineId;
}