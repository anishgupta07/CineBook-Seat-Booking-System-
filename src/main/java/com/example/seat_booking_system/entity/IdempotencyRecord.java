package com.example.seat_booking_system.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.*;

@Entity
@Table(
    name = "idempotency_record",
    indexes = {@Index(name = "idx_idempotency_key", columnList = "idempotency_key", unique = true)})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class IdempotencyRecord {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "idempotency_key", nullable = false, unique = true, length = 100)
  private String idempotencyKey;

  @Column(name = "booking_id")
  private Long bookingId;

  @Column(nullable = false)
  private int responseStatus;

  @Column(columnDefinition = "TEXT")
  private String responseBody;

  @Column(nullable = false)
  private LocalDateTime createdAt;
}
