package com.example.seat_booking_system.scheduler;

import com.example.seat_booking_system.entity.Seat;
import com.example.seat_booking_system.repository.SeatRepository;
import com.example.seat_booking_system.service.SeatHoldService;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "spring.cache.type", havingValue = "redis", matchIfMissing = true)
@RequiredArgsConstructor
@Slf4j
public class HoldExpiryScheduler {

  private static final String LOCK_NAME = "lock:hold-expiry-scheduler";

  private final SeatRepository seatRepository;
  private final SeatHoldService seatHoldService;
  private final RedissonClient redissonClient;

  @Value("${app.hold.expiry-minutes:5}")
  private int holdExpiryMinutes;

  @Scheduled(fixedRate = 60_000)
  public void releaseExpiredHolds() {

    RLock lock = redissonClient.getLock(LOCK_NAME);
    boolean locked = false;

    try {

      locked = lock.tryLock(0, 50, TimeUnit.SECONDS);

      if (!locked) {
        log.debug("[SCHEDULER] Skipping — another instance holds the distributed lock");
        return;
      }

      log.info("[SCHEDULER] Acquired distributed lock, processing expired holds...");

      LocalDateTime expiryThreshold = LocalDateTime.now().minusMinutes(holdExpiryMinutes);

      List<Seat> expiredSeats = seatRepository.findExpiredHolds(expiryThreshold);

      if (expiredSeats.isEmpty()) {
        log.info("[SCHEDULER] No expired holds found");
        return;
      }

      log.info(
          "[SCHEDULER] Releasing {} expired holds (older than {} minutes)",
          expiredSeats.size(),
          holdExpiryMinutes);

      for (Seat seat : expiredSeats) {
        String prevHeldBy = seat.getHeldBy();
        LocalDateTime prevHeldAt = seat.getHeldAt();
        seatHoldService.releaseHold(seat);
        log.info(
            "[SCHEDULER] Released hold on seat {} (was held by {}, heldAt={})",
            seat.getId(),
            prevHeldBy,
            prevHeldAt);
      }

    } catch (InterruptedException e) {

      Thread.currentThread().interrupt();
      log.error("[SCHEDULER] Interrupted while acquiring distributed lock", e);
    } finally {

      if (locked && lock.isHeldByCurrentThread()) {
        lock.unlock();
        log.debug("[SCHEDULER] Released distributed lock");
      }
    }
  }
}
