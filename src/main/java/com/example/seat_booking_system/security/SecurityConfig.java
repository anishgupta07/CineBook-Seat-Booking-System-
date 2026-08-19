package com.example.seat_booking_system.security;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

// Step 2: Spring Security config — defines public vs protected routes, installs JWT filter, sets stateless sessions.
// CSRF disabled (irrelevant for JWT/Authorization-header auth).
// BCrypt password encoder (cost factor 10 ≈ 10ms — slow enough to deter brute force).
@Configuration
@EnableWebSecurity
@EnableMethodSecurity  // Enables @PreAuthorize, @PostAuthorize on controller methods
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthFilter jwtAuthFilter;
    private final UserDetailsServiceImpl userDetailsService;

    // Step 2.1: Main security filter chain — defines route permissions and registers JWT filter.
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                // Step 2.2: Disable CSRF — not needed for stateless REST (no session cookies).
                .csrf(AbstractHttpConfigurer::disable)

                // Step 2.3: STATELESS — no HttpSession created; every request must carry a JWT.
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                // Step 2.4: Authorization rules — first match wins.
                .authorizeHttpRequests(auth -> auth
                        // Public: show browsing (no login required).
                        .requestMatchers(HttpMethod.GET, "/api/shows/**").permitAll()
                        // Public: auth endpoints.
                        .requestMatchers("/api/auth/**").permitAll()
                        // Public: Docker health check.
                        .requestMatchers("/actuator/health").permitAll()
                        // Everything else requires a valid JWT.
                        .anyRequest().authenticated()
                )

                // Step 2.5: Register JWT filter BEFORE Spring's username/password filter.
                .authenticationProvider(authenticationProvider())
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    // Step 2.6: DAO-based provider — loads user from DB and checks BCrypt hash.
    @Bean
    public AuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder());
        return provider;
    }

    // Step 2.7: BCrypt encoder — cost factor 10 is the default (~10ms on modern hardware).
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    // Step 2.8: Exposes AuthenticationManager as a bean so AuthService can call it.
    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config)
            throws Exception {
        return config.getAuthenticationManager();
    }
}
