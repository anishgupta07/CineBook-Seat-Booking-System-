package com.example.seat_booking_system.service;

import com.example.seat_booking_system.dto.SeatDto;
import com.example.seat_booking_system.dto.ShowDto;
import com.example.seat_booking_system.entity.Seat;
import com.example.seat_booking_system.entity.Show;
import com.example.seat_booking_system.repository.SeatRepository;
import com.example.seat_booking_system.repository.ShowRepository;
import java.util.List;
import java.util.NoSuchElementException;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ShowService {

  private final SeatRepository seatRepository;
  private final ShowRepository showRepository;

  /**
   * Returns ALL seats for a show (AVAILABLE, HELD, BOOKED) so the frontend
   * can render the full seat map with correct status colours.
   * Result is cached in Redis for 10 s and evicted on every booking.
   */
  @Cacheable(value = "available-seats", key = "#showId")
  @Transactional(readOnly = true)
  public List<SeatDto> getAllSeats(Long showId) {
    showRepository
        .findById(showId)
        .orElseThrow(() -> new NoSuchElementException("Show not found: " + showId));

    return seatRepository.findByShowIdOrderBySeatNumberAsc(showId).stream()
        .map(this::toDto)
        .collect(java.util.stream.Collectors.toList());
  }

  @CacheEvict(value = "available-seats", key = "#showId")
  public void evictSeatCache(Long showId) {}

  public SeatDto toDto(Seat seat) {
    return SeatDto.builder()
        .id(seat.getId())
        .seatNumber(seat.getSeatNumber())
        .status(seat.getStatus())
        .showId(seat.getShow().getId())
        .build();
  }

  @Transactional(readOnly = true)
  public List<ShowDto> getAllShows() {
    return showRepository.findAll().stream()
        .map(s -> ShowDto.builder()
            .id(s.getId())
            .title(s.getTitle())
            .venue(s.getVenue())
            .startTime(s.getStartTime())
            .build())
        .collect(java.util.stream.Collectors.toList());
  }

  @Transactional
  public Show createShow(Show show) {
    return showRepository.save(show);
  }

  @Transactional
  public Seat createSeat(Seat seat) {
    return seatRepository.save(seat);
  }
}
