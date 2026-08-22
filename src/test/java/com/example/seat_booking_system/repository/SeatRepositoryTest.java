package com.example.seat_booking_system.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.seat_booking_system.entity.Seat;
import com.example.seat_booking_system.entity.SeatStatus;
import com.example.seat_booking_system.entity.Show;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

@DataJpaTest
@org.springframework.test.context.TestPropertySource(
    properties = {"spring.flyway.enabled=false", "spring.jpa.hibernate.ddl-auto=create-drop"})
class SeatRepositoryTest {

  @Autowired private SeatRepository seatRepository;

  @Autowired private TestEntityManager entityManager;

  @Test
  void findByShowIdAndStatus_returnsOnlyAvailableSeats() {
    Show show =
        Show.builder()
            .title("Interstellar")
            .venue("IMAX Screen 1")
            .startTime(LocalDateTime.now().plusDays(7))
            .build();
    entityManager.persist(show);

    Seat available =
        Seat.builder().seatNumber("A1").show(show).status(SeatStatus.AVAILABLE).build();
    entityManager.persist(available);

    Seat booked = Seat.builder().seatNumber("A2").show(show).status(SeatStatus.BOOKED).build();
    entityManager.persist(booked);

    Seat held =
        Seat.builder()
            .seatNumber("A3")
            .show(show)
            .status(SeatStatus.HELD)
            .heldBy("alice")
            .heldAt(LocalDateTime.now())
            .build();
    entityManager.persist(held);

    entityManager.flush();

    List<Seat> result = seatRepository.findByShowIdAndStatus(show.getId(), SeatStatus.AVAILABLE);

    assertThat(result).hasSize(1);
    assertThat(result.get(0).getSeatNumber()).isEqualTo("A1");
    assertThat(result.get(0).getStatus()).isEqualTo(SeatStatus.AVAILABLE);
  }

  @Test
  void findByShowIdAndStatus_withNoAvailableSeats_returnsEmptyList() {
    Show show =
        Show.builder()
            .title("Dune Part Two")
            .venue("Hall B")
            .startTime(LocalDateTime.now().plusDays(3))
            .build();
    entityManager.persist(show);

    Seat booked = Seat.builder().seatNumber("B5").show(show).status(SeatStatus.BOOKED).build();
    entityManager.persist(booked);
    entityManager.flush();

    List<Seat> result = seatRepository.findByShowIdAndStatus(show.getId(), SeatStatus.AVAILABLE);

    assertThat(result).isEmpty();
  }

  @Test
  void findExpiredHolds_returnsOnlyExpiredHeldSeats() {
    Show show =
        Show.builder()
            .title("Oppenheimer")
            .venue("Screen 5")
            .startTime(LocalDateTime.now().plusDays(1))
            .build();
    entityManager.persist(show);

    Seat expiredHold =
        Seat.builder()
            .seatNumber("C1")
            .show(show)
            .status(SeatStatus.HELD)
            .heldBy("bob")
            .heldAt(LocalDateTime.now().minusMinutes(10))
            .build();
    entityManager.persist(expiredHold);

    Seat freshHold =
        Seat.builder()
            .seatNumber("C2")
            .show(show)
            .status(SeatStatus.HELD)
            .heldBy("charlie")
            .heldAt(LocalDateTime.now().minusMinutes(2))
            .build();
    entityManager.persist(freshHold);

    Seat available =
        Seat.builder().seatNumber("C3").show(show).status(SeatStatus.AVAILABLE).build();
    entityManager.persist(available);
    entityManager.flush();

    LocalDateTime expiryThreshold = LocalDateTime.now().minusMinutes(5);
    List<Seat> expired = seatRepository.findExpiredHolds(expiryThreshold);

    assertThat(expired).hasSize(1);
    assertThat(expired.get(0).getSeatNumber()).isEqualTo("C1");
  }
}
