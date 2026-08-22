package com.example.seat_booking_system.scheduler;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
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
class DistributedLockTest {

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

  @Autowired private RedissonClient redissonClient;

  @Test
  void distributedLock_secondAcquireAttempt_isRejectedWhenFirstIsHeld()
      throws InterruptedException {

    final String lockName = "test:distributed-lock";

    RLock lockA = redissonClient.getLock(lockName);
    RLock lockB = redissonClient.getLock(lockName);

    boolean instanceALocked = lockA.tryLock(0, 5, TimeUnit.SECONDS);
    assertThat(instanceALocked).as("Instance A should successfully acquire the lock").isTrue();

    try {
      boolean instanceBLocked = lockB.tryLock(0, 5, TimeUnit.SECONDS);

      assertThat(instanceBLocked)
          .as("Instance B should be DENIED the lock while Instance A holds it")
          .isFalse();

      System.out.println("[DISTRIBUTED LOCK TEST] Instance A holds the lock.");
      System.out.println("[DISTRIBUTED LOCK TEST] Instance B correctly denied access.");

    } finally {
      if (lockA.isHeldByCurrentThread()) {
        lockA.unlock();
      }
    }

    boolean instanceBAfterRelease = lockB.tryLock(0, 5, TimeUnit.SECONDS);
    try {
      assertThat(instanceBAfterRelease)
          .as("After Instance A releases, Instance B should acquire the lock")
          .isTrue();
    } finally {
      if (lockB.isHeldByCurrentThread()) {
        lockB.unlock();
      }
    }
  }
}
