package com.example.seat_booking_system.service;

import com.example.seat_booking_system.entity.Booking;
import com.example.seat_booking_system.entity.BookingSeat;
import com.example.seat_booking_system.entity.BookingStatus;
import com.example.seat_booking_system.entity.Seat;
import com.example.seat_booking_system.entity.SeatStatus;
import com.example.seat_booking_system.repository.BookingRepository;
import com.example.seat_booking_system.repository.BookingSeatRepository;
import com.example.seat_booking_system.repository.SeatRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.NoSuchElementException;

// Step 5: Optimistic locking booking service.
// Reads without locking; at write time Hibernate issues UPDATE ... WHERE version=N.
// If another thread already committed, version is now N+1 → 0 rows updated → OptimisticLockingFailureException.
// PRO: no blocking at read time. CON: callers must handle failures and may need to retry.
@Service
@RequiredArgsConstructor
@Slf4j
public class OptimisticBookingService {

    private final SeatRepository seatRepository;
    private final BookingRepository bookingRepository;
    private final BookingSeatRepository bookingSeatRepository;

    // Step 5: Books a seat using @Version optimistic locking. Returns false on race loss (no exception thrown).
    @Transactional
    public boolean bookSeat(Long seatId, com.example.seat_booking_system.entity.AppUser user) {
        // Step 5.1: READ — no lock. Hibernate tracks the current version in the persistence context.
        Seat seat = seatRepository.findById(seatId)
                .orElseThrow(() -> new NoSuchElementException("Seat not found: " + seatId));

        // Step 5.2: CHECK — seat must be AVAILABLE.
        if (seat.getStatus() != SeatStatus.AVAILABLE) {
            log.debug("[OPTIMISTIC] Seat {} not available", seatId);
            return false;
        }

        try {
            // Step 5.3: WRITE — Hibernate issues UPDATE ... WHERE id=? AND version=N.
            //           If another thread committed first, version is N+1 → exception thrown.
            seat.setStatus(SeatStatus.BOOKED);
            seatRepository.save(seat);

            Booking booking = Booking.builder()
                    .user(user)
                    .status(BookingStatus.CONFIRMED)
                    .createdAt(LocalDateTime.now())
                    .build();
            bookingRepository.save(booking);

            BookingSeat bookingSeat = BookingSeat.builder()
                    .booking(booking)
                    .seat(seat)
                    .priceAtBooking(java.math.BigDecimal.ZERO)
                    .build();
            bookingSeatRepository.save(bookingSeat);

            log.info("[OPTIMISTIC] User {} successfully booked seat {}", user.getUsername(), seatId);
            return true;

        } catch (ObjectOptimisticLockingFailureException e) {
            // Step 5.4: CLEAN FAILURE — another thread won the race. Return false so caller can notify user.
            log.debug("[OPTIMISTIC] Concurrency conflict for seat {} — user {} lost the race", seatId, user.getUsername());
            return false;
        }
    }
}
