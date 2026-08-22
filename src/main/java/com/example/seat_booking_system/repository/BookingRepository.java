package com.example.seat_booking_system.repository;

import com.example.seat_booking_system.entity.Booking;
import com.example.seat_booking_system.entity.BookingStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface BookingRepository extends JpaRepository<Booking, Long> {

  List<Booking> findByUserId(Long userId);

  List<Booking> findByStatus(BookingStatus status);

  Optional<Booking> findByIdempotencyKey(String idempotencyKey);
}
