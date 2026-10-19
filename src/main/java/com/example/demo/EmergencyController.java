package com.example.demo;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// The dedicated action a nurse/doctor uses to check in a student who sent an
// emergency alert (see StudentController.sendEmergencyAlert) and has now
// physically arrived. Registers AND checks the student in as one step,
// landing them at the top of the doctor's active queue - the system is
// online-booking-only, so this is purely how staff act on an emergency raised
// through the student portal, not a general walk-in registration path.
@RestController
@RequestMapping("/emergency")
public class EmergencyController {

    private static final int MAX_REASON_LENGTH = 300;

    private final UserRepository userRepository;
    private final AppointmentRepository appointmentRepository;
    private final CheckInRepository checkInRepository;
    private final NotificationRepository notificationRepository;
    private final DelayNoticeService delayNoticeService;
    private final AuditLogService auditLogService;

    public EmergencyController(UserRepository userRepository,
                                AppointmentRepository appointmentRepository,
                                CheckInRepository checkInRepository,
                                NotificationRepository notificationRepository,
                                DelayNoticeService delayNoticeService,
                                AuditLogService auditLogService) {
        this.userRepository = userRepository;
        this.appointmentRepository = appointmentRepository;
        this.checkInRepository = checkInRepository;
        this.notificationRepository = notificationRepository;
        this.delayNoticeService = delayNoticeService;
        this.auditLogService = auditLogService;
    }

    // Tries the ID as typed first, then upper-cased - new registrations are
    // normalized to SXXXXXXXX, but legacy accounts (e.g. seeded "student1")
    // predate that format and are stored with their original casing.
    private User findStudent(String rawStudentId) {
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

    private String displayNameFor(User student, String studentId) {
        return (student.getFullName() != null && !student.getFullName().isBlank()) ? student.getFullName() : studentId;
    }

    public static class EmergencyCheckInRequest {
        public String studentId;
        public String reason;
        public boolean notifyDelay; // staff ticked "tell today's booked students of a delay"
    }

    // Registers AND checks in the student in a single step, skipping the
    // normal BOOKED -> nurse-queue -> check-in flow, so they land directly at
    // the top of the doctor's active queue. Students booked today are only
    // warned of a delay if the nurse/doctor ticked that option.
    @PostMapping("/checkin")
    public Object emergencyCheckIn(@RequestBody EmergencyCheckInRequest request, Authentication auth) {
        User student = findStudent(request.studentId);
        if (student == null) {
            return Map.of("error", "No registered student found with that Student ID.");
        }
        String studentId = student.getUsername();

        if (request.reason != null && request.reason.trim().length() > MAX_REASON_LENGTH) {
            return Map.of("error", "Reason must be " + MAX_REASON_LENGTH + " characters or fewer.");
        }
        String reason = (request.reason == null || request.reason.isBlank()) ? "Emergency" : request.reason.trim();
        String displayName = displayNameFor(student, studentId);

        Appointment appointment = new Appointment();
        appointment.setStudentName(studentId);
        appointment.setAppointmentDate(LocalDate.now());
        appointment.setTimeSlot("Emergency");
        appointment.setVisitReason(reason);
        appointment.setUrgencyLevel("Emergency");
        appointment.setStatus("CHECKED_IN");
        appointmentRepository.save(appointment);

        CheckIn checkIn = new CheckIn();
        checkIn.setAppointment(appointment);
        checkIn.setPresentingComplaint(reason);
        checkIn.setCheckInTime(LocalDateTime.now());
        checkIn.setCheckedInBy(auth.getName());
        checkInRepository.save(checkIn);

        Notification staffAlert = new Notification();
        staffAlert.setType("EMERGENCY_BOOKING");
        staffAlert.setStudentUsername(studentId);
        staffAlert.setStudentName(displayName);
        staffAlert.setAppointmentId(appointment.getId());
        staffAlert.setMessage("EMERGENCY: " + displayName + " checked in by " + auth.getName() + " and needs immediate attention: " + reason);
        staffAlert.setCreatedAt(LocalDateTime.now());
        notificationRepository.save(staffAlert);

        // The student is now with staff, so their one-click alert is done.
        notificationRepository.findByTypeAndStudentUsernameAndResolvedAtIsNullOrderByCreatedAtDesc("EMERGENCY_ALERT", studentId)
                .forEach(alert -> {
                    alert.resolve(auth.getName());
                    notificationRepository.save(alert);
                });

        String delayNote = "";
        if (request.notifyDelay) {
            DelayNoticeService.Announcement sent = announceDelay(auth, studentId);
            delayNote = " " + sent.notified() + " student(s) booked today notified of a possible delay"
                    + (sent.alreadyNotified() > 0 ? " (" + sent.alreadyNotified() + " already told earlier today)." : ".");
        }

        return Map.of(
                "message", "Emergency check-in complete for " + displayName + ". Doctors have been alerted." + delayNote,
                "appointmentId", appointment.getId()
        );
    }

    public static class NotifyDelayRequest {
        public Long alertId; // the emergency alert this is about, if any
    }

    // "Notify today's students of a delay" - pressed by a nurse/doctor once
    // they know an emergency will hold up today's appointments. Never sent
    // automatically, since a student's alert may be a false alarm.
    @PostMapping("/notify-delay")
    public Map<String, Object> notifyDelay(@RequestBody(required = false) NotifyDelayRequest request, Authentication auth) {
        String emergencyStudentId = null;
        if (request != null && request.alertId != null) {
            emergencyStudentId = notificationRepository.findById(request.alertId)
                    .map(Notification::getStudentUsername).orElse(null);
        }
        DelayNoticeService.Announcement sent = announceDelay(auth, emergencyStudentId);
        String message = sent.notified() == 0 && sent.alreadyNotified() == 0
                ? "No other students are booked for today, so nobody needed to be notified."
                : "Notified " + sent.notified() + " student(s) booked today that their appointment may be delayed."
                        + (sent.alreadyNotified() > 0 ? " " + sent.alreadyNotified() + " had already been told today." : "");
        return Map.of("message", message, "notified", sent.notified(), "alreadyNotified", sent.alreadyNotified());
    }

    private DelayNoticeService.Announcement announceDelay(Authentication auth, String emergencyStudentId) {
        DelayNoticeService.Announcement sent = delayNoticeService.announceDelay(auth.getName(), emergencyStudentId);
        auditLogService.recordAccess(auth, "DELAY_NOTICE_SENT", emergencyStudentId,
                sent.notified() + " student(s) booked today notified of a possible delay");
        return sent;
    }
}
