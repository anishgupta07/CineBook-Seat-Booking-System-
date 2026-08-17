package com.example.seat_booking_system.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

// Step 1: Seat entity — a physical or virtual seat within a Show.
// Steps 3–5: status is the concurrency-critical field multiple threads race to change.
// Step 5: version enables optimistic locking — Hibernate auto-increments on each UPDATE.
// Step 6: heldBy/heldAt track who holds the seat and when, so the scheduler can release stale holds.
@Entity
@Table(name = "seat", indexes = {
        // Composite index on (show_id, status) — optimizes the common query "AVAILABLE seats for show X".
        @Index(name = "idx_seat_show_status", columnList = "show_id, status")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Seat {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Human-readable label (e.g., "A1", "VIP-3") — unique within a Show, not globally.
    @Column(nullable = false, length = 20)
    private String seatNumber;

    // Step 3–5: The field threads race to change. See SeatStatus for the state machine.
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SeatStatus status;

    // Parent show — LAZY: we don't need the full Show every time we load a Seat.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "show_id", nullable = false)
    private Show show;

    // Step 5: Optimistic locking — Hibernate issues UPDATE ... WHERE version=N; throws if 0 rows updated.
    @Version
    private Long version;

    // Step 6: Username of the user currently holding this seat — null when AVAILABLE or BOOKED.
    @Column(length = 50)
    private String heldBy;

    // Step 6: Timestamp when hold was placed — scheduler releases holds older than 5 minutes.
    private LocalDateTime heldAt;
}
