package com.example.seat_booking_system;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

// Entry point — Spring Boot app demonstrating concurrency strategies, caching, rate limiting, idempotency.
@SpringBootApplication
public class SeatBookingSystemApplication {

    public static void main(String[] args) {
        SpringApplication.run(SeatBookingSystemApplication.class, args);
    }

    // Configures Jackson with JavaTimeModule — required for LocalDateTime serialization/deserialization.
    // Used by the idempotency service to serialize BookingResponse to/from Redis.
    @Bean
    public ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        return mapper;
    }
}
