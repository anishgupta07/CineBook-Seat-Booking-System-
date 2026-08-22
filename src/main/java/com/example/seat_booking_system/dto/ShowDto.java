package com.example.seat_booking_system.dto;

import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ShowDto {
  private Long id;
  private String title;
  private String venue;
  private LocalDateTime startTime;
}
