package com.library.library_management_system.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;
import java.time.LocalDate;
import java.util.Set;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "books", uniqueConstraints = {
        @UniqueConstraint(columnNames = "isbn")
})
public class Book {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    @Column(nullable = false)
    private String title;

    @NotBlank
    @Column(nullable = false)
    private String author;

    @NotBlank
    @Column(nullable = false, unique = true)
    private String isbn;

    @NotNull
    @PastOrPresent
    private LocalDate publicationDate;

    @NotBlank
    private String genre;

    @NotNull
    @Min(0)
    @Column(columnDefinition = "integer default 0")
    private Integer totalCopies;

    @NotNull
    @Min(0)
    @Column(columnDefinition = "integer default 0")
    private Integer availableCopies;

    @OneToMany(mappedBy = "book", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<BorrowingRecord> borrowingRecords;

    @PrePersist
    @PreUpdate
    private void ensureAvailableCopiesNotGreaterThanTotal() {
        if (availableCopies == null) availableCopies = 0;
        if (totalCopies == null) totalCopies = 0;
        if (availableCopies > totalCopies) {
            throw new IllegalStateException("Available copies cannot be greater than total copies.");
        }
        if (availableCopies < 0) {
            throw new IllegalStateException("Available copies cannot be negative.");
        }
    }
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Book book = (Book) o;
        return id != null && id.equals(book.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}

