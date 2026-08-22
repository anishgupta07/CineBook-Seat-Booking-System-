package com.example.seat_booking_system.controller;

import com.example.seat_booking_system.dto.BookingRequest;
import com.example.seat_booking_system.dto.BookingResponse;
import com.example.seat_booking_system.dto.BookingSummaryDto;
import com.example.seat_booking_system.entity.AppUser;
import com.example.seat_booking_system.repository.AppUserRepository;
import com.example.seat_booking_system.service.BookingService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/bookings")
@RequiredArgsConstructor
public class BookingController {

  private final BookingService bookingService;
  private final AppUserRepository userRepository;

  @PostMapping
  public ResponseEntity<BookingResponse> createBooking(
      @Valid @RequestBody BookingRequest request,
      @AuthenticationPrincipal UserDetails userDetails,
      @RequestHeader("Idempotency-Key") String idempotencyKey) {

    AppUser user = resolveUser(userDetails);
    BookingResponse response = bookingService.createBooking(request, user, idempotencyKey);
    return ResponseEntity.ok(response);
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
