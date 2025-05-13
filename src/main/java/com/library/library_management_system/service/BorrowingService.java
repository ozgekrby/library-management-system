package com.library.library_management_system.service;

import com.library.library_management_system.dto.request.BorrowBookRequest;
import com.library.library_management_system.dto.response.BorrowingRecordResponse;
import com.library.library_management_system.dto.response.FineResponse;
import com.library.library_management_system.entity.Book;
import com.library.library_management_system.entity.BorrowingRecord;
import com.library.library_management_system.entity.User;
import com.library.library_management_system.exception.BookUnavailableException;
import com.library.library_management_system.exception.ResourceNotFoundException;
import com.library.library_management_system.repository.BookRepository;
import com.library.library_management_system.repository.BorrowingRecordRepository;
import com.library.library_management_system.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class BorrowingService {

    private final BorrowingRecordRepository borrowingRecordRepository;
    private final BookRepository bookRepository;
    private final UserRepository userRepository;
    private final FineService fineService;

    //Allows a user to borrow a book.
    @Transactional
    public BorrowingRecordResponse borrowBook(BorrowBookRequest borrowRequest, User currentUser) {
        Book bookToBorrow = bookRepository.findById(borrowRequest.getBookId())
                .orElseThrow(() -> new ResourceNotFoundException("Book not found with id: " + borrowRequest.getBookId()));

        if (bookToBorrow.getAvailableCopies() <= 0) {
            throw new BookUnavailableException("Book '" + bookToBorrow.getTitle() + "' is not available for borrowing.");
        }

        if (borrowingRecordRepository.existsByBookAndUserAndReturnDateIsNull(bookToBorrow, currentUser)) {
            throw new IllegalStateException("User has already borrowed this book and not returned it yet.");
        }

        bookToBorrow.setAvailableCopies(bookToBorrow.getAvailableCopies() - 1);
        bookRepository.save(bookToBorrow);

        BorrowingRecord borrowingRecord = BorrowingRecord.builder()
                .book(bookToBorrow)
                .user(currentUser)
                .borrowDate(LocalDate.now())
                .dueDate(borrowRequest.getDueDate() != null ? borrowRequest.getDueDate() : LocalDate.now().plusWeeks(2))
                .build();

        BorrowingRecord savedRecord = borrowingRecordRepository.save(borrowingRecord);
        log.info("Book '{}' borrowed by user '{}'", bookToBorrow.getTitle(), currentUser.getUsername());
        return mapToBorrowingRecordResponse(savedRecord);
    }

    //Allows a user to return a borrowed book.
    @Transactional
    public BorrowingRecordResponse returnBook(Long borrowingRecordId, User currentUser) {
        BorrowingRecord recordToReturn = borrowingRecordRepository.findByIdAndUserAndReturnDateIsNull(borrowingRecordId, currentUser)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Active borrowing record not found with id: " + borrowingRecordId + " for user " + currentUser.getUsername()
                ));
        return processReturn(recordToReturn);
    }
    //Allows a librarian to return a book.
    @Transactional
    public BorrowingRecordResponse returnBookByLibrarian(Long borrowingRecordId) {
        BorrowingRecord recordToReturn = borrowingRecordRepository.findByIdAndReturnDateIsNull(borrowingRecordId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Active borrowing record not found with id: " + borrowingRecordId
                ));
        return processReturn(recordToReturn);
    }

    //Handles the common logic for returning a book.
    @Transactional
    private BorrowingRecordResponse processReturn(BorrowingRecord recordToReturn) {
        if (recordToReturn.getReturnDate() != null) {
            log.warn("Book for borrowing ID {} was already returned on {}. Ignoring duplicate return attempt.",
                    recordToReturn.getId(), recordToReturn.getReturnDate());
            return mapToBorrowingRecordResponse(recordToReturn);
        }

        recordToReturn.setReturnDate(LocalDate.now());
        BorrowingRecord updatedRecord = borrowingRecordRepository.save(recordToReturn);


        Book returnedBook = updatedRecord.getBook();
        returnedBook.setAvailableCopies(returnedBook.getAvailableCopies() + 1);
        bookRepository.save(returnedBook);

        log.info("Book '{}' returned by user '{}'. Borrowing ID: {}",
                returnedBook.getTitle(), updatedRecord.getUser().getUsername(), updatedRecord.getId());
        FineResponse fineResponse = fineService.createOrUpdateFineForOverdueBook(updatedRecord);
        if (fineResponse != null) {
            log.info("Fine generated/updated for borrowing ID {}: Amount {}", updatedRecord.getId(), fineResponse.getAmount());
        } else {
            log.info("No fine generated for borrowing ID {} (returned on time or within grace period).", updatedRecord.getId());
        }

        return mapToBorrowingRecordResponse(updatedRecord);
    }

    //Retrieves the borrowing history for a specific user.
    @Transactional(readOnly = true)
    public List<BorrowingRecordResponse> getUserBorrowingHistory(User user) {
        return borrowingRecordRepository.findByUserOrderByBorrowDateDesc(user).stream()
                .map(this::mapToBorrowingRecordResponse)
                .collect(Collectors.toList());
    }

    //Retrieves the borrowing history for a user (by librarian).
    @Transactional(readOnly = true)
    public List<BorrowingRecordResponse> getBorrowingHistoryForUserByLibrarian(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + userId));
        return getUserBorrowingHistory(user);
    }

    //Retrieves all borrowing history records.
    @Transactional(readOnly = true)
    public List<BorrowingRecordResponse> getAllBorrowingHistory() {
        return borrowingRecordRepository.findAll(Sort.by(Sort.Direction.DESC, "borrowDate")).stream()
                .map(this::mapToBorrowingRecordResponse)
                .collect(Collectors.toList());
    }

    //Retrieves all overdue borrowing records (books not returned in time).
    @Transactional(readOnly = true)
    public List<BorrowingRecordResponse> getOverdueBooks() {
        return borrowingRecordRepository.findByReturnDateIsNullAndDueDateBefore(LocalDate.now()).stream()
                .map(this::mapToBorrowingRecordResponse)
                .collect(Collectors.toList());
    }

    //Maps a BorrowingRecord entity to its response DTO.
    private BorrowingRecordResponse mapToBorrowingRecordResponse(BorrowingRecord record) {
        return BorrowingRecordResponse.builder()
                .id(record.getId())
                .bookId(record.getBook().getId())
                .bookTitle(record.getBook().getTitle())
                .userId(record.getUser().getId())
                .username(record.getUser().getUsername())
                .borrowDate(record.getBorrowDate())
                .dueDate(record.getDueDate())
                .returnDate(record.getReturnDate())
                .build();
    }
}
