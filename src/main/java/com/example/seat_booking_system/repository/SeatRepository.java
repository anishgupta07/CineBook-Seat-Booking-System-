package com.example.seat_booking_system.repository;

import com.example.seat_booking_system.entity.Seat;
import com.example.seat_booking_system.entity.SeatStatus;
import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface SeatRepository extends JpaRepository<Seat, Long> {

  List<Seat> findByShowIdAndStatus(Long showId, SeatStatus status);

  List<Seat> findByShowIdOrderBySeatNumberAsc(Long showId);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("SELECT s FROM Seat s WHERE s.id = :id")
  Optional<Seat> findByIdWithPessimisticLock(@Param("id") Long id);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("SELECT s FROM Seat s WHERE s.id IN :ids ORDER BY s.id ASC")
  List<Seat> findAllByIdInWithPessimisticLock(@Param("ids") List<Long> ids);

  @Query("SELECT s FROM Seat s WHERE s.status = 'HELD' AND s.heldAt < :expiryTime")
  List<Seat> findExpiredHolds(@Param("expiryTime") LocalDateTime expiryTime);

  @Modifying
  @Query(
      "UPDATE Seat s SET s.status = 'AVAILABLE', s.heldBy = NULL, s.heldAt = NULL "
          + "WHERE s.status = 'HELD' AND s.heldAt < :expiryTime")
  int releaseExpiredHoldsBulk(@Param("expiryTime") LocalDateTime expiryTime);
}
