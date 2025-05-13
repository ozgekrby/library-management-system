package com.library.library_management_system.service;

import com.library.library_management_system.dto.request.CreateBookRequest;
import com.library.library_management_system.dto.request.UpdateBookRequest;
import com.library.library_management_system.dto.response.BookResponse;
import com.library.library_management_system.entity.Book;
import com.library.library_management_system.exception.DuplicateIsbnException;
import com.library.library_management_system.exception.ResourceNotFoundException;
import com.library.library_management_system.repository.BookRepository;
import com.library.library_management_system.repository.BorrowingRecordRepository;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
@Slf4j
@Service
@RequiredArgsConstructor
public class BookService {

    private final BookRepository bookRepository;
    private final BorrowingRecordRepository borrowingRecordRepository;

    @Transactional
    public BookResponse addBook(CreateBookRequest request) {
        if (bookRepository.existsByIsbn(request.getIsbn())) {
            throw new DuplicateIsbnException("Book with ISBN " + request.getIsbn() + " already exists.");
        }

        Book book = Book.builder()
                .title(request.getTitle())
                .author(request.getAuthor())
                .isbn(request.getIsbn())
                .publicationDate(request.getPublicationDate())
                .genre(request.getGenre())
                .totalCopies(request.getTotalCopies())
                .availableCopies(request.getTotalCopies())
                .build();
        Book savedBook = bookRepository.save(book);
        log.info("Book added: {}", savedBook.getTitle());
        return mapToBookResponse(savedBook);
    }

    @Transactional(readOnly = true)
    public BookResponse getBookById(Long id) {
        Book book = bookRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Book not found with id: " + id));
        return mapToBookResponse(book);
    }

    @Transactional(readOnly = true)
    public Page<BookResponse> searchBooks(String title, String author, String isbn, String genre, Pageable pageable) {
        Specification<Book> spec = (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (StringUtils.hasText(title)) {
                predicates.add(criteriaBuilder.like(criteriaBuilder.lower(root.get("title")), "%" + title.toLowerCase() + "%"));
            }
            if (StringUtils.hasText(author)) {
                predicates.add(criteriaBuilder.like(criteriaBuilder.lower(root.get("author")), "%" + author.toLowerCase() + "%"));
            }
            if (StringUtils.hasText(isbn)) {
                predicates.add(criteriaBuilder.equal(root.get("isbn"), isbn));
            }
            if (StringUtils.hasText(genre)) {
                predicates.add(criteriaBuilder.like(criteriaBuilder.lower(root.get("genre")), "%" + genre.toLowerCase() + "%"));
            }
            return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        };
        return bookRepository.findAll(spec, pageable).map(this::mapToBookResponse);
    }


    @Transactional
    public BookResponse updateBook(Long id, UpdateBookRequest request) {
        Book book = bookRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Book not found with id: " + id));

        if (StringUtils.hasText(request.getIsbn()) && !book.getIsbn().equals(request.getIsbn()) && bookRepository.existsByIsbn(request.getIsbn())) {
            throw new DuplicateIsbnException("Another book with ISBN " + request.getIsbn() + " already exists.");
        }

        if (StringUtils.hasText(request.getTitle())) book.setTitle(request.getTitle());
        if (StringUtils.hasText(request.getAuthor())) book.setAuthor(request.getAuthor());
        if (StringUtils.hasText(request.getIsbn())) book.setIsbn(request.getIsbn());
        if (request.getPublicationDate() != null) book.setPublicationDate(request.getPublicationDate());
        if (StringUtils.hasText(request.getGenre())) book.setGenre(request.getGenre());

        if (request.getTotalCopies() != null) {
            if (request.getTotalCopies() < 0) {
                throw new IllegalArgumentException("Total copies cannot be negative.");
            }

            int currentBorrowedCopies = book.getTotalCopies() - book.getAvailableCopies();
            if (request.getTotalCopies() < currentBorrowedCopies) {
                throw new IllegalStateException("Total copies cannot be less than the number of currently borrowed copies (" + currentBorrowedCopies + ").");
            }
            book.setTotalCopies(request.getTotalCopies());
            if (book.getAvailableCopies() > book.getTotalCopies()) {
                book.setAvailableCopies(book.getTotalCopies());
            }
        }
        Book updatedBook = bookRepository.save(book);
        log.info("Book updated: {}", updatedBook.getTitle());
        return mapToBookResponse(updatedBook);
    }

    @Transactional
    public void deleteBook(Long id) {
        Book book = bookRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Book not found with id: " + id));

        if (borrowingRecordRepository.existsByBookAndReturnDateIsNull(book)) {
            throw new IllegalStateException("Cannot delete book '" + book.getTitle() + "'. It is currently borrowed by one or more users.");
        }
        bookRepository.delete(book);
        log.info("Book deleted with id: {}", id);
    }

    public Flux<BookResponse> searchBooksReactive(String title, String author, String isbn, String genre) {
        Specification<Book> spec = createBookSpecification(title, author, isbn, genre);

        return Flux.defer(() -> Flux.fromIterable(bookRepository.findAll(spec)))
                .subscribeOn(Schedulers.boundedElastic())
                .map(this::mapToBookResponse);
    }

    public Mono<BookResponse> getBookByIdReactive(Long id) {
        return Mono.fromCallable(() -> bookRepository.findById(id))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(optionalBook -> optionalBook.map(this::mapToBookResponse).map(Mono::just).orElseGet(Mono::empty));
    }

    private Specification<Book> createBookSpecification(String title, String author, String isbn, String genre) {
        return (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (StringUtils.hasText(title)) {
                predicates.add(criteriaBuilder.like(criteriaBuilder.lower(root.get("title")), "%" + title.toLowerCase() + "%"));
            }
            if (StringUtils.hasText(author)) {
                predicates.add(criteriaBuilder.like(criteriaBuilder.lower(root.get("author")), "%" + author.toLowerCase() + "%"));
            }
            if (StringUtils.hasText(isbn)) {
                predicates.add(criteriaBuilder.equal(root.get("isbn"), isbn));
            }
            if (StringUtils.hasText(genre)) {
                predicates.add(criteriaBuilder.like(criteriaBuilder.lower(root.get("genre")), "%" + genre.toLowerCase() + "%"));
            }
            return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        };
    }

    private BookResponse mapToBookResponse(Book book) {
        return BookResponse.builder()
                .id(book.getId())
                .title(book.getTitle())
                .author(book.getAuthor())
                .isbn(book.getIsbn())
                .publicationDate(book.getPublicationDate())
                .genre(book.getGenre())
                .totalCopies(book.getTotalCopies())
                .availableCopies(book.getAvailableCopies())
                .build();
    }
}
