package com.example.demo;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

// Visit reasons are free text typed by students, so one student's unusual wording
// could appear as its own row in an admin statistic and point to a single person.
// Admins are meant to see aggregate numbers only, so a reason is shown by name only
// when at least MIN_GROUP_SIZE visits share it (ignoring case and extra spaces);
// all the rarer ones are combined into one "Other" row.
final class VisitReasonGrouper {

    static final int MIN_GROUP_SIZE = 3;
    static final String OTHER_LABEL = "Other (reasons given fewer than " + MIN_GROUP_SIZE + " times)";
    static final String NOT_SPECIFIED_LABEL = "Not specified";

    private VisitReasonGrouper() {}

    static Map<String, Long> group(List<Appointment> appointments) {
        // normalized text -> [display text, count]
        Map<String, Object[]> counts = new LinkedHashMap<>();
        long notSpecified = 0;
        for (Appointment a : appointments) {
            String raw = a.getVisitReason();
            if (raw == null || raw.isBlank()) {
                notSpecified++;
                continue;
            }
            String display = raw.trim().replaceAll("\\s+", " ");
            String key = display.toLowerCase(Locale.ROOT);
            Object[] entry = counts.computeIfAbsent(key, k -> new Object[] { display, 0L });
            entry[1] = (Long) entry[1] + 1;
        }

        Map<String, Long> result = new LinkedHashMap<>();
        long other = 0;
        for (Object[] entry : counts.values()) {
            long count = (Long) entry[1];
            if (count >= MIN_GROUP_SIZE) {
                result.put((String) entry[0], count);
            } else {
                other += count;
            }
        }
        if (other > 0) result.put(OTHER_LABEL, other);
        if (notSpecified > 0) result.put(NOT_SPECIFIED_LABEL, notSpecified);
        return result;
    }
}
