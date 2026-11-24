package com.slotbook.booking;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import javax.validation.Valid;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/bookings")
public class BookingController {

    private final BookingRepository repository;

    public BookingController(BookingRepository repository) {
        this.repository = repository;
    }

    @GetMapping
    public Page<Booking> list(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String date,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {

        List<Booking> all = repository.findAll().stream()
                .sorted(Comparator.comparing(Booking::getStartTime))
                .collect(Collectors.toList());

        if (status != null && !status.isBlank()) {
            BookingStatus wanted;
            try {
                wanted = BookingStatus.valueOf(status.toUpperCase());
            } catch (IllegalArgumentException e) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown status.");
            }
            all = all.stream().filter(b -> b.getStatus() == wanted).collect(Collectors.toList());
        }
        if (q != null && !q.isBlank()) {
            String needle = q.toLowerCase();
            all = all.stream()
                    .filter(b -> b.getCustomerName().toLowerCase().contains(needle)
                            || b.getService().toLowerCase().contains(needle))
                    .collect(Collectors.toList());
        }
        if (date != null && !date.isBlank()) {
            LocalDate day = LocalDate.parse(date);
            all = all.stream()
                    .filter(b -> b.getStartTime().toLocalDate().equals(day))
                    .collect(Collectors.toList());
        }

        int from = Math.min(page * size, all.size());
        int to = Math.min(from + size, all.size());
        return new PageImpl<>(all.subList(from, to), PageRequest.of(page, size), all.size());
    }

    @GetMapping("/stats")
    public Map<String, Long> stats() {
        List<Booking> all = repository.findAll();
        LocalDate today = LocalDate.now();
        return Map.of(
                "total", (long) all.size(),
                "pending", all.stream().filter(b -> b.getStatus() == BookingStatus.PENDING).count(),
                "confirmed", all.stream().filter(b -> b.getStatus() == BookingStatus.CONFIRMED).count(),
                "cancelled", all.stream().filter(b -> b.getStatus() == BookingStatus.CANCELLED).count(),
                "today", all.stream().filter(b -> b.getStartTime().toLocalDate().equals(today)).count());
    }

    @PostMapping
    public ResponseEntity<Booking> create(@Valid @RequestBody Booking booking) {
        boolean conflict = repository.findByStatusNot(BookingStatus.CANCELLED).stream()
                .anyMatch(existing -> existing.overlaps(booking));
        if (conflict) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "That time overlaps an existing booking.");
        }
        booking.setStatus(BookingStatus.PENDING);
        return ResponseEntity.status(HttpStatus.CREATED).body(repository.save(booking));
    }

    @PatchMapping("/{id}/status")
    public Booking updateStatus(@PathVariable Long id, @RequestBody StatusUpdate update) {
        Booking booking = repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        booking.setStatus(update.getStatus());
        return repository.save(booking);
    }

    public static class StatusUpdate {
        private BookingStatus status;

        public BookingStatus getStatus() {
            return status;
        }

        public void setStatus(BookingStatus status) {
            this.status = status;
        }
    }
}
