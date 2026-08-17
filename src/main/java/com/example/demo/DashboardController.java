package com.example.demo;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.format.annotation.DateTimeFormat;
import java.time.LocalDate;
import java.util.*;

@RestController
public class DashboardController {

    @Autowired
    private AppointmentRepository appointmentRepository;

    @GetMapping("/dashboard")
    public List<Appointment> getAllAppointments() {
        return appointmentRepository.findAll();
    }

    @PostMapping("/appointments")
    public Appointment createAppointment(@RequestBody Appointment appointment) {
        return appointmentRepository.save(appointment);
    }

    @GetMapping("/dashboard/summary")
    public DashboardSummary getSummary() {
        return buildSummary(appointmentRepository.findAll());
    }

    @GetMapping("/dashboard/summary/filter")
    public DashboardSummary getFilteredSummary(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        List<Appointment> filtered = appointmentRepository.findByAppointmentDateBetween(startDate, endDate);
        return buildSummary(filtered);
    }

    private DashboardSummary buildSummary(List<Appointment> appointments) {
        long total = appointments.size();
        long noShows = appointments.stream().filter(a -> "NO_SHOW".equals(a.getStatus())).count();
        double noShowRate = total == 0 ? 0 : (double) noShows / total * 100;

        Map<String, Long> byReason = new LinkedHashMap<>();
        for (Appointment a : appointments) {
            byReason.merge(a.getVisitReason(), 1L, Long::sum);
        }

        Map<String, Long> byDoctor = new LinkedHashMap<>();
        for (Appointment a : appointments) {
            byDoctor.merge(a.getDoctorName(), 1L, Long::sum);
        }

        return new DashboardSummary(total, noShows, noShowRate, byReason, byDoctor);
    }
}
