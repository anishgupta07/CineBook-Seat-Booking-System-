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
public class BookingSummaryDto {
  private Long bookingId;
  private BookingStatus status;
  private List<String> seatNumbers;
  private Long showId;
  private String showTitle;
  private LocalDateTime createdAt;
}
