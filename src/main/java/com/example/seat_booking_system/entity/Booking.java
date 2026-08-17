package com.example.seat_booking_system.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

// Step 1: Booking entity — groups one or more seats reserved by a single user in one transaction.
// Step 7: idempotencyKey stored here so duplicate requests are identified and short-circuited.
// Uses explicit BookingSeat join entity instead of @ManyToMany — allows adding per-seat columns later.
@Entity
@Table(name = "booking")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Booking {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // FK to app_user — LAZY: full user details not always needed.
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser user;

    // Current state of this booking. See BookingStatus for valid transitions.
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private BookingStatus status;

    // When this record was created — set at persist time.
    @Column(nullable = false)
    private LocalDateTime createdAt;

    // Step 7: Client-supplied idempotency key — unique constraint prevents duplicate bookings.
    @Column(unique = true, length = 100)
    private String idempotencyKey;

    // Cascade ALL: creating/deleting a Booking manages its BookingSeat rows.
    // orphanRemoval: removing from list deletes the row from DB.
    @OneToMany(mappedBy = "booking", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<BookingSeat> bookingSeats = new ArrayList<>();
}
