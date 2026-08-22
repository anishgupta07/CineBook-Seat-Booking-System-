package com.example.seat_booking_system.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.*;

@Entity
@Table(
    name = "seat",
    indexes = {@Index(name = "idx_seat_show_status", columnList = "show_id, status")})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Seat {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false, length = 20)
  private String seatNumber;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private SeatStatus status;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "show_id", nullable = false)
  private Show show;

  @Version private Long version;

  @Column(length = 50)
  private String heldBy;

  private LocalDateTime heldAt;
}
