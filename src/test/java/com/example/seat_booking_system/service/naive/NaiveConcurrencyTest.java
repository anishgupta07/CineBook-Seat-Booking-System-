package com.example.seat_booking_system.service.naive;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.seat_booking_system.entity.AppUser;
import com.example.seat_booking_system.entity.Role;
import com.example.seat_booking_system.entity.Seat;
import com.example.seat_booking_system.entity.SeatStatus;
import com.example.seat_booking_system.entity.Show;
import com.example.seat_booking_system.repository.AppUserRepository;
import com.example.seat_booking_system.repository.SeatRepository;
import com.example.seat_booking_system.repository.ShowRepository;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@Testcontainers
class NaiveConcurrencyTest {

  @Container
  static PostgreSQLContainer<?> postgres =
      new PostgreSQLContainer<>("postgres:16-alpine")
          .withDatabaseName("seatbooking_test")
          .withUsername("test")
          .withPassword("test");

  @DynamicPropertySource
  static void configureProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", postgres::getJdbcUrl);
    registry.add("spring.datasource.username", postgres::getUsername);
    registry.add("spring.datasource.password", postgres::getPassword);
    registry.add("spring.cache.type", () -> "none");
    registry.add(
        "spring.autoconfigure.exclude",
        () ->
            "org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration,"
                + "org.redisson.spring.starter.RedissonAutoConfiguration");
  }

  @Autowired private NaiveBookingService naiveBookingService;
  @Autowired private SeatRepository seatRepository;
  @Autowired private ShowRepository showRepository;
  @Autowired private AppUserRepository userRepository;

  private Long testSeatId;
  private AppUser testUser;

  @BeforeEach
  void setUp() {
    seatRepository.deleteAll();
    showRepository.deleteAll();
    userRepository.deleteAll();

    Show show =
        showRepository.save(
            Show.builder()
                .title("Race Condition Demo")
                .venue("Test Hall")
                .startTime(LocalDateTime.now().plusDays(1))
                .build());

    Seat seat =
        seatRepository.save(
            Seat.builder().seatNumber("RACE-1").status(SeatStatus.AVAILABLE).show(show).build());

    testSeatId = seat.getId();

    testUser =
        userRepository.save(
            AppUser.builder()
                .username("raceuser")
                .email("race@test.com")
                .passwordHash("$2a$10$dummy")
                .role(Role.USER)
                .build());
  }

  @Test
  void naiveBooking_withConcurrentRequests_allowsDoubleBooking() throws InterruptedException {
    int threadCount = 10;
    AtomicInteger successCount = new AtomicInteger(0);
    AtomicInteger failureCount = new AtomicInteger(0);

    CountDownLatch startLatch = new CountDownLatch(1);
    CountDownLatch doneLatch = new CountDownLatch(threadCount);

    ExecutorService executor = Executors.newFixedThreadPool(threadCount);
    List<Future<?>> futures = new ArrayList<>();

    for (int i = 0; i < threadCount; i++) {
      futures.add(
          executor.submit(
              () -> {
                try {
                  startLatch.await();
                  boolean success =
                      naiveBookingService.bookSeat(testSeatId, testUser.getUsername());
                  if (success) {
                    successCount.incrementAndGet();
                  } else {
                    failureCount.incrementAndGet();
                  }
                } catch (Exception e) {
                  failureCount.incrementAndGet();
                } finally {
                  doneLatch.countDown();
                }
              }));
    }

    startLatch.countDown();
    doneLatch.await();
    executor.shutdown();

    System.out.println("=== NAIVE BOOKING RESULTS (STEP 3) ===");
    System.out.println("Successes: " + successCount.get() + " (expected > 1 — THIS IS THE BUG)");
    System.out.println("Failures:  " + failureCount.get());

    assertThat(successCount.get())
        .as(
            "NAIVE BOOKING BUG: Expected multiple threads to succeed (double-booking), "
                + "but only 1 succeeded. The race may not have manifested — try running again.")
        .isGreaterThan(1);
  }
}
