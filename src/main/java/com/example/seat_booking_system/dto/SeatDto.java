package com.example.seat_booking_system.dto;

import com.example.seat_booking_system.entity.SeatStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

// DTO for seat availability responses — exposes only public fields (hides @Version, heldBy, heldAt, etc.).
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SeatDto {
    private Long id;
    private String seatNumber;
    private SeatStatus status;
    private Long showId;
}
