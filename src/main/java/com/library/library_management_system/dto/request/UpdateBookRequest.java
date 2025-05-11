package com.library.library_management_system.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Pattern;
import lombok.Data;
import java.time.LocalDate;

@Data
public class UpdateBookRequest {

    private String title;
    private String author;

    @Pattern(regexp = "^(?:ISBN(?:-1[03])?:? )?(?=[0-9X]{10}$|(?=(?:[0-9]+[- ]){3})[- 0-9X]{13}$|97[89][0-9]{10}$|(?=(?:[0-9]+[- ]){4})[- 0-9]{17}$)(?:97[89][- ]?)?[0-9]{1,5}[- ]?[0-9]+[- ]?[0-9]+[- ]?[0-9X]$", message = "Invalid ISBN format if provided")
    private String isbn;

    @PastOrPresent(message = "Publication date must be in the past or present if provided")
    private LocalDate publicationDate;

    private String genre;

    @Min(value = 0, message = "Total copies must be non-negative if provided")
    private Integer totalCopies;
}