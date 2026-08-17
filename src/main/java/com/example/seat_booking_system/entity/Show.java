package com.example.seat_booking_system.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

// Step 1: Show entity — represents a single screening or event instance.
// Owns many Seats (parent side); deleting a Show cascades to its Seats.
@Entity
@Table(name = "show")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Show {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Human-readable event title (e.g., "Interstellar", "Ed Sheeran Live").
    @Column(nullable = false, length = 200)
    private String title;

    // Venue name or hall (e.g., "Screen 3", "Hall A").
    @Column(nullable = false, length = 200)
    private String venue;

    // When the event starts — stored as TIMESTAMP WITHOUT TIME ZONE in Postgres.
    @Column(nullable = false)
    private LocalDateTime startTime;

    // Bidirectional one-to-many: Seat owns the FK column (mappedBy="show"). LAZY fetch.
    @OneToMany(mappedBy = "show", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @Builder.Default
    private List<Seat> seats = new ArrayList<>();
}
