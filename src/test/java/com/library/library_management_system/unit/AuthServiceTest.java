package com.library.library_management_system.unit;

import com.library.library_management_system.dto.request.LoginRequest;
import com.library.library_management_system.dto.request.RegisterUserRequest;
import com.library.library_management_system.dto.response.AuthResponse;
import com.library.library_management_system.dto.response.UserResponse;
import com.library.library_management_system.entity.Role;
import com.library.library_management_system.entity.User;
import com.library.library_management_system.exception.UserAlreadyExistsException;
import com.library.library_management_system.repository.UserRepository;
import com.library.library_management_system.security.JwtTokenProvider;
import com.library.library_management_system.service.AuthService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@ActiveProfiles("test")
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtTokenProvider jwtTokenProvider;

    @Mock
    private AuthenticationManager authenticationManager;

    @Mock
    private Authentication authentication;


    @InjectMocks
    private AuthService authService;

    private RegisterUserRequest registerUserRequestPatron;
    private RegisterUserRequest registerUserRequestLibrarian;
    private User registeredUser;
    private LoginRequest loginRequest;
    private User userForLogin;

    @BeforeEach
    void setUp() {
        registerUserRequestPatron = new RegisterUserRequest();
        registerUserRequestPatron.setUsername("testpatron");
        registerUserRequestPatron.setPassword("password123");
        registerUserRequestPatron.setEmail("testpatron@example.com");
        registerUserRequestPatron.setFullName("Test Patron FullName");
        registerUserRequestPatron.setRole(Role.PATRON);

        registerUserRequestLibrarian = new RegisterUserRequest();
        registerUserRequestLibrarian.setUsername("testlibrarian");
        registerUserRequestLibrarian.setPassword("password456");
        registerUserRequestLibrarian.setEmail("testlibrarian@example.com");
        registerUserRequestLibrarian.setFullName("Test Librarian FullName");
        registerUserRequestLibrarian.setRole(Role.LIBRARIAN);

        registeredUser = User.builder()
                .id(1L)
                .username("testpatron")
                .password("hashedPassword")
                .email("testpatron@example.com")
                .fullName("Test Patron FullName")
                .role(Role.PATRON)
                .build();

        loginRequest = new LoginRequest();
        loginRequest.setUsername("loginuser");
        loginRequest.setPassword("password123");

        userForLogin = User.builder()
                .id(2L)
                .username("loginuser")
                .password("hashedLoginPassword")
                .email("loginuser@example.com")
                .fullName("Login User FullName")
                .role(Role.PATRON)
                .build();
    }

    @Test
    void registerUser_whenPatronDataIsValid_shouldRegisterAndReturnUserResponse() {
        when(userRepository.existsByUsername(registerUserRequestPatron.getUsername())).thenReturn(false);
        when(userRepository.existsByEmail(registerUserRequestPatron.getEmail())).thenReturn(false);
        when(passwordEncoder.encode(registerUserRequestPatron.getPassword())).thenReturn("hashedPassword");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User userToSave = invocation.getArgument(0);
            return User.builder()
                    .id(1L)
                    .username(userToSave.getUsername())
                    .password(userToSave.getPassword())
                    .email(userToSave.getEmail())
                    .fullName(userToSave.getFullName())
                    .role(userToSave.getRole())
                    .build();
        });


        UserResponse response = authService.registerUser(registerUserRequestPatron);

        assertNotNull(response);
        assertEquals(registerUserRequestPatron.getUsername(), response.getUsername());
        assertEquals(registerUserRequestPatron.getEmail(), response.getEmail());
        assertEquals(Role.PATRON, response.getRole());
        assertNotNull(response.getId());

        verify(userRepository, times(1)).existsByUsername(registerUserRequestPatron.getUsername());
        verify(userRepository, times(1)).existsByEmail(registerUserRequestPatron.getEmail());
        verify(passwordEncoder, times(1)).encode(registerUserRequestPatron.getPassword());
        verify(userRepository, times(1)).save(any(User.class));
    }

    @Test
    void registerUser_whenLibrarianDataIsValid_shouldRegisterAndReturnUserResponse() {
        when(userRepository.existsByUsername(registerUserRequestLibrarian.getUsername())).thenReturn(false);
        when(userRepository.existsByEmail(registerUserRequestLibrarian.getEmail())).thenReturn(false);
        when(passwordEncoder.encode(registerUserRequestLibrarian.getPassword())).thenReturn("hashedPasswordLib");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User userToSave = invocation.getArgument(0);
            return User.builder()
                    .id(2L)
                    .username(userToSave.getUsername())
                    .password(userToSave.getPassword())
                    .email(userToSave.getEmail())
                    .fullName(userToSave.getFullName())
                    .role(userToSave.getRole())
                    .build();
        });

        UserResponse response = authService.registerUser(registerUserRequestLibrarian);

        assertNotNull(response);
        assertEquals(registerUserRequestLibrarian.getUsername(), response.getUsername());
        assertEquals(Role.LIBRARIAN, response.getRole());
        verify(userRepository, times(1)).save(any(User.class));
    }

    @Test
    void registerUser_whenUsernameExists_shouldThrowUserAlreadyExistsException() {
        when(userRepository.existsByUsername(registerUserRequestPatron.getUsername())).thenReturn(true);

        UserAlreadyExistsException exception = assertThrows(UserAlreadyExistsException.class,
                () -> authService.registerUser(registerUserRequestPatron));
        assertEquals("Username is already taken: " + registerUserRequestPatron.getUsername(), exception.getMessage());

        verify(userRepository, times(1)).existsByUsername(registerUserRequestPatron.getUsername());
        verify(userRepository, never()).existsByEmail(anyString());
        verify(passwordEncoder, never()).encode(anyString());
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void registerUser_whenEmailExists_shouldThrowUserAlreadyExistsException() {
        when(userRepository.existsByUsername(registerUserRequestPatron.getUsername())).thenReturn(false);
        when(userRepository.existsByEmail(registerUserRequestPatron.getEmail())).thenReturn(true);

        UserAlreadyExistsException exception = assertThrows(UserAlreadyExistsException.class,
                () -> authService.registerUser(registerUserRequestPatron));
        assertEquals("Email is already registered: " + registerUserRequestPatron.getEmail(), exception.getMessage());

        verify(userRepository, times(1)).existsByUsername(registerUserRequestPatron.getUsername());
        verify(userRepository, times(1)).existsByEmail(registerUserRequestPatron.getEmail());
        verify(passwordEncoder, never()).encode(anyString());
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void loginUser_whenCredentialsAreValid_shouldReturnAuthResponseWithToken() {
        when(authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(loginRequest.getUsername(), loginRequest.getPassword())))
                .thenReturn(authentication);
        when(authentication.getPrincipal()).thenReturn(userForLogin);
        String expectedToken = "mocked.jwt.token";
        when(jwtTokenProvider.generateToken(authentication)).thenReturn(expectedToken);

        AuthResponse response = authService.loginUser(loginRequest);

        assertNotNull(response);
        assertEquals(expectedToken, response.getAccessToken());
        assertEquals("Bearer", response.getTokenType());
        assertEquals(userForLogin.getId(), response.getUserId());
        assertEquals(userForLogin.getUsername(), response.getUsername());
        assertTrue(response.getRoles().contains("PATRON"));

        verify(authenticationManager, times(1)).authenticate(any(UsernamePasswordAuthenticationToken.class));
        verify(jwtTokenProvider, times(1)).generateToken(authentication);
    }

    @Test
    void loginUser_whenCredentialsAreInvalid_shouldThrowBadCredentialsException() {
        when(authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(loginRequest.getUsername(), "wrongPassword")))
                .thenThrow(new BadCredentialsException("Bad credentials"));

        assertThrows(BadCredentialsException.class, () -> {
            authService.loginUser(new LoginRequest() {{
                setUsername(loginRequest.getUsername());
                setPassword("wrongPassword");
            }});
        });
        verify(jwtTokenProvider, never()).generateToken(any(Authentication.class));
    }
}
