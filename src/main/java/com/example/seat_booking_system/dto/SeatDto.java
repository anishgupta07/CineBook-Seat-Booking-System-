package com.example.seat_booking_system.dto;

import com.example.seat_booking_system.entity.SeatStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

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
