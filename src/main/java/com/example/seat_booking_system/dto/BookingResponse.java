package com.example.seat_booking_system.dto;

import com.example.seat_booking_system.entity.BookingStatus;
import java.time.LocalDateTime;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BookingResponse {
  private Long bookingId;
  private BookingStatus status;
  private List<Long> seatIds;
  private LocalDateTime createdAt;
  private String message;
}
