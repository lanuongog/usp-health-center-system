package com.example.demo;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

// Lets a doctor generate their own caseload/report over a date range, scoped
// to only the consultations they personally completed - unlike the
// clinic-wide aggregate reporting under /admin/reports.
@RestController
@RequestMapping("/doctor/reports")
public class DoctorReportController {

    private final AppointmentRepository appointmentRepository;

    public DoctorReportController(AppointmentRepository appointmentRepository) {
        this.appointmentRepository = appointmentRepository;
    }

    @GetMapping("/types")
    public List<String> getReportTypes() {
        return List.of("Caseload Summary", "Diagnosis Breakdown", "Visit Reason Breakdown", "Daily Consultation Volume");
    }

    public static class GeneratedReport {
        public String reportType;
        public String dateRangeStart;
        public String dateRangeEnd;
        public String generatedAt;
        public long totalConsultations;
        public Map<String, Long> data;
    }

    @GetMapping
    public GeneratedReport generateReport(
            @RequestParam String type,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            Authentication auth) {

        if (startDate == null || endDate == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Please provide both a start and end date for the report");
        }
        if (startDate.isAfter(endDate)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Invalid date range: start date must be on or before the end date");
        }
        if (java.time.temporal.ChronoUnit.DAYS.between(startDate, endDate) > 366) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Date range is too long. Please choose a range of one year or less.");
        }

        List<Appointment> consultations = appointmentRepository.findByConsultedByDoctor(auth.getName()).stream()
                .filter(a -> "COMPLETED".equals(a.getStatus()))
                .filter(a -> a.getAppointmentDate() != null
                        && !a.getAppointmentDate().isBefore(startDate)
                        && !a.getAppointmentDate().isAfter(endDate))
                .toList();

        if (consultations.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No completed consultations found for the selected period");
        }

        Map<String, Long> data = new LinkedHashMap<>();
        switch (type) {
            case "Caseload Summary" -> {
                data.put("Total Consultations", (long) consultations.size());
                data.put("Emergency Cases", consultations.stream().filter(a -> "Emergency".equals(a.getUrgencyLevel())).count());
                data.put("Urgent Cases", consultations.stream().filter(a -> "Urgent".equals(a.getUrgencyLevel())).count());
                data.put("Routine Cases", consultations.stream().filter(a -> "Routine".equals(a.getUrgencyLevel())).count());
                long distinctDaysSeen = consultations.stream().map(Appointment::getAppointmentDate).distinct().count();
                long averagePerActiveDay = distinctDaysSeen == 0 ? 0 : Math.round((double) consultations.size() / distinctDaysSeen);
                data.put("Days With Consultations", distinctDaysSeen);
                data.put("Average Per Active Day", averagePerActiveDay);
            }
            case "Diagnosis Breakdown" -> {
                for (Appointment a : consultations) {
                    String diagnosis = (a.getDiagnosis() == null || a.getDiagnosis().isBlank()) ? "Unspecified" : a.getDiagnosis().trim();
                    data.merge(diagnosis, 1L, Long::sum);
                }
            }
            case "Visit Reason Breakdown" -> {
                for (Appointment a : consultations) {
                    data.merge(a.getVisitReason(), 1L, Long::sum);
                }
            }
            case "Daily Consultation Volume" -> {
                Map<String, Long> byDate = new TreeMap<>();
                for (Appointment a : consultations) {
                    byDate.merge(a.getAppointmentDate().toString(), 1L, Long::sum);
                }
                data.putAll(byDate);
            }
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown report type");
        }

        GeneratedReport report = new GeneratedReport();
        report.reportType = type;
        report.dateRangeStart = startDate.toString();
        report.dateRangeEnd = endDate.toString();
        report.generatedAt = LocalDateTime.now().toString();
        report.totalConsultations = consultations.size();
        report.data = data;
        return report;
    }
}
