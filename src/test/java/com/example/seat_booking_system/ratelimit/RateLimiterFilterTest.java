package com.example.seat_booking_system.ratelimit;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.seat_booking_system.entity.AppUser;
import com.example.seat_booking_system.entity.Role;
import com.example.seat_booking_system.repository.AppUserRepository;
import com.example.seat_booking_system.security.JwtUtil;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class RateLimiterFilterTest {

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
    registry.add("app.rate-limit.requests-per-second", () -> "5");
  }

  @Autowired private MockMvc mockMvc;
  @Autowired private JwtUtil jwtUtil;
  @Autowired private AppUserRepository userRepository;
  @Autowired private PasswordEncoder passwordEncoder;

  private String jwtToken;

  @BeforeEach
  void setUp() {
    userRepository.deleteAll();

    AppUser user =
        userRepository.save(
            AppUser.builder()
                .username("ratelimituser")
                .email("rl@test.com")
                .passwordHash(passwordEncoder.encode("password"))
                .role(Role.USER)
                .build());

    UserDetails userDetails =
        new User(
            user.getUsername(),
            user.getPasswordHash(),
            List.of(new SimpleGrantedAuthority("ROLE_USER")));
    jwtToken = jwtUtil.generateToken(userDetails);
  }

  @Test
  void sixthRequest_returnsStatus429_andBucketRefilsAfterOneSecond() throws Exception {

    for (int i = 1; i <= 5; i++) {
      mockMvc
          .perform(
              post("/api/bookings")
                  .header("Authorization", "Bearer " + jwtToken)
                  .header("Idempotency-Key", "rl-key-" + i)
                  .contentType("application/json")
                  .content("{\"seatIds\":[999]}"))
          .andExpect(status().is(org.hamcrest.Matchers.not(429)));
    }

    mockMvc
        .perform(
            post("/api/bookings")
                .header("Authorization", "Bearer " + jwtToken)
                .header("Idempotency-Key", "rl-key-6")
                .contentType("application/json")
                .content("{\"seatIds\":[999]}"))
        .andExpect(status().isTooManyRequests());

    System.out.println("[RATE LIMIT TEST] 6th request correctly rejected with 429");

    Thread.sleep(1100);

    mockMvc
        .perform(
            post("/api/bookings")
                .header("Authorization", "Bearer " + jwtToken)
                .header("Idempotency-Key", "rl-key-7")
                .contentType("application/json")
                .content("{\"seatIds\":[999]}"))
        .andExpect(status().is(org.hamcrest.Matchers.not(429)));

    System.out.println("[RATE LIMIT TEST] After 1s refill, request succeeded (not 429)");
  }
}
