package com.example.seat_booking_system.service;

import com.example.seat_booking_system.entity.Booking;
import com.example.seat_booking_system.entity.BookingSeat;
import com.example.seat_booking_system.entity.BookingStatus;
import com.example.seat_booking_system.entity.Seat;
import com.example.seat_booking_system.entity.SeatStatus;
import com.example.seat_booking_system.repository.BookingRepository;
import com.example.seat_booking_system.repository.BookingSeatRepository;
import com.example.seat_booking_system.repository.SeatRepository;
import java.time.LocalDateTime;
import java.util.NoSuchElementException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class PessimisticBookingService {

  private final SeatRepository seatRepository;
  private final BookingRepository bookingRepository;
  private final BookingSeatRepository bookingSeatRepository;

  @Transactional
  public boolean bookSeat(Long seatId, com.example.seat_booking_system.entity.AppUser user) {

    Seat seat =
        seatRepository
            .findByIdWithPessimisticLock(seatId)
            .orElseThrow(() -> new NoSuchElementException("Seat not found: " + seatId));

    if (seat.getStatus() != SeatStatus.AVAILABLE) {
      log.debug(
          "[PESSIMISTIC] Seat {} not available, rejecting user {}", seatId, user.getUsername());
      return false;
    }

    seat.setStatus(SeatStatus.BOOKED);
    seatRepository.save(seat);

    Booking booking =
        Booking.builder()
            .user(user)
            .status(BookingStatus.CONFIRMED)
            .createdAt(LocalDateTime.now())
            .build();
    bookingRepository.save(booking);

    BookingSeat bookingSeat =
        BookingSeat.builder()
            .booking(booking)
            .seat(seat)
            .priceAtBooking(java.math.BigDecimal.ZERO)
            .build();
    bookingSeatRepository.save(bookingSeat);

    log.info("[PESSIMISTIC] User {} successfully booked seat {}", user.getUsername(), seatId);

    return true;
  }
}
