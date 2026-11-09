package com.example.seat_booking_system.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.seat_booking_system.dto.BookingRequest;
import com.example.seat_booking_system.dto.BookingResponse;
import com.example.seat_booking_system.entity.AppUser;
import com.example.seat_booking_system.entity.Role;
import com.example.seat_booking_system.entity.Seat;
import com.example.seat_booking_system.entity.SeatStatus;
import com.example.seat_booking_system.entity.Show;
import com.example.seat_booking_system.repository.AppUserRepository;
import com.example.seat_booking_system.repository.BookingRepository;
import com.example.seat_booking_system.repository.BookingSeatRepository;
import com.example.seat_booking_system.repository.IdempotencyRepository;
import com.example.seat_booking_system.repository.SeatRepository;
import com.example.seat_booking_system.repository.ShowRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
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
class IdempotencyServiceTest {

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

  @Autowired private BookingService bookingService;
  @Autowired private SeatRepository seatRepository;
  @Autowired private ShowRepository showRepository;
  @Autowired private AppUserRepository userRepository;
  @Autowired private BookingRepository bookingRepository;
  @Autowired private BookingSeatRepository bookingSeatRepository;
  @Autowired private IdempotencyRepository idempotencyRepository;

  private Seat testSeat;
  private AppUser testUser;
  private static final String IDEMPOTENCY_KEY = "test-key-abc-123";

  @BeforeEach
  void setUp() {
    idempotencyRepository.deleteAll();
    bookingSeatRepository.deleteAll();
    bookingRepository.deleteAll();
    seatRepository.deleteAll();
    showRepository.deleteAll();
    userRepository.deleteAll();

    Show show =
        showRepository.save(
            Show.builder()
                .title("Idempotency Test Show")
                .venue("Test Hall")
                .startTime(LocalDateTime.now().plusDays(1))
                .build());

    testSeat =
        seatRepository.save(
            Seat.builder().seatNumber("IDEM-1").status(SeatStatus.AVAILABLE).show(show).build());

    testUser =
        userRepository.save(
            AppUser.builder()
                .username("idemuser")
                .email("idem@test.com")
                .passwordHash("$2a$10$dummy")
                .role(Role.USER)
                .build());
  }

  @Test
  void createBooking_withSameIdempotencyKey_returnsOriginalResponseWithoutDuplicate() {
    BookingRequest request = new BookingRequest(testSeat.getShow().getId(), List.of(testSeat.getId()));

    BookingResponse firstResponse =
        bookingService.createBooking(request, testUser, IDEMPOTENCY_KEY);

    assertThat(firstResponse.getBookingId()).isNotNull();
    assertThat(bookingRepository.count()).isEqualTo(1);

    BookingResponse secondResponse =
        bookingService.createBooking(request, testUser, IDEMPOTENCY_KEY);

    assertThat(secondResponse.getBookingId())
        .as("Second request should return the same booking ID as the first")
        .isEqualTo(firstResponse.getBookingId());

    assertThat(bookingRepository.count())
        .as("No duplicate booking should have been created")
        .isEqualTo(1);

    assertThat(idempotencyRepository.count())
        .as("Only one idempotency record should exist for this key")
        .isEqualTo(1);
  }

  @Test
  void createBooking_withDifferentIdempotencyKeys_createsTwoBookings() {
    Seat seat2 =
        seatRepository.save(
            Seat.builder()
                .seatNumber("IDEM-2")
                .status(SeatStatus.AVAILABLE)
                .show(testSeat.getShow())
                .build());

    BookingRequest request1 = new BookingRequest(testSeat.getShow().getId(), List.of(testSeat.getId()));
    BookingRequest request2 = new BookingRequest(seat2.getShow().getId(), List.of(seat2.getId()));

    bookingService.createBooking(request1, testUser, "key-111");
    bookingService.createBooking(request2, testUser, "key-222");

    assertThat(bookingRepository.count())
        .as("Different idempotency keys should create separate bookings")
        .isEqualTo(2);
  }

  @Test
  void createBooking_withConcurrentSameIdempotencyKey_allReturnIdenticalBookingId()
      throws InterruptedException {
    int threadCount = 5;
    BookingRequest request =
        new BookingRequest(testSeat.getShow().getId(), List.of(testSeat.getId()));

    CountDownLatch startLatch = new CountDownLatch(1);
    CountDownLatch doneLatch = new CountDownLatch(threadCount);
    ExecutorService executor = Executors.newFixedThreadPool(threadCount);

    Set<Long> bookingIds = ConcurrentHashMap.newKeySet();
    AtomicInteger errors = new AtomicInteger(0);

    for (int i = 0; i < threadCount; i++) {
      executor.submit(
          () -> {
            try {
              startLatch.await();
              BookingResponse resp =
                  bookingService.createBooking(request, testUser, "concurrent-key-999");
              bookingIds.add(resp.getBookingId());
            } catch (Exception e) {
              errors.incrementAndGet();
            } finally {
              doneLatch.countDown();
            }
          });
    }

    startLatch.countDown();
    doneLatch.await();
    executor.shutdown();

    assertThat(errors.get())
        .as("All concurrent idempotent requests should succeed cleanly without crashing")
        .isEqualTo(0);
    assertThat(bookingIds)
        .as("All requests should return the exact same booking ID")
        .hasSize(1);
    assertThat(bookingRepository.count()).isEqualTo(1);
    assertThat(idempotencyRepository.count()).isEqualTo(1);
  }
}
