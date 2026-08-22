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

@Service
@RequiredArgsConstructor
public class AuthService {

  private final AppUserRepository userRepository;
  private final PasswordEncoder passwordEncoder;
  private final JwtUtil jwtUtil;
  private final AuthenticationManager authenticationManager;
  private final UserDetailsService userDetailsService;

  @Transactional
  public AuthResponse register(RegisterRequest request) {
    if (userRepository.existsByUsername(request.getUsername())) {
      throw new IllegalArgumentException("Username already taken: " + request.getUsername());
    }
    if (userRepository.existsByEmail(request.getEmail())) {
      throw new IllegalArgumentException("Email already registered: " + request.getEmail());
    }

    AppUser user =
        AppUser.builder()
            .username(request.getUsername())
            .email(request.getEmail())
            .passwordHash(passwordEncoder.encode(request.getPassword()))
            .role(Role.USER)
            .build();

    userRepository.save(user);

    UserDetails userDetails = userDetailsService.loadUserByUsername(user.getUsername());
    String token = jwtUtil.generateToken(userDetails);

    return AuthResponse.builder()
        .token(token)
        .username(user.getUsername())
        .expiresIn(jwtUtil.getExpirationMs() / 1000)
        .build();
  }

  public AuthResponse login(AuthRequest request) {

    authenticationManager.authenticate(
        new UsernamePasswordAuthenticationToken(request.getUsername(), request.getPassword()));

    UserDetails userDetails = userDetailsService.loadUserByUsername(request.getUsername());
    String token = jwtUtil.generateToken(userDetails);

    return AuthResponse.builder()
        .token(token)
        .username(request.getUsername())
        .expiresIn(jwtUtil.getExpirationMs() / 1000)
        .build();
  }
}
