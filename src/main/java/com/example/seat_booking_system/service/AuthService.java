package com.example.seat_booking_system.service;

import com.example.seat_booking_system.dto.AuthRequest;
import com.example.seat_booking_system.dto.AuthResponse;
import com.example.seat_booking_system.dto.RegisterRequest;
import com.example.seat_booking_system.entity.AppUser;
import com.example.seat_booking_system.entity.Role;
import com.example.seat_booking_system.repository.AppUserRepository;
import com.example.seat_booking_system.security.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// Step 2: Handles user registration and login — returns a JWT in both cases.
// Identity is always the JWT; the password hash is never returned in any response.
@Service
@RequiredArgsConstructor
public class AuthService {

    private final AppUserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final AuthenticationManager authenticationManager;
    private final UserDetailsService userDetailsService;

    // Step 2.1: Register — validate uniqueness → hash password → save user → issue JWT.
    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new IllegalArgumentException("Username already taken: " + request.getUsername());
        }
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new IllegalArgumentException("Email already registered: " + request.getEmail());
        }

        AppUser user = AppUser.builder()
                .username(request.getUsername())
                .email(request.getEmail())
                // BCrypt hash — never store plain text.
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .role(Role.USER)
                .build();

        userRepository.save(user);

        // Issue JWT immediately so the user is logged in right after registration.
        UserDetails userDetails = userDetailsService.loadUserByUsername(user.getUsername());
        String token = jwtUtil.generateToken(userDetails);

        return AuthResponse.builder()
                .token(token)
                .username(user.getUsername())
                .expiresIn(jwtUtil.getExpirationMs() / 1000)
                .build();
    }

    // Step 2.2: Login — delegates to AuthenticationManager (loads user, compares BCrypt hash) → issue JWT.
    //           Throws BadCredentialsException if credentials are wrong.
    public AuthResponse login(AuthRequest request) {
        // This line throws if credentials are wrong — Spring Security handles the error.
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getUsername(), request.getPassword())
        );

        UserDetails userDetails = userDetailsService.loadUserByUsername(request.getUsername());
        String token = jwtUtil.generateToken(userDetails);

        return AuthResponse.builder()
                .token(token)
                .username(request.getUsername())
                .expiresIn(jwtUtil.getExpirationMs() / 1000)
                .build();
    }
}
