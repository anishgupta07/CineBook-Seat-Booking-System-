package com.example.seat_booking_system.service;

import com.example.seat_booking_system.dto.BookingRequest;
import com.example.seat_booking_system.dto.BookingResponse;
import com.example.seat_booking_system.entity.AppUser;
import com.example.seat_booking_system.entity.Booking;
import com.example.seat_booking_system.entity.BookingSeat;
import com.example.seat_booking_system.entity.BookingStatus;
import com.example.seat_booking_system.entity.IdempotencyRecord;
import com.example.seat_booking_system.entity.Seat;
import com.example.seat_booking_system.entity.SeatStatus;
import com.example.seat_booking_system.repository.BookingRepository;
import com.example.seat_booking_system.repository.BookingSeatRepository;
import com.example.seat_booking_system.repository.IdempotencyRepository;
import com.example.seat_booking_system.repository.SeatRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

// Step 7: Idempotency-aware booking service.
// First request with key K → process and store result.
// Any retry with same key K → return stored result without re-processing.
// UNIQUE constraint on idempotency_key handles concurrent retries safely.
@Service
@RequiredArgsConstructor
@Slf4j
public class BookingService {

    private final SeatRepository seatRepository;
    private final BookingRepository bookingRepository;
    private final BookingSeatRepository bookingSeatRepository;
    private final IdempotencyRepository idempotencyRepository;
    private final ShowService showService;
    private final ObjectMapper objectMapper;

    // Step 7: Entry point — checks idempotency cache, then processes or replays booking.
    @Transactional
    public BookingResponse createBooking(BookingRequest request, AppUser user, String idempotencyKey) {

        // Step 7.1: Check idempotency cache — return stored response if key already seen.
        Optional<IdempotencyRecord> existingRecord =
                idempotencyRepository.findByIdempotencyKey(idempotencyKey);

        if (existingRecord.isPresent()) {
            log.info("[IDEMPOTENCY] Key '{}' found — returning cached response (no duplicate booking)",
                    idempotencyKey);
            return deserializeResponse(existingRecord.get().getResponseBody());
        }

        // Step 7.2: Key is a new — lock and validate seats (pessimistic lock prevents double-booking).
        List<Seat> seatsToBook = new ArrayList<>();
        for (Long seatId : request.getSeatIds()) {
            Seat seat = seatRepository.findByIdWithPessimisticLock(seatId)
                    .orElseThrow(() -> new IllegalArgumentException("Seat not found: " + seatId));

            if (seat.getStatus() != SeatStatus.AVAILABLE && seat.getStatus() != SeatStatus.HELD) {
                throw new IllegalStateException("Seat " + seatId + " is not bookable (status=" + seat.getStatus() + ")");
            }
            seatsToBook.add(seat);
        }

        // Step 7.3: Mark all seats as BOOKED and clear hold tracking fields.
        seatsToBook.forEach(seat -> {
            seat.setStatus(SeatStatus.BOOKED);
            seat.setHeldBy(null);
            seat.setHeldAt(null);
            seatRepository.save(seat);
        });

        // Step 7.4: Persist the booking record.
        Booking booking = Booking.builder()
                .user(user)
                .status(BookingStatus.CONFIRMED)
                .createdAt(LocalDateTime.now())
                .idempotencyKey(idempotencyKey)
                .build();
        bookingRepository.save(booking);

        // Step 7.5: Persist BookingSeat join records and evict seat availability cache.
        for (Seat seat : seatsToBook) {
            BookingSeat bs = BookingSeat.builder()
                    .booking(booking)
                    .seat(seat)
                    .priceAtBooking(java.math.BigDecimal.ZERO)
                    .build();
            bookingSeatRepository.save(bs);

            // Step 9: Invalidate cache for the affected show.
            showService.evictSeatCache(seat.getShow().getId());
        }

        BookingResponse response = BookingResponse.builder()
                .bookingId(booking.getId())
                .status(booking.getStatus())
                .seatIds(request.getSeatIds())
                .createdAt(booking.getCreatedAt())
                .message("Booking confirmed")
                .build();

        // Step 7.6: Persist the idempotency record so retries return this response.
        try {
            IdempotencyRecord record = IdempotencyRecord.builder()
                    .idempotencyKey(idempotencyKey)
                    .bookingId(booking.getId())
                    .responseStatus(200)
                    .responseBody(objectMapper.writeValueAsString(response))
                    .createdAt(LocalDateTime.now())
                    .build();
            idempotencyRepository.save(record);
        } catch (DataIntegrityViolationException e) {
            // Step 7.7: Concurrent duplicate — UNIQUE constraint blocked the second INSERT; safe to ignore.
            log.warn("[IDEMPOTENCY] Concurrent duplicate for key '{}' — ignored", idempotencyKey);
        } catch (JsonProcessingException e) {
            log.error("[IDEMPOTENCY] Failed to serialize response for key '{}'", idempotencyKey, e);
        }

        log.info("[IDEMPOTENCY] Created new booking {} for key '{}'", booking.getId(), idempotencyKey);
        return response;
    }

    private BookingResponse deserializeResponse(String json) {
        try {
            return objectMapper.readValue(json, BookingResponse.class);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to deserialize idempotency response", e);
        }
    }
}
