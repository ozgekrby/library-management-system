package com.library.library_management_system.config;

import com.library.library_management_system.repository.UserRepository;
import com.library.library_management_system.security.JwtAuthenticationFilter;
import com.library.library_management_system.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ReactiveAuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.SecurityWebFiltersOrder;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.ReactiveUserDetailsService;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.authentication.AuthenticationWebFilter;
import org.springframework.security.web.server.authentication.ServerAuthenticationConverter;
import org.springframework.security.web.server.context.NoOpServerSecurityContextRepository;
import org.springframework.security.web.server.util.matcher.ServerWebExchangeMatchers;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Configuration
@EnableWebSecurity
@EnableWebFluxSecurity
@EnableMethodSecurity(prePostEnabled = true)
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final JwtTokenProvider jwtTokenProvider;
    private final UserRepository userRepository;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration authenticationConfiguration) throws Exception {
        return authenticationConfiguration.getAuthenticationManager();
    }

    @Bean
    @Order(1)
    public SecurityFilterChain apiSecurityFilterChain(HttpSecurity http) throws Exception {
        http
                .securityMatcher(new OrRequestMatcher(
                        AntPathRequestMatcher.antMatcher("/api/auth/**"),
                        AntPathRequestMatcher.antMatcher("/api/books/**"),
                        AntPathRequestMatcher.antMatcher("/api/users/**"),
                        AntPathRequestMatcher.antMatcher("/api/borrow/**"),
                        AntPathRequestMatcher.antMatcher("/api/fines/**"),
                        AntPathRequestMatcher.antMatcher("/api/reports/**"),
                        AntPathRequestMatcher.antMatcher("/api/reservations/**"),
                        AntPathRequestMatcher.antMatcher("/swagger-ui/**"),
                        AntPathRequestMatcher.antMatcher("/v3/api-docs/**"),
                        AntPathRequestMatcher.antMatcher("/swagger-ui.html")
                ))
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/auth/**").permitAll()
                        .requestMatchers("/swagger-ui/**", "/v3/api-docs/**", "/swagger-ui.html").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/books", "/api/books/**").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/books").hasRole("LIBRARIAN")
                        .requestMatchers(HttpMethod.PUT, "/api/books/**").hasRole("LIBRARIAN")
                        .requestMatchers(HttpMethod.DELETE, "/api/books/**").hasRole("LIBRARIAN")
                        .requestMatchers("/api/users/**").hasRole("LIBRARIAN")
                        .requestMatchers(HttpMethod.POST, "/api/borrow").hasAnyRole("PATRON", "LIBRARIAN")
                        .requestMatchers(HttpMethod.PUT, "/api/borrow/return/**").hasAnyRole("PATRON", "LIBRARIAN")
                        .requestMatchers(HttpMethod.GET, "/api/borrow/history/me").authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/borrow/history/user/**").hasRole("LIBRARIAN")
                        .requestMatchers(HttpMethod.GET, "/api/borrow/history/all").hasRole("LIBRARIAN")
                        .requestMatchers(HttpMethod.GET, "/api/borrow/overdue").hasRole("LIBRARIAN")
                        .requestMatchers(HttpMethod.GET, "/api/fines/me").authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/fines/user/{userId}").hasRole("LIBRARIAN")
                        .requestMatchers(HttpMethod.GET, "/api/fines/pending").hasRole("LIBRARIAN")
                        .requestMatchers(HttpMethod.GET, "/api/fines/all").hasRole("LIBRARIAN")
                        .requestMatchers(HttpMethod.PUT, "/api/fines/{fineId}/pay").hasRole("LIBRARIAN")
                        .requestMatchers(HttpMethod.PUT, "/api/fines/{fineId}/waive").hasRole("LIBRARIAN")
                        .requestMatchers("/api/reports/**").hasRole("LIBRARIAN")
                        .requestMatchers(HttpMethod.POST, "/api/reservations").hasRole("PATRON")
                        .requestMatchers(HttpMethod.DELETE, "/api/reservations/**").authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/reservations/me").hasRole("PATRON")
                        .requestMatchers(HttpMethod.GET, "/api/reservations/book/**").hasRole("LIBRARIAN")
                        .requestMatchers(HttpMethod.GET, "/api/reservations/active").hasRole("LIBRARIAN")
                        .requestMatchers(HttpMethod.POST, "/api/reservations/expire-check").hasRole("LIBRARIAN")

                        .anyRequest().authenticated()
                );

        http.addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
    @Bean
    public ReactiveUserDetailsService reactiveUserDetailsService() {
        return username -> Mono.fromCallable(() -> userRepository.findByUsername(username))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(optionalUser -> optionalUser.map(Mono::just).orElseGet(Mono::empty))
                .cast(UserDetails.class);
    }

    @Bean
    public ReactiveAuthenticationManager reactiveJwtAuthenticationManager(ReactiveUserDetailsService userDetailsService) {
        return authentication -> Mono.just(authentication)
                .filter(auth -> auth.getCredentials() instanceof String)
                .map(auth -> (String) auth.getCredentials())
                .filter(jwtTokenProvider::validateToken)
                .map(jwtTokenProvider::getUsernameFromJWT)
                .flatMap(username -> userDetailsService.findByUsername(username)
                        .map(userDetails -> new UsernamePasswordAuthenticationToken(
                                userDetails,
                                authentication.getCredentials(),
                                userDetails.getAuthorities()
                        ))
                )
                .cast(Authentication.class);
    }

    @Bean
    public ServerAuthenticationConverter jwtAuthenticationConverter() {
        return exchange -> Mono.justOrEmpty(exchange.getRequest().getHeaders().getFirst("Authorization"))
                .filter(header -> header.startsWith("Bearer "))
                .map(header -> header.substring(7))
                .map(token -> new UsernamePasswordAuthenticationToken(token, token));
    }

    @Bean
    @Order(0)
    public SecurityWebFilterChain reactiveSecurityWebFilterChain(ServerHttpSecurity http,
                                                                 ReactiveAuthenticationManager reactiveAuthenticationManager,
                                                                 ServerAuthenticationConverter jwtAuthenticationConverter) {

        AuthenticationWebFilter authenticationWebFilter = new AuthenticationWebFilter(reactiveAuthenticationManager);
        authenticationWebFilter.setServerAuthenticationConverter(jwtAuthenticationConverter);

        http
                .securityMatcher(ServerWebExchangeMatchers.pathMatchers("/api/v1/reactive/**"))
                .authorizeExchange(exchanges -> exchanges
                        .pathMatchers(HttpMethod.GET, "/api/v1/reactive/books/search").permitAll()
                        .pathMatchers(HttpMethod.GET, "/api/v1/reactive/books/**").permitAll()
                        .anyExchange().authenticated()
                )
                .addFilterAt(authenticationWebFilter, SecurityWebFiltersOrder.AUTHENTICATION)
                .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
                .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .logout(ServerHttpSecurity.LogoutSpec::disable)
                .securityContextRepository(NoOpServerSecurityContextRepository.getInstance());

        return http.build();

    }
}