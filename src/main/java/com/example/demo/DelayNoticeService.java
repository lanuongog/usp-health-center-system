package com.example.demo;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Service;

// Warns students booked for TODAY that an emergency will push their visit back.
// Fired both when a student sends an emergency alert from the portal (staff
// are expecting them) and when staff complete the emergency check-in - each
// booking is only told once per emergency student, so the later check-in
// doesn't repeat the notice the alert already sent.
@Service
public class DelayNoticeService {

    private static final Set<String> WAITING_STATUSES = Set.of("BOOKED", "CHECKED_IN");

    private final AppointmentRepository appointmentRepository;
    private final NotificationRepository notificationRepository;

    public DelayNoticeService(AppointmentRepository appointmentRepository,
                              NotificationRepository notificationRepository) {
        this.appointmentRepository = appointmentRepository;
        this.notificationRepository = notificationRepository;
    }

    // Returns how many students were newly notified.
    public int notifyTodaysBookings(String emergencyStudentUsername, String emergencyStudentName) {
        LocalDate today = LocalDate.now();
        List<Appointment> affected = appointmentRepository.findByAppointmentDateBetween(today, today).stream()
                .filter(a -> WAITING_STATUSES.contains(a.getStatus()))
                .filter(a -> !emergencyStudentUsername.equals(a.getStudentName()))
                .filter(a -> !notificationRepository.existsByTypeAndAppointmentIdAndStudentUsername(
                        "DELAY_NOTICE", a.getId(), emergencyStudentUsername))
                .toList();

        for (Appointment appointment : affected) {
            Notification delayNotice = new Notification();
            delayNotice.setType("DELAY_NOTICE");
            delayNotice.setRecipientUsername(appointment.getStudentName());
            delayNotice.setStudentUsername(emergencyStudentUsername);
            delayNotice.setStudentName(emergencyStudentName);
            delayNotice.setAppointmentId(appointment.getId());
            delayNotice.setMessage("An emergency patient is being prioritized at the Health Center today, so your "
                    + "appointment" + (appointment.getTimeSlot() != null && !"Emergency".equals(appointment.getTimeSlot())
                            ? " at " + appointment.getTimeSlot() : "")
                    + " may be delayed. You'll be seen as soon as the doctor is free - thank you for your patience.");
            delayNotice.setCreatedAt(LocalDateTime.now());
            notificationRepository.save(delayNotice);
        }
        return affected.size();
    }
}
