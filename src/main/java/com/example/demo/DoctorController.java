package com.example.demo;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/doctor")
public class DoctorController {

    private static final int MAX_DIAGNOSIS_LENGTH = 200;
    private static final int MAX_NOTES_LENGTH = 2000;

    private final AppointmentRepository appointmentRepository;
    private final CheckInRepository checkInRepository;
    private final DoctorScheduleRepository scheduleRepository;
    private final UserRepository userRepository;
    private final AuditLogService auditLogService;
    private final ConsultationReportPdfService reportPdfService;

    public DoctorController(AppointmentRepository appointmentRepository,
                           CheckInRepository checkInRepository,
                           DoctorScheduleRepository scheduleRepository,
                           UserRepository userRepository,
                           AuditLogService auditLogService,
                           ConsultationReportPdfService reportPdfService) {
        this.appointmentRepository = appointmentRepository;
        this.checkInRepository = checkInRepository;
        this.scheduleRepository = scheduleRepository;
        this.userRepository = userRepository;
        this.auditLogService = auditLogService;
        this.reportPdfService = reportPdfService;
    }

    // Lower number = seen sooner
    private int urgencyRank(String urgencyLevel) {
        if (urgencyLevel == null) return 3;
        switch (urgencyLevel) {
            case "Emergency": return 0;
            case "Urgent": return 1;
            case "Routine": return 2;
            default: return 3;
        }
    }

    private LocalDateTime getCheckInTime(Long appointmentId) {
        return checkInRepository.findAll().stream()
                .filter(c -> c.getAppointment() != null && c.getAppointment().getId().equals(appointmentId))
                .map((CheckIn c) -> c.getCheckInTime())
                .findFirst()
                .orElse(LocalDateTime.MAX);
    }

    private String getCheckedInBy(Long appointmentId) {
        return checkInRepository.findByAppointmentId(appointmentId)
                .map(CheckIn::getCheckedInBy)
                .orElse(null);
    }

    // checked-in students, ordered by urgency then check-in time, with the
    // checking-in nurse's full name so the doctor can see who checked them in
    @GetMapping("/queue")
    public List<Map<String, Object>> getQueue() {
        List<Appointment> checkedIn = appointmentRepository.findByStatus("CHECKED_IN").stream()
                .sorted(Comparator
                        .comparingInt((Appointment a) -> urgencyRank(a.getUrgencyLevel()))
                        .thenComparing(a -> getCheckInTime(a.getId())))
                .collect(Collectors.toList());

        Map<String, String> fullNamesByUsername = userRepository.findAll().stream()
                .filter(u -> u.getFullName() != null && !u.getFullName().isBlank())
                .collect(Collectors.toMap(User::getUsername, User::getFullName, (a, b) -> a));

        return checkedIn.stream().map(a -> {
            String checkedInByUsername = getCheckedInBy(a.getId());
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("id", a.getId());
            map.put("studentId", a.getStudentName());
            map.put("studentName", fullNamesByUsername.getOrDefault(a.getStudentName(), a.getStudentName()));
            map.put("appointmentDate", a.getAppointmentDate() != null ? a.getAppointmentDate().toString() : "");
            map.put("timeSlot", a.getTimeSlot());
            map.put("visitReason", a.getVisitReason());
            map.put("urgencyLevel", a.getUrgencyLevel());
            map.put("status", a.getStatus());
            map.put("consultedByDoctor", a.getConsultedByDoctor());
            map.put("checkedInBy", fullNamesByUsername.getOrDefault(checkedInByUsername, checkedInByUsername));
            return map;
        }).collect(Collectors.toList());
    }

    // View all booked / scheduled appointments that have not yet been checked in by nurse
    @GetMapping("/booked")
    public List<Appointment> getBookedAppointments() {
        return appointmentRepository.findByStatus("BOOKED");
    }

    // View all appointments for the doctor dashboard overview, most recently
    // booked/scheduled dates first, enriched with the student's full name
    // (Appointment.studentName holds the student ID used to log in).
    @GetMapping("/all")
    public List<Map<String, Object>> getAllAppointments() {
        List<Appointment> appointments = appointmentRepository.findAll().stream()
                .sorted(Comparator
                        .comparing((Appointment a) -> a.getAppointmentDate(), Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(a -> a.getId(), Comparator.reverseOrder()))
                .collect(Collectors.toList());

        Map<String, String> fullNamesByUsername = userRepository.findAll().stream()
                .filter(u -> u.getFullName() != null && !u.getFullName().isBlank())
                .collect(Collectors.toMap(User::getUsername, User::getFullName, (a, b) -> a));

        return appointments.stream().map(a -> {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("id", a.getId());
            map.put("studentId", a.getStudentName());
            map.put("studentName", fullNamesByUsername.getOrDefault(a.getStudentName(), a.getStudentName()));
            map.put("appointmentDate", a.getAppointmentDate() != null ? a.getAppointmentDate().toString() : "");
            map.put("timeSlot", a.getTimeSlot());
            map.put("visitReason", a.getVisitReason());
            map.put("urgencyLevel", a.getUrgencyLevel());
            map.put("status", a.getStatus());
            map.put("diagnosis", a.getDiagnosis());
            map.put("consultationNotes", a.getConsultationNotes());
            map.put("consultedByDoctor", a.getConsultedByDoctor());
            return map;
        }).collect(Collectors.toList());
    }

    // the nurse's recorded vitals and presenting complaint for the current visit
    @GetMapping("/checkin/{appointmentId}")
    public Map<String, Object> getCheckIn(@PathVariable Long appointmentId, Authentication auth) {
        CheckIn checkIn = checkInRepository.findByAppointmentId(appointmentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "This student has not been checked in yet"));

        auditLogService.recordAccess(auth, "VIEW_CHECKIN_VITALS",
                checkIn.getAppointment() != null ? checkIn.getAppointment().getStudentName() : null,
                "Viewed check-in vitals for appointment #" + appointmentId);

        String checkedInByUsername = checkIn.getCheckedInBy();
        String checkedInByFullName = userRepository.findByUsername(checkedInByUsername)
                .map(User::getFullName)
                .filter(name -> name != null && !name.isBlank())
                .orElse(checkedInByUsername);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("temperature", checkIn.getTemperature());
        result.put("bloodPressure", checkIn.getBloodPressure());
        result.put("presentingComplaint", checkIn.getPresentingComplaint());
        result.put("checkInTime", checkIn.getCheckInTime() != null ? checkIn.getCheckInTime().toString() : null);
        result.put("checkedInBy", checkedInByFullName);
        return result;
    }

    // a student's visit history
    @GetMapping("/history/{studentName}")
    public List<Appointment> getMedicalHistory(@PathVariable String studentName, Authentication auth) {
        List<Appointment> history = appointmentRepository.findByStudentName(studentName);
        if (history.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Medical record not found");
        }
        auditLogService.recordAccess(auth, "VIEW_MEDICAL_HISTORY", studentName,
                "Viewed full medical history (" + history.size() + " visit(s))");
        return history;
    }

    public static class ConsultationRequest {
        public Long appointmentId;
        public String diagnosis;
        public String consultationNotes;
    }

    // record diagnosis/notes, mark completed, exit the queue
    @Transactional
    @PostMapping("/consultation")
    public Appointment recordConsultation(@RequestBody ConsultationRequest request, Authentication auth) {
        Appointment appointment = appointmentRepository.findById(request.appointmentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Medical record not found"));

        // a consultation can only be recorded for a patient the nurse has checked in
        if (!"CHECKED_IN".equals(appointment.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "This patient is not checked in, or the consultation is already completed.");
        }

        // required consultation information is missing
        if (isBlank(request.diagnosis) || isBlank(request.consultationNotes)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Display validation messages for incomplete fields");
        }
        if (request.diagnosis.trim().length() > MAX_DIAGNOSIS_LENGTH) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Diagnosis must be " + MAX_DIAGNOSIS_LENGTH + " characters or fewer.");
        }
        if (request.consultationNotes.trim().length() > MAX_NOTES_LENGTH) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Consultation notes must be " + MAX_NOTES_LENGTH + " characters or fewer.");
        }
        String dxError = InputRules.freeText(request.diagnosis, "Diagnosis");
        if (dxError == null) dxError = InputRules.freeText(request.consultationNotes, "Consultation notes");
        if (dxError != null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, dxError);
        }

        // prevent a doctor from editing another doctor's submitted consultation
        if (appointment.getConsultedByDoctor() != null &&
                !appointment.getConsultedByDoctor().equals(auth.getName())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "This consultation was already recorded by another doctor.");
        }

        appointment.setDiagnosis(request.diagnosis);
        appointment.setConsultationNotes(request.consultationNotes);
        appointment.setConsultedByDoctor(auth.getName());
        appointment.setStatus("COMPLETED");

        Appointment saved = appointmentRepository.save(appointment);

        // NOTE: do NOT free the schedule slot here either - same reasoning as
        // NurseController.checkInStudent (DEF-02). Once a visit has actually
        // happened for this dated/timed slot it should stay marked as used, not
        // reopen for a new booking. Slots are only freed on cancellation/
        // reschedule (BookingController) or a no-show (NurseController.markNoShow).

        auditLogService.recordAccess(auth, "RECORD_CONSULTATION", saved.getStudentName(),
                "Recorded diagnosis/notes for appointment #" + saved.getId());

        return saved;
    }

    // Consolidated single-visit clinical summary: reason/urgency, nurse's
    // vitals & presenting complaint, doctor's diagnosis & notes, and the
    // relevant timestamps, in one report an authorised doctor can view or
    // print. Only available once the consultation is actually completed.
    @GetMapping("/consultation-report/{appointmentId}")
    public Map<String, Object> generateConsultationReport(@PathVariable Long appointmentId, Authentication auth) {
        Appointment appointment = appointmentRepository.findById(appointmentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Visit record not found"));

        if (!"COMPLETED".equals(appointment.getStatus()) || isBlank(appointment.getDiagnosis()) || isBlank(appointment.getConsultationNotes())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unable to generate report");
        }

        // restrict consultation reports to the doctor who actually saw this
        // patient, or an admin - same ownership pattern as ScheduleController.
        // validateOwnership(). Missing before (DEF-01): any authenticated doctor
        // could read any other doctor's patient's full consultation report just
        // by knowing/guessing the appointment ID.
        boolean isAdmin = auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        if (!isAdmin && !auth.getName().equals(appointment.getConsultedByDoctor())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "You do not have permission to view this consultation report");
        }

        auditLogService.recordAccess(auth, "VIEW_CONSULTATION_REPORT", appointment.getStudentName(),
                "Generated consultation report for appointment #" + appointmentId);

        String studentFullName = userRepository.findByUsername(appointment.getStudentName())
                .map(User::getFullName)
                .filter(name -> name != null && !name.isBlank())
                .orElse(appointment.getStudentName());

        Map<String, Object> report = new LinkedHashMap<>();
        report.put("appointmentId", appointment.getId());
        report.put("studentId", appointment.getStudentName());
        report.put("studentName", studentFullName);
        report.put("appointmentDate", appointment.getAppointmentDate() != null ? appointment.getAppointmentDate().toString() : "");
        report.put("timeSlot", appointment.getTimeSlot());
        report.put("visitReason", appointment.getVisitReason());
        report.put("urgencyLevel", appointment.getUrgencyLevel());
        report.put("diagnosis", appointment.getDiagnosis());
        report.put("consultationNotes", appointment.getConsultationNotes());
        report.put("consultedByDoctor", appointment.getConsultedByDoctor());

        checkInRepository.findByAppointmentId(appointmentId).ifPresent(c -> {
            String checkedInByFullName = userRepository.findByUsername(c.getCheckedInBy())
                    .map(User::getFullName)
                    .filter(name -> name != null && !name.isBlank())
                    .orElse(c.getCheckedInBy());
            report.put("temperature", c.getTemperature());
            report.put("bloodPressure", c.getBloodPressure());
            report.put("presentingComplaint", c.getPresentingComplaint());
            report.put("checkInTime", c.getCheckInTime() != null ? c.getCheckInTime().toString() : null);
            report.put("checkedInBy", checkedInByFullName);
        });

        report.put("generatedAt", LocalDateTime.now().toString());
        return report;
    }

    // Same report as a downloadable PDF file. Goes through the JSON report
    // above, so the completed-visit check and the audit log entry still apply.
    @GetMapping("/consultation-report/{appointmentId}/pdf")
    public ResponseEntity<byte[]> downloadConsultationReport(@PathVariable Long appointmentId, Authentication auth) {
        Map<String, Object> report = generateConsultationReport(appointmentId, auth);
        byte[] pdf;
        try {
            pdf = reportPdfService.render(report);
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Unable to generate report");
        }
        String filename = "consultation-report-A" + appointmentId + "-" + report.get("studentId") + ".pdf";
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(filename).build().toString())
                .body(pdf);
    }

    private boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }
}