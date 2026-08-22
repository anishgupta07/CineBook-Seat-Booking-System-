package com.example.seat_booking_system.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import lombok.*;

@Entity
@Table(
    name = "booking_seat",
    uniqueConstraints =
        @UniqueConstraint(
            name = "uc_bookingseat_booking_seat",
            columnNames = {"booking_id", "seat_id"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BookingSeat {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "booking_id", nullable = false)
  private Booking booking;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "seat_id", nullable = false)
  private Seat seat;

  @Column(nullable = false, precision = 10, scale = 2)
  private BigDecimal priceAtBooking;
}
