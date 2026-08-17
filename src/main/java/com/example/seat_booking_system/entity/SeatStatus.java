package com.example.seat_booking_system.entity;

// Step 1: Lifecycle states for a Seat.
// State machine: AVAILABLE → HELD (user reserves) → BOOKED (payment done) | AVAILABLE (hold expired).
// Step 6: HELD state prevents double-booking during the payment window (max 5 minutes).
public enum SeatStatus {

    // Seat is free — can be held or booked.
    AVAILABLE,

    // Step 6: Seat is reserved for a short window (5 min). Tracked via Seat.heldBy/heldAt.
    //         Scheduler releases it if the user doesn't complete the booking.
    HELD,

    // Seat successfully booked — payment confirmed.
    BOOKED
}
