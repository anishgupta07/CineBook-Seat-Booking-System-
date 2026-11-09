package com.example.seat_booking_system.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.seat_booking_system.entity.Seat;
import com.example.seat_booking_system.entity.SeatStatus;
import com.example.seat_booking_system.entity.Show;
import com.example.seat_booking_system.repository.BookingRepository;
import com.example.seat_booking_system.repository.BookingSeatRepository;
import com.example.seat_booking_system.repository.SeatRepository;
import com.example.seat_booking_system.repository.ShowRepository;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest
@Testcontainers
class SeatCacheTest {

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
  }

  @Autowired private ShowService showService;
  @Autowired private SeatRepository seatRepository;
  @Autowired private ShowRepository showRepository;
  @Autowired private CacheManager cacheManager;
  @Autowired private BookingRepository bookingRepository;
  @Autowired private BookingSeatRepository bookingSeatRepository;

  private Show show;

  @BeforeEach
  void setUp() {
    cacheManager.getCache("available-seats").clear();

    bookingSeatRepository.deleteAll();
    bookingRepository.deleteAll();
    seatRepository.deleteAll();
    showRepository.deleteAll();

    show =
        showRepository.save(
            Show.builder()
                .title("Cache Test Show")
                .venue("Redis Hall")
                .startTime(LocalDateTime.now().plusDays(1))
                .build());

    seatRepository.save(
        Seat.builder().seatNumber("CACHE-1").status(SeatStatus.AVAILABLE).show(show).build());

    seatRepository.save(
        Seat.builder().seatNumber("CACHE-2").status(SeatStatus.AVAILABLE).show(show).build());
  }

  @Test
  void getAllSeats_cacheHit_returnsCachedResult() {
    // getAllSeats now returns all statuses — AVAILABLE seats only for this test
    var firstResult = showService.getAllSeats(show.getId());
    assertThat(firstResult).hasSize(2);

    seatRepository.save(
        Seat.builder()
            .seatNumber("CACHE-3-INJECTED")
            .status(SeatStatus.AVAILABLE)
            .show(show)
            .build());

    var secondResult = showService.getAllSeats(show.getId());
    assertThat(secondResult)
        .as("Second call should return cached result (2 seats, not the newly injected 3)")
        .hasSize(2);

    System.out.println("[CACHE TEST] Cache hit confirmed — second call served from Redis");
  }

  @Test
  void getAllSeats_afterCacheEviction_returnsUpdatedResult() {
    var firstResult = showService.getAllSeats(show.getId());
    assertThat(firstResult).hasSize(2);

    showService.evictSeatCache(show.getId());

    List<Seat> seats = seatRepository.findByShowIdAndStatus(show.getId(), SeatStatus.AVAILABLE);
    Seat firstSeat = seats.get(0);
    firstSeat.setStatus(SeatStatus.BOOKED);
    seatRepository.save(firstSeat);

    // After eviction, cache is refreshed — returns 2 seats total (1 AVAILABLE + 1 BOOKED)
    var afterEviction = showService.getAllSeats(show.getId());
    assertThat(afterEviction)
        .as("After cache eviction, should see all 2 seats (1 available, 1 booked)")
        .hasSize(2);

    assertThat(afterEviction.stream().anyMatch(s -> s.getStatus() == SeatStatus.BOOKED))
        .as("After cache eviction, the updated BOOKED status must be visible")
        .isTrue();

    System.out.println("[CACHE TEST] Cache eviction confirmed — post-eviction call hit DB");
  }
}
