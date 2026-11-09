package com.example.seat_booking_system.controller;

import com.example.seat_booking_system.dto.BookingRequest;
import com.example.seat_booking_system.dto.BookingResponse;
import com.example.seat_booking_system.dto.BookingSummaryDto;
import com.example.seat_booking_system.entity.AppUser;
import com.example.seat_booking_system.repository.AppUserRepository;
import com.example.seat_booking_system.service.BookingService;
import com.example.seat_booking_system.service.SeatHoldService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/bookings")
@RequiredArgsConstructor
public class BookingController {

  private final BookingService bookingService;
  private final SeatHoldService seatHoldService;
  private final AppUserRepository userRepository;

  @PostMapping
  public ResponseEntity<BookingResponse> createBooking(
      @Valid @RequestBody BookingRequest request,
      @AuthenticationPrincipal UserDetails userDetails,
      @RequestHeader("Idempotency-Key") String idempotencyKey,
      @RequestHeader(value = "X-Simulate-Delay", required = false, defaultValue = "false") boolean simulateDelay) {

    AppUser user = resolveUser(userDetails);
    BookingResponse response =
        bookingService.createBooking(request, user, idempotencyKey, simulateDelay);
    return ResponseEntity.ok(response);
  }

  /** Holds a seat for 5 minutes for the authenticated user. */
  @PostMapping("/holds/{seatId}")
  public ResponseEntity<Map<String, Object>> holdSeat(
      @PathVariable Long seatId,
      @AuthenticationPrincipal UserDetails userDetails) {

    AppUser user = resolveUser(userDetails);
    boolean success = seatHoldService.holdSeat(seatId, user.getUsername());
    if (success) {
      return ResponseEntity.ok(Map.of("success", true, "message", "Seat held successfully for 5 minutes"));
    }
    return ResponseEntity.status(HttpStatus.CONFLICT)
        .body(Map.of("success", false, "message", "Seat is not available to hold"));
  }

  /** Releases a held seat before the hold expiry. */
  @DeleteMapping("/holds/{seatId}")
  public ResponseEntity<Map<String, Object>> releaseHold(
      @PathVariable Long seatId,
      @AuthenticationPrincipal UserDetails userDetails) {

    AppUser user = resolveUser(userDetails);
    boolean success = seatHoldService.releaseHold(seatId, user.getUsername());
    if (success) {
      return ResponseEntity.ok(Map.of("success", true, "message", "Seat hold released successfully"));
    }
    return ResponseEntity.badRequest()
        .body(Map.of("success", false, "message", "Unable to release hold"));
  }

  /** Returns all bookings made by the currently authenticated user. */
  @GetMapping("/my")
  public ResponseEntity<List<BookingSummaryDto>> getMyBookings(
      @AuthenticationPrincipal UserDetails userDetails) {

    AppUser user = resolveUser(userDetails);
    return ResponseEntity.ok(bookingService.getMyBookings(user));
  }

  private AppUser resolveUser(UserDetails userDetails) {
    return userRepository
        .findByUsername(userDetails.getUsername())
        .orElseThrow(
            () -> new RuntimeException("Authenticated user not found — this should never happen"));
  }
}
