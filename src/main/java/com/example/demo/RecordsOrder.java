package com.example.demo;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

// Shared "All records" order for the nurse and doctor workspaces: the most
// recently checked-in students first, then visits without a check-in (booked,
// no-show, cancelled), newest appointment date first.
final class RecordsOrder {

    private RecordsOrder() {}

    // One query for every check-in, keyed by appointment id, instead of a lookup per row.
    static Map<Long, CheckIn> checkInsByAppointment(CheckInRepository checkInRepository) {
        return checkInRepository.findAll().stream()
                .filter(c -> c.getAppointment() != null)
                .collect(Collectors.toMap(c -> c.getAppointment().getId(), c -> c, (a, b) -> a));
    }

    static List<Appointment> latestCheckInFirst(List<Appointment> appointments, Map<Long, CheckIn> checkIns) {
        Function<Appointment, LocalDateTime> checkedInAt = a -> {
            CheckIn c = checkIns.get(a.getId());
            return c != null ? c.getCheckInTime() : null;
        };
        return appointments.stream()
                .sorted(Comparator
                        .comparing(checkedInAt, Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(a -> a.getAppointmentDate(), Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(a -> a.getId(), Comparator.reverseOrder()))
                .toList();
    }
}
