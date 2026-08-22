package com.example.seat_booking_system.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.*;

@Entity
@Table(name = "booking")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Booking {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "user_id", nullable = false)
  private AppUser user;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private BookingStatus status;

  @Column(nullable = false)
  private LocalDateTime createdAt;

  @Column(unique = true, length = 100)
  private String idempotencyKey;

  @OneToMany(mappedBy = "booking", cascade = CascadeType.ALL, orphanRemoval = true)
  @Builder.Default
  private List<BookingSeat> bookingSeats = new ArrayList<>();
}
