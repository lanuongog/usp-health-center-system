package com.example.demo;

import org.springframework.web.bind.annotation.*;
import org.springframework.format.annotation.DateTimeFormat;
import java.time.LocalDate;
import java.util.*;

@RestController
public class DashboardController {

    private final AppointmentRepository appointmentRepository;

    public DashboardController(AppointmentRepository appointmentRepository) {
        this.appointmentRepository = appointmentRepository;
    }

    // NOTE: A raw "list all appointments" endpoint was intentionally removed from here.
    // REQ 63 requires Admin to see aggregate statistics only, never individual patient
    // records (name, reason, urgency per student). Only the summary endpoints below
    // are exposed to the ADMIN role.

    @PostMapping("/appointments")
    public Appointment createAppointment(@RequestBody Appointment appointment) {
        return appointmentRepository.save(appointment);
    }

    @GetMapping("/dashboard/summary")
    public DashboardSummary getSummary() {
        return buildSummary(appointmentRepository.findAll());
    }

    // FR-05-62: filter by date range; FR-05 error table: invalid date range must be rejected
    @GetMapping("/dashboard/summary/filter")
    public DashboardSummary getFilteredSummary(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        if (startDate.isAfter(endDate)) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.BAD_REQUEST,
                    "Invalid date range: start date must be on or before the end date");
        }
        List<Appointment> filtered = appointmentRepository.findByAppointmentDateBetween(startDate, endDate);
        return buildSummary(filtered);
    }

    private DashboardSummary buildSummary(List<Appointment> appointments) {
        List<Appointment> active = appointments.stream()
                .filter(a -> !"CANCELLED".equalsIgnoreCase(a.getStatus()))
                .toList();

        long total = active.size();
        long noShows = active.stream().filter(a -> "NO_SHOW".equals(a.getStatus())).count();
        double noShowRate = total == 0 ? 0 : (double) noShows / total * 100;

        Map<String, Long> byReason = new LinkedHashMap<>();
        for (Appointment a : active) {
            byReason.merge(a.getVisitReason(), 1L, (oldVal, one) -> oldVal + one);
        }

        Map<String, Long> byDoctor = new LinkedHashMap<>();
        for (Appointment a : active) {
            byDoctor.merge(a.getDoctorName(), 1L, (oldVal, one) -> oldVal + one);
        }

        return new DashboardSummary(total, noShows, noShowRate, byReason, byDoctor);
    }
}
