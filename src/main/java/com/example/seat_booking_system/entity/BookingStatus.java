package com.example.seat_booking_system.entity;

// Step 1: Lifecycle states for a Booking.
// State machine: PENDING → CONFIRMED (payment ok) | CANCELLED (user/payment) | EXPIRED (hold timeout).
public enum BookingStatus {

    // Booking created, payment not yet confirmed — seats are HELD during this window.
    PENDING,

    // Payment confirmed — seats are BOOKED and cannot be claimed by others.
    CONFIRMED,

    // User cancelled or payment failed — seats released back to AVAILABLE.
    CANCELLED,

    // Step 6: Booking never confirmed in time — hold expiry scheduler transitions PENDING → EXPIRED.
    EXPIRED
}
