package com.example.seat_booking_system.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.seat_booking_system.entity.AppUser;
import com.example.seat_booking_system.entity.Role;
import com.example.seat_booking_system.entity.Seat;
import com.example.seat_booking_system.entity.SeatStatus;
import com.example.seat_booking_system.entity.Show;
import com.example.seat_booking_system.repository.AppUserRepository;
import com.example.seat_booking_system.repository.BookingRepository;
import com.example.seat_booking_system.repository.BookingSeatRepository;
import com.example.seat_booking_system.repository.SeatRepository;
import com.example.seat_booking_system.repository.ShowRepository;
import java.time.LocalDateTime;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
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
class PessimisticConcurrencyTest {

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

  @Autowired private PessimisticBookingService pessimisticBookingService;
  @Autowired private SeatRepository seatRepository;
  @Autowired private ShowRepository showRepository;
  @Autowired private AppUserRepository userRepository;
  @Autowired private BookingRepository bookingRepository;
  @Autowired private BookingSeatRepository bookingSeatRepository;

  private Long testSeatId;
  private AppUser testUser;

  @BeforeEach
  void setUp() {
    bookingSeatRepository.deleteAll();
    bookingRepository.deleteAll();
    seatRepository.deleteAll();
    showRepository.deleteAll();
    userRepository.deleteAll();

    Show show =
        showRepository.save(
            Show.builder()
                .title("Pessimistic Lock Demo")
                .venue("Test Hall")
                .startTime(LocalDateTime.now().plusDays(1))
                .build());

    Seat seat =
        seatRepository.save(
            Seat.builder().seatNumber("PESS-1").status(SeatStatus.AVAILABLE).show(show).build());

    testSeatId = seat.getId();

    testUser =
        userRepository.save(
            AppUser.builder()
                .username("pessuser")
                .email("pess@test.com")
                .passwordHash("$2a$10$dummy")
                .role(Role.USER)
                .build());
  }

  @Test
  void pessimisticBooking_withConcurrentRequests_allowsExactlyOneSuccess()
      throws InterruptedException {

    int threadCount = 10;
    AtomicInteger successCount = new AtomicInteger(0);
    AtomicInteger failureCount = new AtomicInteger(0);

    CountDownLatch startLatch = new CountDownLatch(1);
    CountDownLatch doneLatch = new CountDownLatch(threadCount);
    ExecutorService executor = Executors.newFixedThreadPool(threadCount);

    for (int i = 0; i < threadCount; i++) {
      executor.submit(
          () -> {
            try {
              startLatch.await();
              boolean success = pessimisticBookingService.bookSeat(testSeatId, testUser);
              if (success) successCount.incrementAndGet();
              else failureCount.incrementAndGet();
            } catch (Exception e) {
              failureCount.incrementAndGet();
            } finally {
              doneLatch.countDown();
            }
          });
    }

    startLatch.countDown();
    doneLatch.await();
    executor.shutdown();

    System.out.println("=== PESSIMISTIC BOOKING RESULTS (STEP 4) ===");
    System.out.println("Successes: " + successCount.get() + " (expected exactly 1)");
    System.out.println("Failures:  " + failureCount.get() + " (expected 9 clean rejections)");

    assertThat(successCount.get())
        .as("Pessimistic locking should allow exactly one booking")
        .isEqualTo(1);

    assertThat(failureCount.get())
        .as("All other 9 threads should have been cleanly rejected")
        .isEqualTo(9);

    Seat seat = seatRepository.findById(testSeatId).orElseThrow();
    assertThat(seat.getStatus()).isEqualTo(SeatStatus.BOOKED);
    assertThat(bookingRepository.count()).isEqualTo(1);
  }
}
