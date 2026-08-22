package com.example.seat_booking_system.repository;

import com.example.seat_booking_system.entity.BookingSeat;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface BookingSeatRepository extends JpaRepository<BookingSeat, Long> {

  List<BookingSeat> findByBookingId(Long bookingId);

  boolean existsBySeatId(Long seatId);
}
