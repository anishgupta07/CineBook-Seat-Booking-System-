package com.example.seat_booking_system.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

// Step 7: Persists the result of each booking request against its idempotency key.
// On retry: key found → return stored response without re-processing.
// UNIQUE constraint on idempotency_key means only one INSERT wins under concurrent retries.
@Entity
@Table(name = "idempotency_record", indexes = {
        @Index(name = "idx_idempotency_key", columnList = "idempotency_key", unique = true)
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class IdempotencyRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Step 7.1: Client-supplied key (e.g., UUID) — unique index makes lookup O(log n).
    @Column(name = "idempotency_key", nullable = false, unique = true, length = 100)
    private String idempotencyKey;

    // The booking created for this key — null if the booking failed.
    @Column(name = "booking_id")
    private Long bookingId;

    // Step 7.2: HTTP status code returned to the client — replayed verbatim on retry.
    @Column(nullable = false)
    private int responseStatus;

    // Step 7.3: JSON-serialized response body — deserialized and returned on replay.
    @Column(columnDefinition = "TEXT")
    private String responseBody;

    // When this record was created — used for TTL cleanup (future work).
    @Column(nullable = false)
    private LocalDateTime createdAt;
}
