package com.example.demo;
import org.springframework.transaction.annotation.Transactional;

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

    public EmergencyController(UserRepository userRepository,
                                AppointmentRepository appointmentRepository,
                                CheckInRepository checkInRepository,
                                NotificationRepository notificationRepository,
                                DelayNoticeService delayNoticeService) {
        this.userRepository = userRepository;
        this.appointmentRepository = appointmentRepository;
        this.checkInRepository = checkInRepository;
        this.notificationRepository = notificationRepository;
        this.delayNoticeService = delayNoticeService;
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
    }

    // Registers AND checks in the student in a single step, skipping the
    // normal BOOKED -> nurse-queue -> check-in flow, so they land directly at
    // the top of the doctor's active queue. Every student currently waiting -
    // whether booked for today or already checked in ahead of a doctor - is
    // warned of a possible delay; they'll be seen once the doctor is free of
    // the emergency.
    @Transactional
    @PostMapping("/checkin")
    public Object emergencyCheckIn(@RequestBody EmergencyCheckInRequest request, Authentication auth) {
        User student = findStudent(request.studentId);
        if (student == null) {
            return Map.of("error", "No registered student found with that Student ID.");
        }
        String studentId = student.getUsername();
        if (!student.isActive()) {
            return Map.of("error", "This student account is deactivated.");
        }
        if (!appointmentRepository.findByStudentNameAndStatus(studentId, "CHECKED_IN").isEmpty()) {
            return Map.of("error", "This student is already checked in and waiting to be seen.");
        }

        if (request.reason != null && request.reason.trim().length() > MAX_REASON_LENGTH) {
            return Map.of("error", "Reason must be " + MAX_REASON_LENGTH + " characters or fewer.");
        }
        String rsnError = InputRules.freeText(request.reason, "Reason");
        if (rsnError != null) {
            return Map.of("error", rsnError);
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

        // Everyone with a visit today - booked, or already checked in ahead of
        // a doctor - gets bumped behind this emergency and is notified.
        int notifiedCount = delayNoticeService.notifyStudentsWithVisitToday(studentId, displayName, appointment.getId());

        return Map.of(
                "message", "Emergency check-in complete for " + displayName + ". Doctors have been alerted and "
                        + notifiedCount + " student(s) notified of a possible delay.",
                "appointmentId", appointment.getId(),
                "notifiedCount", notifiedCount
        );
    }
}
