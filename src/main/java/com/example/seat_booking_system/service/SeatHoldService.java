package com.example.seat_booking_system.service;

import com.example.seat_booking_system.entity.Seat;
import com.example.seat_booking_system.entity.SeatStatus;
import com.example.seat_booking_system.repository.SeatRepository;
import com.example.seat_booking_system.service.ShowService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.NoSuchElementException;

// Step 6: Seat hold service — manages HELD state between user "Reserve" click and payment.
// AVAILABLE → HELD (holdSeat): seat reserved for 5 min so user can pay.
// HELD → AVAILABLE (releaseHold): called by scheduler on expiry or by user on cancel.
// Uses pessimistic locking to prevent two users from holding the same seat simultaneously.
@Service
@RequiredArgsConstructor
@Slf4j
public class SeatHoldService {

    private final SeatRepository seatRepository;
    private final ShowService showService;

    // Step 6.1: Places hold — AVAILABLE → HELD. Returns false if seat is not available.
    @Transactional
    public boolean holdSeat(Long seatId, String username) {
        // Step 6.2: Pessimistic lock — prevents concurrent holds on the same seat.
        Seat seat = seatRepository.findByIdWithPessimisticLock(seatId)
                .orElseThrow(() -> new NoSuchElementException("Seat not found: " + seatId));

        if (seat.getStatus() != SeatStatus.AVAILABLE) {
            log.debug("[HOLD] Seat {} is not available (status={})", seatId, seat.getStatus());
            return false;
        }

        // Step 6.3: Transition to HELD and record who holds it and when.
        seat.setStatus(SeatStatus.HELD);
        seat.setHeldBy(username);
        seat.setHeldAt(LocalDateTime.now());
        seatRepository.save(seat);

        // Step 9: Invalidate seat availability cache — seat is no longer AVAILABLE.
        showService.evictSeatCache(seat.getShow().getId());

        log.info("[HOLD] User {} placed hold on seat {}", username, seatId);
        return true;
    }

    // Step 6.4: Releases hold — HELD → AVAILABLE. Called by scheduler on expiry or user on cancel.
    @Transactional
    public void releaseHold(Seat seat) {
        seat.setStatus(SeatStatus.AVAILABLE);
        seat.setHeldBy(null);
        seat.setHeldAt(null);
        seatRepository.save(seat);

        // Step 9: Invalidate cache — seat is available again.
        showService.evictSeatCache(seat.getShow().getId());

        log.info("[HOLD] Released hold on seat {} (was held by {})", seat.getId(), seat.getHeldBy());
    }
}
