package com.example.seat_booking_system.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

// Request body for creating a new booking.
// Step 2: User identity intentionally omitted — always derived from the JWT, never from the body.
@Data
@NoArgsConstructor
@AllArgsConstructor
public class BookingRequest {

    // List of seat IDs to book — must contain at least one entry.
    @NotNull
    @NotEmpty
    private List<Long> seatIds;
}
