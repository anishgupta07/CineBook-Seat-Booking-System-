package com.example.seat_booking_system.scheduler;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.seat_booking_system.entity.Seat;
import com.example.seat_booking_system.entity.SeatStatus;
import com.example.seat_booking_system.entity.Show;
import com.example.seat_booking_system.repository.SeatRepository;
import com.example.seat_booking_system.repository.ShowRepository;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest
@Testcontainers
class HoldExpirySchedulerTest {

  @Container
  static PostgreSQLContainer<?> postgres =
      new PostgreSQLContainer<>("postgres:16-alpine")
          .withDatabaseName("seatbooking_test")
          .withUsername("test")
          .withPassword("test");

  @SuppressWarnings("rawtypes")
  @Container
  static GenericContainer redis =
      new GenericContainer<>(DockerImageName.parse("redis:7-alpine")).withExposedPorts(6379);

  @DynamicPropertySource
  static void configureProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", postgres::getJdbcUrl);
    registry.add("spring.datasource.username", postgres::getUsername);
    registry.add("spring.datasource.password", postgres::getPassword);
    registry.add("spring.data.redis.host", redis::getHost);
    registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
    registry.add(
        "spring.redisson.address",
        () -> "redis://" + redis.getHost() + ":" + redis.getMappedPort(6379));
    registry.add("spring.cache.type", () -> "none");
  }

  @Autowired private HoldExpiryScheduler scheduler;
  @Autowired private SeatRepository seatRepository;
  @Autowired private ShowRepository showRepository;

  private Show show;

  @BeforeEach
  void setUp() {
    seatRepository.deleteAll();
    showRepository.deleteAll();

    show =
        showRepository.save(
            Show.builder()
                .title("Hold Test Show")
                .venue("Test Hall")
                .startTime(LocalDateTime.now().plusDays(1))
                .build());
  }

  @Test
  void scheduler_releasesExpiredHolds_butLeavesRecentHoldsIntact() {
    Seat expiredHold =
        seatRepository.save(
            Seat.builder()
                .seatNumber("EXPIRED-HOLD")
                .show(show)
                .status(SeatStatus.HELD)
                .heldBy("alice")
                .heldAt(LocalDateTime.now().minusMinutes(10))
                .build());

    Seat freshHold =
        seatRepository.save(
            Seat.builder()
                .seatNumber("FRESH-HOLD")
                .show(show)
                .status(SeatStatus.HELD)
                .heldBy("bob")
                .heldAt(LocalDateTime.now().minusMinutes(1))
                .build());

    Seat available =
        seatRepository.save(
            Seat.builder().seatNumber("AVAIL").show(show).status(SeatStatus.AVAILABLE).build());

    scheduler.releaseExpiredHolds();

    Seat released = seatRepository.findById(expiredHold.getId()).orElseThrow();
    assertThat(released.getStatus())
        .as("Expired hold should have been released to AVAILABLE")
        .isEqualTo(SeatStatus.AVAILABLE);
    assertThat(released.getHeldBy()).as("heldBy should be cleared after release").isNull();
    assertThat(released.getHeldAt()).as("heldAt should be cleared after release").isNull();

    Seat stillHeld = seatRepository.findById(freshHold.getId()).orElseThrow();
    assertThat(stillHeld.getStatus())
        .as("Fresh hold should still be HELD (not expired yet)")
        .isEqualTo(SeatStatus.HELD);
    assertThat(stillHeld.getHeldBy()).isEqualTo("bob");

    Seat unchanged = seatRepository.findById(available.getId()).orElseThrow();
    assertThat(unchanged.getStatus())
        .as("Available seat should not have been affected")
        .isEqualTo(SeatStatus.AVAILABLE);
  }
}
