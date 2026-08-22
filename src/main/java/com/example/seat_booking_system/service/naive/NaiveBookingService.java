package com.example.seat_booking_system.service.naive;

import com.example.seat_booking_system.entity.Seat;
import com.example.seat_booking_system.entity.SeatStatus;
import com.example.seat_booking_system.repository.SeatRepository;
import java.util.NoSuchElementException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class NaiveBookingService {

  private final SeatRepository seatRepository;

  @Transactional
  public boolean bookSeat(Long seatId, String userId) {

    Seat seat =
        seatRepository
            .findById(seatId)
            .orElseThrow(() -> new NoSuchElementException("Seat not found: " + seatId));

    if (seat.getStatus() != SeatStatus.AVAILABLE) {
      log.debug(
          "Seat {} is not available (status={}), rejecting user {}",
          seatId,
          seat.getStatus(),
          userId);
      return false;
    }

    try {
      Thread.sleep(50);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return false;
    }

    seat.setStatus(SeatStatus.BOOKED);
    seatRepository.save(seat);

    log.info("[NAIVE] User {} booked seat {}", userId, seatId);
    return true;
  }
}
