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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.NoSuchElementException;

// Step 4: Pessimistic locking booking service.
// Issues SELECT ... FOR UPDATE — acquires an exclusive DB row lock before reading.
// Concurrent booking attempts are SERIALIZED at the DB level: loser threads BLOCK until winner commits.
// PRO: zero double-bookings. CON: high contention causes queuing/latency; risk of deadlock with multiple rows.
@Service
@RequiredArgsConstructor
@Slf4j
public class PessimisticBookingService {

    private final SeatRepository seatRepository;
    private final BookingRepository bookingRepository;
    private final BookingSeatRepository bookingSeatRepository;

    // Step 4: Books a seat using SELECT ... FOR UPDATE. Returns false if seat is not available.
    @Transactional
    public boolean bookSeat(Long seatId, com.example.seat_booking_system.entity.AppUser user) {
        // Step 4.1: LOCK + READ — SELECT * FROM seat WHERE id = ? FOR UPDATE.
        //           Other threads calling this for the same seatId BLOCK here until we commit.
        Seat seat = seatRepository.findByIdWithPessimisticLock(seatId)
                .orElseThrow(() -> new NoSuchElementException("Seat not found: " + seatId));

        // Step 4.2: CHECK — safe to read status here; we hold the exclusive lock.
        if (seat.getStatus() != SeatStatus.AVAILABLE) {
            log.debug("[PESSIMISTIC] Seat {} not available, rejecting user {}", seatId, user.getUsername());
            return false;
        }

        // Step 4.3: WRITE — update status under the lock guarantee.
        seat.setStatus(SeatStatus.BOOKED);
        seatRepository.save(seat);

        // Step 4.4: Persist the booking record.
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

        log.info("[PESSIMISTIC] User {} successfully booked seat {}", user.getUsername(), seatId);
        // Step 4.5: Lock is released here when @Transactional commits.
        return true;
    }
}
