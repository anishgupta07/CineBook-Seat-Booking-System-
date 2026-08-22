package com.example.seat_booking_system.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class BookingRequest {

  /** The show these seats belong to — used by the frontend for context. */
  @NotNull private Long showId;

  @NotNull @NotEmpty private List<Long> seatIds;
}
