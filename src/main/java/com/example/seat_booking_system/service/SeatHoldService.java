package com.example.seat_booking_system.service;

import com.example.seat_booking_system.entity.Seat;
import com.example.seat_booking_system.entity.SeatStatus;
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
public class SeatHoldService {

  private final SeatRepository seatRepository;
  private final ShowService showService;

  @Transactional
  public boolean holdSeat(Long seatId, String username) {

    Seat seat =
        seatRepository
            .findByIdWithPessimisticLock(seatId)
            .orElseThrow(() -> new NoSuchElementException("Seat not found: " + seatId));

    if (seat.getStatus() != SeatStatus.AVAILABLE) {
      log.debug("[HOLD] Seat {} is not available (status={})", seatId, seat.getStatus());
      return false;
    }

    seat.setStatus(SeatStatus.HELD);
    seat.setHeldBy(username);
    seat.setHeldAt(LocalDateTime.now());
    seatRepository.save(seat);

    showService.evictSeatCache(seat.getShow().getId());

    log.info("[HOLD] User {} placed hold on seat {}", username, seatId);
    return true;
  }

  @Transactional
  public void releaseHold(Seat seat) {
    seat.setStatus(SeatStatus.AVAILABLE);
    seat.setHeldBy(null);
    seat.setHeldAt(null);
    seatRepository.save(seat);

    showService.evictSeatCache(seat.getShow().getId());

    log.info("[HOLD] Released hold on seat {} (was held by {})", seat.getId(), seat.getHeldBy());
  }
}
