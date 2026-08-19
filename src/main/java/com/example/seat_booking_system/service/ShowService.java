package com.example.seat_booking_system.service;

import com.example.seat_booking_system.dto.SeatDto;
import com.example.seat_booking_system.entity.Seat;
import com.example.seat_booking_system.entity.SeatStatus;
import com.example.seat_booking_system.entity.Show;
import com.example.seat_booking_system.repository.SeatRepository;
import com.example.seat_booking_system.repository.ShowRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.NoSuchElementException;

// Step 9: Cache-aside pattern for seat availability — @Cacheable on read, @CacheEvict on write.
// Cache key: "available-seats::{showId}" — TTL is 10s (see CacheConfig).
// All seat-mutating services call evictSeatCache after status changes.
@Service
@RequiredArgsConstructor
public class ShowService {

    private final SeatRepository seatRepository;
    private final ShowRepository showRepository;

    // Step 9.1: Returns AVAILABLE seats from Redis cache or DB on miss.
    //           Cache key includes showId so each show has its own cache entry.
    @Cacheable(value = "available-seats", key = "#showId")
    @Transactional(readOnly = true)
    public List<SeatDto> getAvailableSeats(Long showId) {
        // Verify show exists first — fail fast with a clear error on cache miss.
        showRepository.findById(showId)
                .orElseThrow(() -> new NoSuchElementException("Show not found: " + showId));

        return seatRepository.findByShowIdAndStatus(showId, SeatStatus.AVAILABLE)
                .stream()
                .map(this::toDto)
                .toList();
    }

    // Step 9.2: Evicts the cache entry for this show — called by booking/hold/release after any status change.
    //           No body needed; @CacheEvict is handled entirely by AOP proxy.
    @CacheEvict(value = "available-seats", key = "#showId")
    public void evictSeatCache(Long showId) {
    }

    // Maps Seat entity → SeatDto — hides internal fields like @Version, heldBy, heldAt.
    public SeatDto toDto(Seat seat) {
        return SeatDto.builder()
                .id(seat.getId())
                .seatNumber(seat.getSeatNumber())
                .status(seat.getStatus())
                .showId(seat.getShow().getId())
                .build();
    }

    // Admin/seeding helper — creates and persists a new Show.
    @Transactional
    public Show createShow(Show show) {
        return showRepository.save(show);
    }

    // Admin/seeding helper — creates and persists a new Seat.
    @Transactional
    public Seat createSeat(Seat seat) {
        return seatRepository.save(seat);
    }
}
