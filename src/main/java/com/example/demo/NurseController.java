package com.example.demo;
import org.springframework.transaction.annotation.Transactional;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/nurse")
public class NurseController {

    private static final List<String> URGENCY_LEVELS = List.of("Routine", "Urgent", "Emergency");

    private final AppointmentRepository appointmentRepository;
    private final CheckInRepository checkInRepository;
    private final DoctorScheduleRepository scheduleRepository;
    private final UserRepository userRepository;
    private final AuditLogService auditLogService;

    public NurseController(AppointmentRepository appointmentRepository,
                          CheckInRepository checkInRepository,
                          DoctorScheduleRepository scheduleRepository,
                          UserRepository userRepository,
                          AuditLogService auditLogService) {
        this.appointmentRepository = appointmentRepository;
        this.checkInRepository = checkInRepository;
        this.scheduleRepository = scheduleRepository;
        this.userRepository = userRepository;
        this.auditLogService = auditLogService;
    }

    // Lower number = seen sooner - an emergency case should visibly jump
    // ahead of routine bookings in the waiting queue.
    private int urgencyRank(String urgencyLevel) {
        if (urgencyLevel == null) return 3;
        switch (urgencyLevel) {
            case "Emergency": return 0;
            case "Urgent": return 1;
            case "Routine": return 2;
            default: return 3;
        }
    }

    // view students with scheduled appointments who have not yet been checked in
    @GetMapping("/queue")
    public List<Appointment> getQueue() {
        return appointmentRepository.findByStatus("BOOKED").stream()
                .sorted(Comparator.comparingInt((Appointment a) -> urgencyRank(a.getUrgencyLevel())))
                .collect(Collectors.toList());
    }

    // every appointment record, latest check-in first (see RecordsOrder), enriched
    // with the student's full name, so a nurse can search for a specific student and
    // see when their appointment was and check them in even after a no-show mark
    @GetMapping("/all")
    public List<Map<String, Object>> getAllAppointments() {
        Map<Long, CheckIn> checkIns = RecordsOrder.checkInsByAppointment(checkInRepository);
        List<Appointment> appointments = RecordsOrder.latestCheckInFirst(appointmentRepository.findAll(), checkIns);

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
            map.put("status", a.getStatus());

            // Who checked this student in, for the record. Normally this is the
            // nurse who logged vitals. But a doctor can complete a consultation on
            // a still-BOOKED appointment directly (found via Scheduled Bookings),
            // skipping the nurse queue entirely - no CheckIn row exists for that
            // visit, so fall back to attributing it to the treating doctor and
            // flag it as a direct visit rather than presenting it as a real check-in.
            CheckIn checkIn = checkIns.get(a.getId());
            String nurseCheckedInBy = checkIn != null ? checkIn.getCheckedInBy() : null;
            map.put("checkedInAt", checkIn != null && checkIn.getCheckInTime() != null ? checkIn.getCheckInTime().toString() : null);
            if (nurseCheckedInBy != null) {
                map.put("checkedInBy", fullNamesByUsername.getOrDefault(nurseCheckedInBy, nurseCheckedInBy));
                map.put("checkedInDirect", false);
            } else if ("COMPLETED".equals(a.getStatus()) && a.getConsultedByDoctor() != null) {
                map.put("checkedInBy", fullNamesByUsername.getOrDefault(a.getConsultedByDoctor(), a.getConsultedByDoctor()));
                map.put("checkedInDirect", true);
            } else {
                map.put("checkedInBy", null);
                map.put("checkedInDirect", false);
            }

            return map;
        }).collect(Collectors.toList());
    }

    public static class CheckInRequest {
        public Long appointmentId;
        public String temperature;
        public String bloodPressure;
        public String presentingComplaint;
    }

    // check in a booked student, record vitals + complaint,
    // prevent the same student being checked in twice for the same visit
    @Transactional
    @PostMapping("/checkin")
    public CheckIn checkInStudent(@RequestBody CheckInRequest request, Authentication auth) {
        if (request == null || request.appointmentId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Appointment ID is required.");
        }
        Appointment appointment = appointmentRepository.findById(request.appointmentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Student record not found"));

        // prevent duplicate check-in for the same visit
        if (checkInRepository.findByAppointmentId(appointment.getId()).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This student has already been checked in");
        }
        // a student who was marked as a no-show can still be checked in if they
        // turn up after all, in addition to the normal booked-and-waiting case
        if (!"BOOKED".equals(appointment.getStatus()) && !"NO_SHOW".equals(appointment.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "No valid appointment found");
        }

        // a future-dated appointment cannot be checked in yet
        if (appointment.getAppointmentDate() != null && appointment.getAppointmentDate().isAfter(LocalDate.now())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "This appointment is scheduled for a future date and cannot be checked in yet.");
        }

        // required fields must be present
        if (isBlank(request.temperature) || isBlank(request.bloodPressure) || isBlank(request.presentingComplaint)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Display validation messages for incomplete vital information");
        }
        if (request.presentingComplaint.trim().length() > MAX_COMPLAINT_LENGTH) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Presenting complaint must be " + MAX_COMPLAINT_LENGTH + " characters or fewer.");
        }
        String complaintError = InputRules.freeText(request.presentingComplaint, "Presenting complaint");
        if (complaintError != null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, complaintError);
        }

        // reject out-of-range or malformed vital sign values
        validateVitals(request.temperature, request.bloodPressure);

        CheckIn checkIn = new CheckIn();
        checkIn.setAppointment(appointment);
        checkIn.setTemperature(request.temperature);
        checkIn.setBloodPressure(request.bloodPressure);
        checkIn.setPresentingComplaint(request.presentingComplaint);
        checkIn.setCheckInTime(LocalDateTime.now());
        checkIn.setCheckedInBy(auth.getName());

        appointment.setStatus("CHECKED_IN");
        Appointment saved = appointmentRepository.save(appointment);

        // NOTE: do NOT free the schedule slot here. The slot must stay marked
        // booked for the entire time the student is being seen (check-in through
        // consultation) - freeing it early let a second student book the exact
        // same doctor/date/time slot while the first one was still in progress
        // (DEF-02). The slot is only freed on cancellation/reschedule
        // (BookingController) or a no-show (markNoShow below).

        CheckIn savedCheckIn = checkInRepository.save(checkIn);
        auditLogService.recordAccess(auth, "RECORD_CHECKIN_VITALS", appointment.getStudentName(),
                "Recorded vitals for appointment #" + appointment.getId());
        return savedCheckIn;
    }

    // Tries the ID as typed first, then upper-cased - new registrations are
    // normalized to SXXXXXXXX, but legacy accounts predate that format.
    private User findStudentById(String rawStudentId) {
        if (rawStudentId == null || rawStudentId.isBlank()) return null;
        String trimmed = rawStudentId.trim();
        User user = userRepository.findByUsername(trimmed)
                .filter(u -> "STUDENT".equals(u.getRole()))
                .orElse(null);
        if (user == null) {
            user = userRepository.findByUsername(trimmed.toUpperCase())
                    .filter(u -> "STUDENT".equals(u.getRole()))
                    .orElse(null);
        }
        return user;
    }

    public static class WalkInCheckInRequest {
        public String studentId;
        public String temperature;
        public String bloodPressure;
        public String presentingComplaint;
        public String urgencyLevel;
    }

    // Registers a visit on the spot for a student who arrives without a prior
    // booking, and checks them in with vitals in the same step - the same
    // combined check-in+vitals pattern used for booked students above, just
    // without an existing Appointment/schedule slot to attach to.
    @Transactional
    @PostMapping("/walk-in-checkin")
    public Object walkInCheckIn(@RequestBody WalkInCheckInRequest request, Authentication auth) {
        if (request.studentId == null || request.studentId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Student ID is required.");
        }
        User student = findStudentById(request.studentId);
        if (student == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Student record not found");
        }
        String studentId = student.getUsername();

        if (!student.isActive()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "This student account is deactivated.");
        }

        boolean hasActiveVisit = appointmentRepository.findByStudentName(studentId).stream()
                .anyMatch(a -> "BOOKED".equals(a.getStatus()) || "CHECKED_IN".equals(a.getStatus()));
        if (hasActiveVisit) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This student already has an active visit");
        }

        if (isBlank(request.temperature) || isBlank(request.bloodPressure) || isBlank(request.presentingComplaint)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Display validation messages for the missing information");
        }
        if (request.presentingComplaint.trim().length() > MAX_COMPLAINT_LENGTH) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Presenting complaint must be " + MAX_COMPLAINT_LENGTH + " characters or fewer.");
        }
        String complaintError = InputRules.freeText(request.presentingComplaint, "Presenting complaint");
        if (complaintError != null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, complaintError);
        }
        if (request.urgencyLevel == null || !URGENCY_LEVELS.contains(request.urgencyLevel)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Urgency level must be one of: " + String.join(", ", URGENCY_LEVELS) + ".");
        }

        validateVitals(request.temperature, request.bloodPressure);

        Appointment appointment = new Appointment();
        appointment.setStudentName(studentId);
        appointment.setAppointmentDate(LocalDate.now());
        appointment.setTimeSlot("Walk-in");
        appointment.setVisitReason(request.presentingComplaint.trim());
        appointment.setUrgencyLevel(request.urgencyLevel);
        appointment.setStatus("CHECKED_IN");
        Appointment savedAppointment = appointmentRepository.save(appointment);

        CheckIn checkIn = new CheckIn();
        checkIn.setAppointment(savedAppointment);
        checkIn.setTemperature(request.temperature);
        checkIn.setBloodPressure(request.bloodPressure);
        checkIn.setPresentingComplaint(request.presentingComplaint);
        checkIn.setCheckInTime(LocalDateTime.now());
        checkIn.setCheckedInBy(auth.getName());
        checkInRepository.save(checkIn);

        auditLogService.recordAccess(auth, "WALK_IN_CHECKIN", studentId,
                "Walk-in check-in for appointment #" + savedAppointment.getId());

        return Map.of(
                "message", "Walk-in check-in complete. The student has been added to the doctor's queue.",
                "appointmentId", savedAppointment.getId()
        );
    }

    // today's completed check-ins, enriched with the student's and nurse's
    // full names so a doctor can see at a glance who checked a student in
    @GetMapping("/checkins")
    public List<Map<String, Object>> getAllCheckIns() {
        LocalDate today = LocalDate.now();

        Map<String, String> fullNamesByUsername = userRepository.findAll().stream()
                .filter(u -> u.getFullName() != null && !u.getFullName().isBlank())
                .collect(Collectors.toMap(User::getUsername, User::getFullName, (a, b) -> a));

        return checkInRepository.findAll().stream()
                .filter(c -> c.getCheckInTime() != null && c.getCheckInTime().toLocalDate().equals(today))
                .sorted(Comparator.comparing(CheckIn::getCheckInTime).reversed())
                .map(c -> {
                    String studentUsername = c.getAppointment() != null ? c.getAppointment().getStudentName() : null;
                    Map<String, Object> map = new LinkedHashMap<>();
                    map.put("id", c.getId());
                    map.put("studentName", fullNamesByUsername.getOrDefault(studentUsername, studentUsername));
                    map.put("temperature", c.getTemperature());
                    map.put("bloodPressure", c.getBloodPressure());
                    map.put("presentingComplaint", c.getPresentingComplaint());
                    map.put("checkInTime", c.getCheckInTime() != null ? c.getCheckInTime().toString() : null);
                    map.put("checkedInBy", fullNamesByUsername.getOrDefault(c.getCheckedInBy(), c.getCheckedInBy()));
                    return map;
                })
                .collect(Collectors.toList());
    }

    public static class NoShowRequest {
        public Long appointmentId;
    }

    // mark a booked student who never arrived as a no-show, removing them from
    // the check-in queue and freeing the schedule slot for other students
    @PostMapping("/no-show")
    public Appointment markNoShow(@RequestBody NoShowRequest request) {
        if (request == null || request.appointmentId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Appointment ID is required.");
        }
        Appointment appointment = appointmentRepository.findById(request.appointmentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Student record not found"));

        if (!"BOOKED".equals(appointment.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "No valid appointment found");
        }

        if (appointment.getAppointmentDate() != null && appointment.getAppointmentDate().isAfter(LocalDate.now())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "A future appointment cannot be marked as a no-show.");
        }

        appointment.setStatus("NO_SHOW");
        Appointment saved = appointmentRepository.save(appointment);
        scheduleRepository.freeSlot(saved.getScheduleSlotId());

        return saved;
    }

    private boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }

    private static final int MAX_COMPLAINT_LENGTH = 500;

    // a plausible human body temperature in Celsius
    private static final double MIN_TEMP_C = 30.0;
    private static final double MAX_TEMP_C = 43.0;

    // plausible systolic/diastolic blood pressure bounds (mmHg)
    private static final int MIN_SYSTOLIC = 60;
    private static final int MAX_SYSTOLIC = 250;
    private static final int MIN_DIASTOLIC = 40;
    private static final int MAX_DIASTOLIC = 150;

    private void validateVitals(String temperature, String bloodPressure) {
        double temp;
        try {
            temp = Double.parseDouble(temperature.trim());
        } catch (NumberFormatException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Temperature must be a number in degrees Celsius (e.g. 37.2)");
        }
        if (temp < MIN_TEMP_C || temp > MAX_TEMP_C) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Temperature is out of the valid range (" + MIN_TEMP_C + "–" + MAX_TEMP_C + "°C)");
        }

        String[] parts = bloodPressure.trim().split("/");
        if (parts.length != 2) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Blood pressure must be in the format systolic/diastolic (e.g. 120/80)");
        }

        int systolic;
        int diastolic;
        try {
            systolic = Integer.parseInt(parts[0].trim());
            diastolic = Integer.parseInt(parts[1].trim());
        } catch (NumberFormatException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Blood pressure must be in the format systolic/diastolic (e.g. 120/80)");
        }

        boolean outOfRange = systolic < MIN_SYSTOLIC || systolic > MAX_SYSTOLIC
                || diastolic < MIN_DIASTOLIC || diastolic > MAX_DIASTOLIC;
        if (outOfRange || systolic <= diastolic) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Blood pressure values are out of the valid range");
        }
    }
}
