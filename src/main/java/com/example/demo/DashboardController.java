package com.example.demo;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class DashboardController {

    private final AppointmentRepository appointmentRepository;

    public DashboardController(AppointmentRepository appointmentRepository) {
        this.appointmentRepository = appointmentRepository;
    }


    // REQ 63 requires Admin to see aggregate statistics only, never individual patient
    // records (name, reason, urgency per student). Only the summary endpoints below
    // are exposed to the ADMIN role.

    @GetMapping("/dashboard/summary")
    public DashboardSummary getSummary() {
        return buildSummary(appointmentRepository.findAll());
    }

    // filter by date range; invalid date range must be rejected
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

        // Caseload is attributed by who actually treated the patient
        // (consultedByDoctor), not by doctorName - no doctor is assigned to an
        // appointment until a doctor picks it up and completes the consultation.
        Map<String, Long> byDoctor = new LinkedHashMap<>();
        for (Appointment a : active) {
            String doctor = a.getConsultedByDoctor() != null ? a.getConsultedByDoctor() : "Unassigned";
            byDoctor.merge(doctor, 1L, (oldVal, one) -> oldVal + one);
        }

        return new DashboardSummary(total, noShows, noShowRate, byReason, byDoctor);
    }
}
