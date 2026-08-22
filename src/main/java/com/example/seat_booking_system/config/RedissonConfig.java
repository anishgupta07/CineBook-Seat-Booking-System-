package com.example.seat_booking_system.config;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(name = "spring.cache.type", havingValue = "redis", matchIfMissing = true)
public class RedissonConfig {

  @Value("${spring.redisson.address:redis://localhost:6379}")
  private String redissonAddress;

  @Value("${spring.data.redis.username:}")
  private String redisUsername;

  @Value("${spring.data.redis.password:}")
  private String redisPassword;

  @Bean(destroyMethod = "shutdown")
  public RedissonClient redissonClient() {
    Config config = new Config();

    var singleServerConfig =
        config
            .useSingleServer()
            .setAddress(redissonAddress)
            .setConnectionPoolSize(10)
            .setConnectionMinimumIdleSize(2)
            .setConnectTimeout(10_000)
            .setTimeout(3_000)
            .setRetryAttempts(3)
            .setRetryInterval(1_500);

    if (redisUsername != null && !redisUsername.isBlank()) {
      singleServerConfig.setUsername(redisUsername);
    }

    if (redisPassword != null && !redisPassword.isBlank()) {
      singleServerConfig.setPassword(redisPassword);
    }

    return Redisson.create(config);
  }
}
