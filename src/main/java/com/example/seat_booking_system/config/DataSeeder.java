package com.example.seat_booking_system.config;

import com.example.seat_booking_system.entity.Seat;
import com.example.seat_booking_system.entity.SeatStatus;
import com.example.seat_booking_system.entity.Show;
import com.example.seat_booking_system.repository.SeatRepository;
import com.example.seat_booking_system.repository.ShowRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
@Slf4j
public class DataSeeder implements CommandLineRunner {

    private final ShowRepository showRepository;
    private final SeatRepository seatRepository;

    @Override
    public void run(String... args) throws Exception {
        if (showRepository.count() == 0) {
            log.info("Database is empty, seeding initial data...");
            Show show = new Show();
            show.setTitle("Avengers: Endgame");
            show.setVenue("Cinema 1");
            show.setStartTime(LocalDateTime.now().plusDays(1));
            showRepository.save(show);

            for (int i = 1; i <= 20; i++) {
                Seat seat = new Seat();
                seat.setShow(show);
                seat.setSeatNumber("A" + i);
                seat.setStatus(SeatStatus.AVAILABLE);
                seatRepository.save(seat);
            }
            log.info("Seeded 1 show and 20 seats.");
        }
    }
}
