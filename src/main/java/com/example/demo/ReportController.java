package com.example.demo;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/admin/reports")
public class ReportController {

    private final AppointmentRepository appointmentRepository;

    public ReportController(AppointmentRepository appointmentRepository) {
        this.appointmentRepository = appointmentRepository;
    }

    // available report types an administrator can choose from
    @GetMapping("/types")
    public List<String> getReportTypes() {
        return List.of("Appointment Summary", "Visit Reason Breakdown", "Doctor Workload");
    }

    public static class GeneratedReport {
        public String reportType;
        public String dateRangeStart;
        public String dateRangeEnd;
        public String generatedAt;
        public long totalAppointments;
        public Map<String, Long> data;
    }

    // select a report type + date range, generate it from aggregate data.
    // only ever aggregate counts are returned here - no student names, no
    // individual visit details - same privacy boundary as the rest of Admin Reporting.
    @GetMapping
    public GeneratedReport generateReport(
            @RequestParam String type,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {

        // date range is missing
        if (startDate == null || endDate == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Please provide both a start and end date for the report");
        }
        if (startDate.isAfter(endDate)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Invalid date range: start date must be on or before the end date");
        }

        List<Appointment> appointments = appointmentRepository.findByAppointmentDateBetween(startDate, endDate)
                .stream()
                .filter(a -> !"CANCELLED".equalsIgnoreCase(a.getStatus()))
                .toList();

        // no information is available
        if (appointments.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "No data available for the selected period");
        }

        Map<String, Long> data = new LinkedHashMap<>();
        try {
            switch (type) {
                case "Visit Reason Breakdown":
                    for (Appointment a : appointments) {
                        data.merge(a.getVisitReason(), 1L, (oldVal, one) -> oldVal + one);
                    }
                    break;
                case "Doctor Workload":
                    for (Appointment a : appointments) {
                        String doctor = a.getConsultedByDoctor() != null ? a.getConsultedByDoctor() : "Unassigned";
                        data.merge(doctor, 1L, (oldVal, one) -> oldVal + one);
                    }
                    break;
                case "Appointment Summary":
                    data.put("Total Appointments", (long) appointments.size());
                    data.put("Completed", appointments.stream().filter(a -> "COMPLETED".equals(a.getStatus())).count());
                    data.put("No-Shows", appointments.stream().filter(a -> "NO_SHOW".equals(a.getStatus())).count());
                    data.put("Checked In (in progress)", appointments.stream().filter(a -> "CHECKED_IN".equals(a.getStatus())).count());
                    data.put("Booked (awaiting check-in)", appointments.stream().filter(a -> "BOOKED".equals(a.getStatus())).count());
                    break;
                default:
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown report type");
            }
        } catch (ResponseStatusException e) {
            throw e; // preserve intended 400 "Unknown report type"
        } catch (Exception e) {
            // a genuine failure while building the report itself
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Unable to generate report");
        }

        GeneratedReport report = new GeneratedReport();
        report.reportType = type;
        report.dateRangeStart = startDate.toString();
        report.dateRangeEnd = endDate.toString();
        report.generatedAt = LocalDateTime.now().toString();
        report.totalAppointments = appointments.size();
        report.data = data;
        return report;
    }
}
