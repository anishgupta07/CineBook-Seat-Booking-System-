package com.example.seat_booking_system.controller;

import com.example.seat_booking_system.dto.SeatDto;
import com.example.seat_booking_system.dto.ShowDto;
import com.example.seat_booking_system.service.ShowService;
import java.util.List;
import java.util.NoSuchElementException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/shows")
@RequiredArgsConstructor
public class ShowController {

  private final ShowService showService;

  /** Returns all shows — used by the frontend to populate the show selector. */
  @GetMapping
  public ResponseEntity<List<ShowDto>> getAllShows() {
    return ResponseEntity.ok(showService.getAllShows());
  }

  /**
   * Returns ALL seats for a show (AVAILABLE, HELD, BOOKED) so the
   * frontend can render the full seat map with the correct status colours.
   */
  @GetMapping("/{id}/seats")
  public ResponseEntity<List<SeatDto>> getSeats(@PathVariable("id") Long showId) {
    try {
      List<SeatDto> seats = showService.getAllSeats(showId);
      return ResponseEntity.ok(seats);
    } catch (NoSuchElementException e) {
      return ResponseEntity.notFound().build();
    }
  }
}
