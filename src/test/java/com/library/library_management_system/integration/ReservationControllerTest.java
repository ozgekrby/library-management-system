package com.library.library_management_system.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.library.library_management_system.dto.request.CreateReservationRequest;
import com.library.library_management_system.entity.*;
import com.library.library_management_system.repository.*;
import com.library.library_management_system.security.UserDetailsServiceImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ReservationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private BookRepository bookRepository;

    @Autowired
    private BorrowingRecordRepository borrowingRecordRepository;

    @Autowired
    private ReservationRepository reservationRepository;

    @Autowired
    private FineRepository fineRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private UserDetailsServiceImpl userDetailsServiceImpl;

    @Value("${app.reservation.hold-duration-hours:48}")
    private int reservationHoldDurationHours;

    private User patron1;
    private User patron2;
    private User librarian;
    private Book unavailableBook1;
    private Book unavailableBook2;
    private Book availableBook;

    @BeforeEach
    void setUp() {
        fineRepository.deleteAllInBatch();
        reservationRepository.deleteAllInBatch();
        borrowingRecordRepository.deleteAllInBatch();
        bookRepository.deleteAllInBatch();
        userRepository.deleteAllInBatch();

        patron1 = User.builder().username("resPatron1").password(passwordEncoder.encode("pass"))
                .email("respatron1@mail.com").fullName("Res Patron 1").role(Role.PATRON).build();
        userRepository.saveAndFlush(patron1);

        patron2 = User.builder().username("resPatron2").password(passwordEncoder.encode("pass"))
                .email("respatron2@mail.com").fullName("Res Patron 2").role(Role.PATRON).build();
        userRepository.saveAndFlush(patron2);

        librarian = User.builder().username("resLibrarian").password(passwordEncoder.encode("pass"))
                .email("reslibrarian@mail.com").fullName("Res Librarian").role(Role.LIBRARIAN).build();
        userRepository.saveAndFlush(librarian);

        unavailableBook1 = Book.builder().title("Unavailable Book One").author("Author U1").isbn("111222333")
                .publicationDate(LocalDate.now()).genre("Science").totalCopies(1).availableCopies(0).build();
        bookRepository.saveAndFlush(unavailableBook1);

        unavailableBook2 = Book.builder().title("Unavailable Book Two").author("Author U2").isbn("444555666")
                .publicationDate(LocalDate.now()).genre("History").totalCopies(2).availableCopies(0).build();
        bookRepository.saveAndFlush(unavailableBook2);

        availableBook = Book.builder().title("Available Book").author("Author A").isbn("777888999")
                .publicationDate(LocalDate.now()).genre("Fiction").totalCopies(1).availableCopies(1).build();
        bookRepository.saveAndFlush(availableBook);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private void setupSecurityContext(String username) {
        UserDetails userDetails = userDetailsServiceImpl.loadUserByUsername(username);
        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                userDetails, null, userDetails.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    @Test
    void createReservation_whenPatronAndBookUnavailable_shouldCreateReservation() throws Exception {
        setupSecurityContext("resPatron1");
        CreateReservationRequest request = new CreateReservationRequest();
        request.setBookId(unavailableBook1.getId());

        mockMvc.perform(post("/api/reservations")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.bookId").value(unavailableBook1.getId()))
                .andExpect(jsonPath("$.username").value(patron1.getUsername()))
                .andExpect(jsonPath("$.status").value(ReservationStatus.PENDING.toString()));
    }

    @Test
    void createReservation_whenBookIsAvailable_shouldReturnBadRequest() throws Exception {
        setupSecurityContext("resPatron1");
        CreateReservationRequest request = new CreateReservationRequest();
        request.setBookId(availableBook.getId());

        mockMvc.perform(post("/api/reservations")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Book '" + availableBook.getTitle() + "' is currently available. Reservation is not needed."));
    }

    @Test
    void createReservation_whenUserAlreadyHasActiveReservation_shouldReturnConflict() throws Exception {
        setupSecurityContext("resPatron1");
        CreateReservationRequest request1 = new CreateReservationRequest();
        request1.setBookId(unavailableBook1.getId());
        mockMvc.perform(post("/api/reservations").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(request1)))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/reservations")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request1)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("You already have an active reservation for the book: " + unavailableBook1.getTitle()));
    }

    @Test
    void cancelReservation_byOwner_whenReservationPending_shouldReturnNoContent() throws Exception {
        setupSecurityContext("resPatron1");
        Reservation reservation = Reservation.builder().book(unavailableBook1).user(patron1)
                .reservationDateTime(LocalDateTime.now()).status(ReservationStatus.PENDING).build();
        reservation = reservationRepository.saveAndFlush(reservation);

        mockMvc.perform(delete("/api/reservations/" + reservation.getId())
                        .with(csrf()))
                .andExpect(status().isNoContent());

        Reservation cancelledReservation = reservationRepository.findById(reservation.getId()).orElseThrow();
        assert(cancelledReservation.getStatus() == ReservationStatus.CANCELED);
    }

    @Test
    void cancelReservation_byAnotherPatron_shouldReturnForbidden() throws Exception {
        setupSecurityContext("resPatron2");
        Reservation reservation = Reservation.builder().book(unavailableBook1).user(patron1)
                .reservationDateTime(LocalDateTime.now()).status(ReservationStatus.PENDING).build();
        reservation = reservationRepository.saveAndFlush(reservation);

        mockMvc.perform(delete("/api/reservations/" + reservation.getId())
                        .with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    void cancelReservation_byLibrarian_shouldReturnNoContent() throws Exception {
        setupSecurityContext("resLibrarian");
        Reservation reservation = Reservation.builder().book(unavailableBook1).user(patron1)
                .reservationDateTime(LocalDateTime.now()).status(ReservationStatus.PENDING).build();
        reservation = reservationRepository.saveAndFlush(reservation);

        mockMvc.perform(delete("/api/reservations/" + reservation.getId())
                        .with(csrf()))
                .andExpect(status().isNoContent());
    }

    @Test
    void getMyActiveReservations_whenPatronHasReservations_shouldReturnList() throws Exception {
        setupSecurityContext("resPatron1");
        Reservation res1 = Reservation.builder().book(unavailableBook1).user(patron1).reservationDateTime(LocalDateTime.now()).status(ReservationStatus.PENDING).build();
        Reservation res2 = Reservation.builder().book(unavailableBook2).user(patron1).reservationDateTime(LocalDateTime.now().minusHours(1)).status(ReservationStatus.AVAILABLE).expirationDateTime(LocalDateTime.now().plusHours(this.reservationHoldDurationHours)).build();
        reservationRepository.saveAllAndFlush(Arrays.asList(res1, res2));

        mockMvc.perform(get("/api/reservations/me")
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].status").value(ReservationStatus.AVAILABLE.toString()))
                .andExpect(jsonPath("$[1].status").value(ReservationStatus.PENDING.toString()));
    }

    @Test
    void getReservationsForBook_whenLibrarian_shouldReturnPendingReservations() throws Exception {
        setupSecurityContext("resLibrarian");
        Reservation res1 = Reservation.builder().book(unavailableBook1).user(patron1).reservationDateTime(LocalDateTime.now()).status(ReservationStatus.PENDING).build();
        Reservation res2 = Reservation.builder().book(unavailableBook1).user(patron2).reservationDateTime(LocalDateTime.now().plusHours(1)).status(ReservationStatus.PENDING).build();
        Reservation res3 = Reservation.builder().book(unavailableBook1).user(patron1).reservationDateTime(LocalDateTime.now().minusHours(1)).status(ReservationStatus.AVAILABLE).build();
        reservationRepository.saveAllAndFlush(Arrays.asList(res1, res2, res3));

        mockMvc.perform(get("/api/reservations/book/" + unavailableBook1.getId())
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].username").value(patron1.getUsername()))
                .andExpect(jsonPath("$[1].username").value(patron2.getUsername()));
    }

    @Test
    void getAllActiveReservations_whenLibrarian_shouldReturnAllPendingAndAvailable() throws Exception {
        setupSecurityContext("resLibrarian");
        Reservation resPending = Reservation.builder().book(unavailableBook1).user(patron1).reservationDateTime(LocalDateTime.now()).status(ReservationStatus.PENDING).build();
        Reservation resAvailable = Reservation.builder().book(unavailableBook2).user(patron2).reservationDateTime(LocalDateTime.now()).status(ReservationStatus.AVAILABLE).expirationDateTime(LocalDateTime.now().plusHours(1)).build();
        Reservation resFulfilled = Reservation.builder().book(availableBook).user(patron1).reservationDateTime(LocalDateTime.now()).status(ReservationStatus.FULFILLED).build();
        reservationRepository.saveAllAndFlush(Arrays.asList(resPending, resAvailable, resFulfilled));

        mockMvc.perform(get("/api/reservations/active")
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[?(@.status == 'PENDING')]").exists())
                .andExpect(jsonPath("$[?(@.status == 'AVAILABLE')]").exists());
    }

    @Test
    void manuallyExpireReservations_whenLibrarian_shouldReturnOk() throws Exception {
        setupSecurityContext("resLibrarian");
        Reservation expiredAvailableRes = Reservation.builder()
                .book(unavailableBook1)
                .user(patron1)
                .reservationDateTime(LocalDateTime.now().minusHours(reservationHoldDurationHours + 1))
                .status(ReservationStatus.AVAILABLE)
                .expirationDateTime(LocalDateTime.now().minusHours(1))
                .build();
        reservationRepository.saveAndFlush(expiredAvailableRes);

        mockMvc.perform(post("/api/reservations/expire-check")
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(content().string("Expired reservations check completed."));

        Reservation checkedReservation = reservationRepository.findById(expiredAvailableRes.getId()).orElseThrow();
        assertEquals(ReservationStatus.EXPIRED, checkedReservation.getStatus());
    }
}
