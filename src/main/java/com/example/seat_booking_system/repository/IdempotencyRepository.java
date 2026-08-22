package com.example.seat_booking_system.repository;

import com.example.seat_booking_system.entity.IdempotencyRecord;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface IdempotencyRepository extends JpaRepository<IdempotencyRecord, Long> {

  Optional<IdempotencyRecord> findByIdempotencyKey(String idempotencyKey);
}
