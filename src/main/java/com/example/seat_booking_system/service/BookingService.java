package com.example.seat_booking_system.service;

import com.example.seat_booking_system.dto.BookingRequest;
import com.example.seat_booking_system.dto.BookingResponse;
import com.example.seat_booking_system.dto.BookingSummaryDto;
import com.example.seat_booking_system.entity.AppUser;
import com.example.seat_booking_system.entity.Booking;
import com.example.seat_booking_system.entity.BookingSeat;
import com.example.seat_booking_system.entity.BookingStatus;
import com.example.seat_booking_system.entity.IdempotencyRecord;
import com.example.seat_booking_system.entity.Seat;
import com.example.seat_booking_system.entity.SeatStatus;
import com.example.seat_booking_system.repository.BookingRepository;
import com.example.seat_booking_system.repository.BookingSeatRepository;
import com.example.seat_booking_system.repository.IdempotencyRepository;
import com.example.seat_booking_system.repository.SeatRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class BookingService {

  private final SeatRepository seatRepository;
  private final BookingRepository bookingRepository;
  private final BookingSeatRepository bookingSeatRepository;
  private final IdempotencyRepository idempotencyRepository;
  private final ShowService showService;
  private final ObjectMapper objectMapper;

  @Transactional
  public BookingResponse createBooking(
      BookingRequest request, AppUser user, String idempotencyKey) {

    Optional<IdempotencyRecord> existingRecord =
        idempotencyRepository.findByIdempotencyKey(idempotencyKey);

    if (existingRecord.isPresent()) {
      log.info(
          "[IDEMPOTENCY] Key '{}' found — returning cached response (no duplicate booking)",
          idempotencyKey);
      return deserializeResponse(existingRecord.get().getResponseBody());
    }

    List<Seat> seatsToBook = new ArrayList<>();
    for (Long seatId : request.getSeatIds()) {
      Seat seat =
          seatRepository
              .findByIdWithPessimisticLock(seatId)
              .orElseThrow(() -> new IllegalArgumentException("Seat not found: " + seatId));

      if (seat.getStatus() != SeatStatus.AVAILABLE && seat.getStatus() != SeatStatus.HELD) {
        throw new IllegalStateException(
            "Seat " + seatId + " is not bookable (status=" + seat.getStatus() + ")");
      }
      seatsToBook.add(seat);
    }

    seatsToBook.forEach(
        seat -> {
          seat.setStatus(SeatStatus.BOOKED);
          seat.setHeldBy(null);
          seat.setHeldAt(null);
          seatRepository.save(seat);
        });

    Booking booking =
        Booking.builder()
            .user(user)
            .status(BookingStatus.CONFIRMED)
            .createdAt(LocalDateTime.now())
            .idempotencyKey(idempotencyKey)
            .build();
    bookingRepository.save(booking);

    for (Seat seat : seatsToBook) {
      BookingSeat bs =
          BookingSeat.builder()
              .booking(booking)
              .seat(seat)
              .priceAtBooking(java.math.BigDecimal.ZERO)
              .build();
      bookingSeatRepository.save(bs);

      showService.evictSeatCache(seat.getShow().getId());
    }

    BookingResponse response =
        BookingResponse.builder()
            .bookingId(booking.getId())
            .status(booking.getStatus())
            .seatIds(request.getSeatIds())
            .createdAt(booking.getCreatedAt())
            .message("Booking confirmed")
            .build();

    try {
      IdempotencyRecord record =
          IdempotencyRecord.builder()
              .idempotencyKey(idempotencyKey)
              .bookingId(booking.getId())
              .responseStatus(200)
              .responseBody(objectMapper.writeValueAsString(response))
              .createdAt(LocalDateTime.now())
              .build();
      idempotencyRepository.save(record);
    } catch (DataIntegrityViolationException e) {
      log.warn("[IDEMPOTENCY] Concurrent duplicate for key '{}' — ignored", idempotencyKey);
    } catch (JsonProcessingException e) {
      log.error("[IDEMPOTENCY] Failed to serialize response for key '{}'\n", idempotencyKey, e);
    }

    log.info("[IDEMPOTENCY] Created new booking {} for key '{}'", booking.getId(), idempotencyKey);
    return response;
  }

  /**
   * Returns a summary list of all bookings made by the given user,
   * including seat numbers and show details for display in the UI.
   */
  @Transactional(readOnly = true)
  public List<BookingSummaryDto> getMyBookings(AppUser user) {
    List<Booking> bookings = bookingRepository.findByUserId(user.getId());
    return bookings.stream()
        .map(booking -> {
          List<BookingSeat> bookingSeats = bookingSeatRepository.findByBookingId(booking.getId());
          List<String> seatNumbers = bookingSeats.stream()
              .map(bs -> bs.getSeat().getSeatNumber())
              .collect(Collectors.toList());

          Long showId = bookingSeats.isEmpty() ? null
              : bookingSeats.get(0).getSeat().getShow().getId();
          String showTitle = bookingSeats.isEmpty() ? "N/A"
              : bookingSeats.get(0).getSeat().getShow().getTitle();

          return BookingSummaryDto.builder()
              .bookingId(booking.getId())
              .status(booking.getStatus())
              .seatNumbers(seatNumbers)
              .showId(showId)
              .showTitle(showTitle)
              .createdAt(booking.getCreatedAt())
              .build();
        })
        .collect(Collectors.toList());
  }

  private BookingResponse deserializeResponse(String json) {
    try {
      return objectMapper.readValue(json, BookingResponse.class);
    } catch (JsonProcessingException e) {
      throw new RuntimeException("Failed to deserialize idempotency response", e);
    }
  }
}
