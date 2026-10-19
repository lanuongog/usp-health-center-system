package com.example.demo;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

// Doctor absences for the nurse check-in workspace and the admin dashboard:
// which doctor is off on which day, and which students were affected, with
// their current contact details and whether someone has reached them yet.
// Nurse/admin only (see SecurityConfig).
@RestController
@RequestMapping("/absences")
public class DoctorAbsenceController {

    private static final int MAX_RANGE_DAYS = 400;

    private final DoctorAbsenceRepository absenceRepository;
    private final DoctorAbsenceStudentRepository absenceStudentRepository;
    private final UserRepository userRepository;
    private final AuditLogService auditLogService;

    public DoctorAbsenceController(DoctorAbsenceRepository absenceRepository,
                                   DoctorAbsenceStudentRepository absenceStudentRepository,
                                   UserRepository userRepository,
                                   AuditLogService auditLogService) {
        this.absenceRepository = absenceRepository;
        this.absenceStudentRepository = absenceStudentRepository;
        this.userRepository = userRepository;
        this.auditLogService = auditLogService;
    }

    @GetMapping
    public List<Map<String, Object>> list(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        if (to.isBefore(from) || to.isAfter(from.plusDays(MAX_RANGE_DAYS))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Choose a valid date range.");
        }
        List<DoctorAbsence> absences = absenceRepository.findByAbsenceDateBetweenOrderByAbsenceDateAscDoctorNameAsc(from, to);
        Map<Long, List<DoctorAbsenceStudent>> studentsByAbsence = absences.isEmpty() ? Map.of()
                : absenceStudentRepository.findByAbsenceIdInOrderByTimeSlotAsc(absences.stream().map(DoctorAbsence::getId).toList())
                        .stream().collect(Collectors.groupingBy(DoctorAbsenceStudent::getAbsenceId));

        return absences.stream().map(a -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", a.getId());
            row.put("doctorName", a.getDoctorName());
            row.put("date", a.getAbsenceDate().toString());
            row.put("markedBy", a.getMarkedBy());
            row.put("markedAt", a.getMarkedAt() != null ? a.getMarkedAt().toString() : null);
            row.put("removedSlots", a.getRemovedSlots());
            row.put("cancelledCount", a.getCancelledCount());
            row.put("keptCheckedIn", a.getKeptCheckedIn());
            row.put("students", studentsByAbsence.getOrDefault(a.getId(), List.of()).stream().map(this::studentRow).toList());
            return row;
        }).toList();
    }

    public static class ContactedRequest {
        public boolean contacted;
    }

    @PostMapping("/students/{id}/contacted")
    public Map<String, Object> setContacted(@PathVariable Long id, @RequestBody ContactedRequest request, Authentication auth) {
        DoctorAbsenceStudent s = absenceStudentRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Student not found on this absence."));
        s.setContacted(request.contacted);
        s.setContactedBy(request.contacted ? auth.getName() : null);
        s.setContactedAt(request.contacted ? LocalDateTime.now() : null);
        absenceStudentRepository.save(s);
        auditLogService.recordAccess(auth, request.contacted ? "ABSENCE_STUDENT_CONTACTED" : "ABSENCE_STUDENT_CONTACT_UNDONE",
                s.getStudentId(), "Appointment #" + s.getAppointmentId());
        return studentRow(s);
    }

    // Phone and email are read from the student's profile now, not copied at
    // cancellation time, so staff always call the latest number.
    private Map<String, Object> studentRow(DoctorAbsenceStudent s) {
        User u = userRepository.findByUsername(s.getStudentId()).orElse(null);
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", s.getId());
        row.put("studentId", s.getStudentId());
        row.put("fullName", s.getFullName());
        row.put("timeSlot", s.getTimeSlot());
        row.put("phoneNumber", u != null ? u.getPhoneNumber() : null);
        row.put("email", u != null ? u.getEmail() : null);
        row.put("contacted", s.isContacted());
        row.put("contactedBy", s.getContactedBy());
        row.put("contactedAt", s.getContactedAt() != null ? s.getContactedAt().toString() : null);
        return row;
    }
}
