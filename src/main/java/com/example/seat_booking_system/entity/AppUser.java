package com.example.seat_booking_system.entity;

import jakarta.persistence.*;
import lombok.*;

// Step 1: AppUser entity — named "AppUser" to avoid collision with Spring Security's User and SQL reserved word.
// Step 2: role field drives authorization — ROLE_USER can book, ROLE_ADMIN can manage shows.
@Entity
@Table(name = "app_user")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AppUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Login name — unique across the system.
    @Column(nullable = false, unique = true, length = 50)
    private String username;

    // Email — used for notifications (future extension).
    @Column(nullable = false, unique = true, length = 100)
    private String email;

    // BCrypt hash — NEVER store plain text. Salt is embedded in the hash output.
    @Column(nullable = false)
    private String passwordHash;

    // Stored as STRING so adding enum values doesn't break the schema (ordinal is fragile).
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Role role;
}
