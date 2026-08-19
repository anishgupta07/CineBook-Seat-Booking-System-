package com.example.seat_booking_system.service.naive;

import com.example.seat_booking_system.entity.Seat;
import com.example.seat_booking_system.entity.SeatStatus;
import com.example.seat_booking_system.repository.SeatRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.NoSuchElementException;

// Step 3: DELIBERATELY BROKEN — demonstrates TOCTOU (Time-Of-Check to Time-Of-Use) race condition.
// READ COMMITTED isolation: two transactions both read AVAILABLE before either writes BOOKED → double-booking.
// Artificial 50ms sleep widens the race window to make it reliably reproducible in tests.
// DO NOT USE IN PRODUCTION — exists only as a "before" reference for the concurrency lesson.
@Service
@RequiredArgsConstructor
@Slf4j
public class NaiveBookingService {

    private final SeatRepository seatRepository;

    // Step 3: Books without any concurrency control — race window between findById and save.
    @Transactional
    public boolean bookSeat(Long seatId, String userId) {
        // Step 3.1: READ — two threads can both read AVAILABLE before either writes BOOKED.
        //           Under READ COMMITTED, each transaction sees only committed data at read time.
        Seat seat = seatRepository.findById(seatId)
                .orElseThrow(() -> new NoSuchElementException("Seat not found: " + seatId));

        // Step 3.2: CHECK — no lock held; another thread can pass this check simultaneously.
        if (seat.getStatus() != SeatStatus.AVAILABLE) {
            log.debug("Seat {} is not available (status={}), rejecting user {}", seatId, seat.getStatus(), userId);
            return false;
        }

        // Step 3.3: ARTIFICIAL DELAY — simulates real latency (e.g., calling payment service).
        //           Widens the race window so tests with 10 threads reliably expose the bug.
        try {
            Thread.sleep(50);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }

        // Step 3.4: WRITE — another thread may have already written BOOKED here.
        //           We blindly overwrite it → double-booking. This is the bug.
        seat.setStatus(SeatStatus.BOOKED);
        seatRepository.save(seat);

        log.info("[NAIVE] User {} booked seat {}", userId, seatId);
        return true;
    }
}
