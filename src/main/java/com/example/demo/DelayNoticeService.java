package com.example.demo;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;

// Warns students who have a visit TODAY that an emergency is being prioritized
// and their appointment may be delayed. Only today's bookings are affected - a
// student booked for another day is never notified. Each student gets at most
// one notice per emergency student per day, so an emergency that is first
// alerted from the student portal and later checked in by staff doesn't
// notify the same people twice.
@Service
public class DelayNoticeService {

    static final String DELAY_MESSAGE = "An emergency patient is being prioritized at the Health Center today. "
            + "Your appointment today may be delayed - you'll be seen once the doctor is free. Thank you for your patience.";

    private final AppointmentRepository appointmentRepository;
    private final NotificationRepository notificationRepository;

    public DelayNoticeService(AppointmentRepository appointmentRepository,
                              NotificationRepository notificationRepository) {
        this.appointmentRepository = appointmentRepository;
        this.notificationRepository = notificationRepository;
    }

    // An emergency visit (created by Emergency Check-in) is the one being
    // prioritized, so it is never the visit that gets delayed.
    static boolean isEmergencyVisit(Appointment appointment) {
        return "Emergency".equals(appointment.getTimeSlot()) || "Emergency".equals(appointment.getUrgencyLevel());
    }

    // A student who raised an emergency themselves today is the patient being
    // prioritized - they're never told they'll be delayed.
    public boolean hasRaisedEmergencyToday(String username) {
        return notificationRepository.existsByTypeAndStudentUsernameAndCreatedAtGreaterThanEqual(
                "EMERGENCY_ALERT", username, LocalDate.now().atStartOfDay());
    }

    // The student's normal booking for today (still booked, or checked in and
    // waiting for a doctor), if any - the only kind of visit a delay applies to.
    public Appointment findNormalVisitToday(String username) {
        LocalDate today = LocalDate.now();
        return appointmentRepository.findByStudentName(username).stream()
                .filter(a -> today.equals(a.getAppointmentDate()))
                .filter(a -> "BOOKED".equals(a.getStatus()) || "CHECKED_IN".equals(a.getStatus()))
                .filter(a -> !isEmergencyVisit(a))
                .findFirst()
                .orElse(null);
    }

    // Returns how many students were notified.
    public int notifyStudentsWithVisitToday(String emergencyStudentId, String emergencyDisplayName, Long excludeAppointmentId) {
        LocalDate today = LocalDate.now();
        LocalDateTime startOfToday = today.atStartOfDay();

        List<Appointment> waiting = new ArrayList<>();
        waiting.addAll(appointmentRepository.findByStatus("BOOKED"));
        waiting.addAll(appointmentRepository.findByStatus("CHECKED_IN"));

        int notified = 0;
        java.util.Set<String> alreadyNotified = new java.util.HashSet<>();
        for (Appointment prior : waiting) {
            if (!today.equals(prior.getAppointmentDate())) continue;
            if (excludeAppointmentId != null && excludeAppointmentId.equals(prior.getId())) continue;
            if (isEmergencyVisit(prior)) continue;
            String recipient = prior.getStudentName();
            if (recipient == null || recipient.equals(emergencyStudentId)) continue;
            if (hasRaisedEmergencyToday(recipient)) continue;
            if (!alreadyNotified.add(recipient)) continue;
            if (notificationRepository.existsByTypeAndRecipientUsernameAndStudentUsernameAndCreatedAtGreaterThanEqual(
                    "DELAY_NOTICE", recipient, emergencyStudentId, startOfToday)) continue;

            saveDelayNotice(recipient, emergencyStudentId, emergencyDisplayName, prior.getId());
            notified++;
        }
        return notified;
    }

    // For a student who books (or reschedules) into TODAY after an emergency
    // has already been raised today: they weren't around when the original
    // delay notices went out, so send theirs now. Bookings for any other day
    // never get one. Returns true if a notice was sent.
    public boolean notifyLateBookingIfEmergencyToday(Appointment booking) {
        LocalDate today = LocalDate.now();
        String recipient = booking.getStudentName();
        if (recipient == null || !today.equals(booking.getAppointmentDate()) || isEmergencyVisit(booking)) return false;
        if (hasRaisedEmergencyToday(recipient)) return false;

        LocalDateTime startOfToday = today.atStartOfDay();
        boolean sent = false;
        java.util.Set<String> seenEmergencies = new java.util.HashSet<>();
        for (Notification emergency : notificationRepository.findByRecipientUsernameIsNullAndTypeInAndCreatedAtGreaterThanEqual(
                List.of("EMERGENCY_ALERT", "EMERGENCY_BOOKING"), startOfToday)) {
            String emergencyStudentId = emergency.getStudentUsername();
            if (emergencyStudentId == null || emergencyStudentId.equals(recipient)) continue;
            if (!seenEmergencies.add(emergencyStudentId)) continue;
            if (notificationRepository.existsByTypeAndRecipientUsernameAndStudentUsernameAndCreatedAtGreaterThanEqual(
                    "DELAY_NOTICE", recipient, emergencyStudentId, startOfToday)) continue;
            saveDelayNotice(recipient, emergencyStudentId, emergency.getStudentName(), booking.getId());
            sent = true;
        }
        return sent;
    }

    private void saveDelayNotice(String recipient, String emergencyStudentId, String emergencyDisplayName, Long appointmentId) {
        Notification delayNotice = new Notification();
        delayNotice.setType("DELAY_NOTICE");
        delayNotice.setRecipientUsername(recipient);
        delayNotice.setStudentUsername(emergencyStudentId);
        delayNotice.setStudentName(emergencyDisplayName);
        delayNotice.setAppointmentId(appointmentId);
        delayNotice.setMessage(DELAY_MESSAGE);
        delayNotice.setCreatedAt(LocalDateTime.now());
        notificationRepository.save(delayNotice);
    }
}
