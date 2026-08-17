package com.example.seat_booking_system.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;

// Step 1: Explicit join entity for the Booking↔Seat relationship.
// Preferred over @ManyToMany — allows extra columns (e.g., priceAtBooking) and cleaner queries.
// Unique constraint on (booking_id, seat_id) prevents the same seat appearing twice in a booking.
@Entity
@Table(name = "booking_seat",
        uniqueConstraints = @UniqueConstraint(
                name = "uc_bookingseat_booking_seat",
                columnNames = {"booking_id", "seat_id"}
        ))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BookingSeat {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // The booking this seat belongs to.
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "booking_id", nullable = false)
    private Booking booking;

    // The specific seat that was booked.
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "seat_id", nullable = false)
    private Seat seat;

    // Price at time of booking — stored here so price changes don't alter historical records.
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal priceAtBooking;
}
