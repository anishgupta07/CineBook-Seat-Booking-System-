package com.example.seat_booking_system.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Response returned after a successful login. Contains the JWT token. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuthResponse {

    // JWT bearer token — include as: Authorization: Bearer <token>.
    private String token;

    // Convenience field — the username this token belongs to.
    private String username;

    // Token expiry in seconds (e.g., 86400 = 24 hours).
    private long expiresIn;
}
