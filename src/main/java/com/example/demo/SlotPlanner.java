package com.example.demo;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

// Splits a doctor's working hours (e.g. 09:30-18:00) into the individual
// appointment slots students book, one student per slot. Shared by the schedule
// endpoints and the startup clean-up of old long slots, so the rules match.
final class SlotPlanner {

    private SlotPlanner() {}

    static final int DEFAULT_MINUTES = 30;
    // Anything longer than this is working hours, not a single appointment.
    static final int MAX_APPOINTMENT_MINUTES = 60;
    // Clinic is closed for lunch 1-2 PM, so no appointments are created then.
    static final LocalTime LUNCH_START = LocalTime.of(13, 0);
    static final LocalTime LUNCH_END = LocalTime.of(14, 0);

    // Slots are stored as "09:00 AM - 09:30 AM".
    private static final DateTimeFormatter SLOT_TIME_FORMAT = new DateTimeFormatterBuilder()
            .parseCaseInsensitive().appendPattern("hh:mm a").toFormatter(Locale.US);

    record Plan(List<String> timeSlots, int skippedLunch, int skippedPast, int skippedOverlap) {}

    // "10:00 AM - 10:30 AM" -> [10:00, 10:30]; null if the row isn't in that format.
    static LocalTime[] parseRange(String timeSlot) {
        if (timeSlot == null) return null;
        String[] parts = timeSlot.split("-");
        if (parts.length != 2) return null;
        try {
            return new LocalTime[] {
                LocalTime.parse(parts[0].trim(), SLOT_TIME_FORMAT),
                LocalTime.parse(parts[1].trim(), SLOT_TIME_FORMAT)
            };
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    static String format(LocalTime start, LocalTime end) {
        return SLOT_TIME_FORMAT.format(start) + " - " + SLOT_TIME_FORMAT.format(end);
    }

    static long minutesOf(LocalTime[] range) {
        return java.time.Duration.between(range[0], range[1]).toMinutes();
    }

    // Appointments of `minutes` from start to end, skipping the lunch break, times
    // at or before `notAfter` (pass null unless the date is today) and anything that
    // overlaps `existing`. A leftover shorter than one appointment is dropped.
    static Plan plan(LocalTime start, LocalTime end, int minutes, List<LocalTime[]> existing, LocalTime notAfter) {
        List<String> slots = new ArrayList<>();
        int skippedLunch = 0, skippedPast = 0, skippedOverlap = 0;
        // The second check stops the loop if adding minutes would wrap past midnight.
        for (LocalTime t = start; !t.plusMinutes(minutes).isAfter(end) && t.plusMinutes(minutes).isAfter(t); t = t.plusMinutes(minutes)) {
            LocalTime slotStart = t;
            LocalTime slotEnd = t.plusMinutes(minutes);
            if (slotStart.isBefore(LUNCH_END) && slotEnd.isAfter(LUNCH_START)) { skippedLunch++; continue; }
            if (notAfter != null && !slotStart.isAfter(notAfter)) { skippedPast++; continue; }
            if (existing.stream().anyMatch(r -> slotStart.isBefore(r[1]) && slotEnd.isAfter(r[0]))) { skippedOverlap++; continue; }
            slots.add(format(slotStart, slotEnd));
        }
        return new Plan(slots, skippedLunch, skippedPast, skippedOverlap);
    }
}
