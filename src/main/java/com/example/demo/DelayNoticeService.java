package com.example.demo;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Service;

// Warns students who have a visit TODAY that an emergency is being prioritized
// and their appointment may be delayed. Nothing is sent automatically - a
// student's emergency alert may be a false alarm - so a nurse or doctor
// decides, by pressing "Notify today's students of a delay". That press is
// recorded as a DELAY_ANNOUNCED entry in the staff feed (so other staff can
// see it's been done), and each student booked today gets one DELAY_NOTICE.
// Only today's bookings are affected, and each student is told at most once a
// day; a student who books into today after the announcement is told then.
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

    // The most recent staff announcement today, or null if nobody has pressed it.
    public Notification findDelayAnnouncementToday() {
        return notificationRepository.findFirstByTypeAndCreatedAtGreaterThanEqualOrderByCreatedAtDesc(
                "DELAY_ANNOUNCED", LocalDate.now().atStartOfDay());
    }

    public record Announcement(int notified, int alreadyNotified) {}

    // A nurse/doctor pressed "Notify today's students of a delay". Students
    // already told today are skipped, so pressing it again (e.g. for a second
    // emergency, or by a colleague) only reaches anyone who booked since.
    public Announcement announceDelay(String staffUsername, String emergencyStudentId) {
        LocalDateTime startOfToday = LocalDate.now().atStartOfDay();

        List<Appointment> waiting = new ArrayList<>();
        waiting.addAll(appointmentRepository.findByStatus("BOOKED"));
        waiting.addAll(appointmentRepository.findByStatus("CHECKED_IN"));

        int notified = 0;
        int alreadyNotified = 0;
        Set<String> seen = new HashSet<>();
        for (Appointment visit : waiting) {
            if (!LocalDate.now().equals(visit.getAppointmentDate()) || isEmergencyVisit(visit)) continue;
            String recipient = visit.getStudentName();
            if (recipient == null || recipient.equals(emergencyStudentId)) continue;
            if (hasRaisedEmergencyToday(recipient)) continue;
            if (!seen.add(recipient)) continue;
            if (wasToldToday(recipient, startOfToday)) { alreadyNotified++; continue; }
            saveDelayNotice(recipient, visit.getId());
            notified++;
        }

        Notification staffNote = new Notification();
        staffNote.setType("DELAY_ANNOUNCED");
        staffNote.setStudentUsername(emergencyStudentId);
        staffNote.setMessage(staffUsername + " notified " + notified + " student" + (notified == 1 ? "" : "s")
                + " booked today that their appointment may be delayed"
                + (alreadyNotified > 0 ? " (" + alreadyNotified + " already told earlier today)." : "."));
        staffNote.setCreatedAt(LocalDateTime.now());
        staffNote.setRead(true); // a record for staff, not something to act on
        notificationRepository.save(staffNote);

        return new Announcement(notified, alreadyNotified);
    }

    // For a student who books (or reschedules) into TODAY after staff have
    // announced a delay today: they weren't around when the notices went out,
    // so send theirs now. Returns true if a notice was sent.
    public boolean notifyLateBookingIfDelayAnnounced(Appointment booking) {
        LocalDate today = LocalDate.now();
        String recipient = booking.getStudentName();
        if (recipient == null || !today.equals(booking.getAppointmentDate()) || isEmergencyVisit(booking)) return false;
        if (hasRaisedEmergencyToday(recipient) || findDelayAnnouncementToday() == null) return false;
        if (wasToldToday(recipient, today.atStartOfDay())) return false;
        saveDelayNotice(recipient, booking.getId());
        return true;
    }

    private boolean wasToldToday(String recipient, LocalDateTime startOfToday) {
        return notificationRepository.existsByTypeAndRecipientUsernameAndCreatedAtGreaterThanEqual(
                "DELAY_NOTICE", recipient, startOfToday);
    }

    // The notice doesn't name the emergency patient - other students only need
    // to know their own visit may be late.
    private void saveDelayNotice(String recipient, Long appointmentId) {
        Notification delayNotice = new Notification();
        delayNotice.setType("DELAY_NOTICE");
        delayNotice.setRecipientUsername(recipient);
        delayNotice.setAppointmentId(appointmentId);
        delayNotice.setMessage(DELAY_MESSAGE);
        delayNotice.setCreatedAt(LocalDateTime.now());
        notificationRepository.save(delayNotice);
    }
}
