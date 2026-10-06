package com.example.demo;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/student")
public class BookingController {

    private static final List<String> URGENCY_LEVELS = List.of("Routine", "Urgent", "Emergency");
    private static final int MAX_REASON_LENGTH = 300;
    private static final int MAX_UPCOMING_APPOINTMENTS = 3;

    private final DoctorScheduleRepository scheduleRepository;
    private final AppointmentRepository appointmentRepository;
    private final CheckInRepository checkInRepository;
    private final UserRepository userRepository;
    private final NotificationRepository notificationRepository;

    public BookingController(DoctorScheduleRepository scheduleRepository,
                            AppointmentRepository appointmentRepository,
                            CheckInRepository checkInRepository,
                            UserRepository userRepository,
                            NotificationRepository notificationRepository) {
        this.scheduleRepository = scheduleRepository;
        this.appointmentRepository = appointmentRepository;
        this.checkInRepository = checkInRepository;
        this.userRepository = userRepository;
        this.notificationRepository = notificationRepository;
    }

    public static class BookingRequest {
        public Long slotId;
        public String visitReason;
        public String urgencyLevel;
    }

    // Student views their full visit and consultation history (completed, vitals, diagnosis, notes)
    @GetMapping("/history")
    public List<Map<String, Object>> myHistory(Authentication auth) {
        List<Appointment> list = appointmentRepository.findByStudentName(auth.getName());
        return list.stream()
                .sorted(java.util.Comparator.comparing(
                    (Appointment a) -> a.getAppointmentDate(),
                    java.util.Comparator.nullsLast(java.util.Comparator.reverseOrder())))
                .map(a -> {
                    Map<String, Object> map = new java.util.LinkedHashMap<>();
                    map.put("id", a.getId());
                    map.put("doctorName", a.getDoctorName());
                    map.put("appointmentDate", a.getAppointmentDate() != null ? a.getAppointmentDate().toString() : "");
                    map.put("timeSlot", a.getTimeSlot());
                    map.put("visitReason", a.getVisitReason());
                    map.put("urgencyLevel", a.getUrgencyLevel());
                    map.put("status", a.getStatus());
                    map.put("diagnosis", a.getDiagnosis());
                    map.put("consultationNotes", a.getConsultationNotes());
                    map.put("consultedByDoctor", a.getConsultedByDoctor());

                    checkInRepository.findByAppointmentId(a.getId()).ifPresent(c -> {
                        map.put("temperature", c.getTemperature());
                        map.put("bloodPressure", c.getBloodPressure());
                        map.put("presentingComplaint", c.getPresentingComplaint());
                    });

                    return map;
                })
                .toList();
    }

    // book an available slot, tied to the logged-in student
    @Transactional
    @PostMapping("/book")
    public Object bookAppointment(@RequestBody BookingRequest request, Authentication auth) {
        User student = userRepository.findByUsername(auth.getName()).orElse(null);
        if (student == null || !student.isFeePaid()) {
            return Map.of("error", "Your payment receipt is still pending verification by an admin. Please wait for approval before booking an appointment.");
        }
        if (request.visitReason == null || request.visitReason.isBlank()) {
            return Map.of("error", "Reason for visit is required.");
        }
        if (request.visitReason.trim().length() > MAX_REASON_LENGTH) {
            return Map.of("error", "Reason for visit must be " + MAX_REASON_LENGTH + " characters or fewer.");
        }
        if (request.urgencyLevel == null || request.urgencyLevel.isBlank()) {
            return Map.of("error", "Urgency level is required.");
        }
        if (!URGENCY_LEVELS.contains(request.urgencyLevel)) {
            return Map.of("error", "Urgency level must be one of: " + String.join(", ", URGENCY_LEVELS) + ".");
        }
        if (request.slotId == null) {
            return Map.of("error", "Please select an available slot.");
        }

        DoctorSchedule slot = scheduleRepository.findById(request.slotId).orElse(null);
        if (slot == null) {
            return Map.of("error", "This slot is no longer available.");
        }

        // DEF-05: a slot whose date has already passed can never take place
        if (slot.getScheduleDate() != null && slot.getScheduleDate().isBefore(LocalDate.now())) {
            return Map.of("error", "This slot has already passed. Please select a current or future slot.");
        }

        // limit how many upcoming appointments one student can hold at a time
        long upcoming = appointmentRepository.findByStudentNameAndStatus(auth.getName(), "BOOKED").stream()
                .filter(x -> x.getAppointmentDate() != null && !x.getAppointmentDate().isBefore(LocalDate.now()))
                .count();
        if (upcoming >= MAX_UPCOMING_APPOINTMENTS) {
            return Map.of("error", "You already have " + MAX_UPCOMING_APPOINTMENTS
                    + " upcoming appointments. Please cancel one or attend it before booking another.");
        }

        boolean alreadyBookedAtThisTime = !appointmentRepository.findByStudentNameAndStatusAndAppointmentDateAndTimeSlot(
                auth.getName(), "BOOKED", slot.getScheduleDate(), slot.getTimeSlot()).isEmpty();
        if (alreadyBookedAtThisTime) {
            return Map.of("error", "You already have an appointment booked for this date and time slot.");
        }

        // Prevents two students booking the same doctor's slot at once - each
        // doctor's row is booked independently of every other doctor's.
        synchronized (this) {
            slot = scheduleRepository.findById(request.slotId).orElse(null);
            if (slot == null || slot.isBooked()) {
                return Map.of("error", "This slot is no longer available.");
            }
            slot.setBooked(true);
            scheduleRepository.save(slot);
        }

        Appointment appointment = new Appointment();
        appointment.setStudentName(auth.getName());
        // No doctor is assigned at booking time - any doctor may pick up this
        // patient once checked in, and the treating doctor is recorded as
        // consultedByDoctor when the consultation is actually completed.
        appointment.setAppointmentDate(slot.getScheduleDate());
        appointment.setTimeSlot(slot.getTimeSlot());
        appointment.setVisitReason(request.visitReason.trim());
        appointment.setUrgencyLevel(request.urgencyLevel);
        appointment.setStatus("BOOKED");
        appointment.setScheduleSlotId(slot.getId());
        appointmentRepository.save(appointment);

        // Urgent/Emergency bookings raise a staff alert immediately, so a nurse
        // or doctor doesn't have to be actively browsing the queue to notice a
        // booking that needs prompt attention.
        if ("Urgent".equals(request.urgencyLevel) || "Emergency".equals(request.urgencyLevel)) {
            String displayName = student.getFullName() != null && !student.getFullName().isBlank()
                    ? student.getFullName() : auth.getName();
            Notification notification = new Notification();
            notification.setType("Emergency".equals(request.urgencyLevel) ? "EMERGENCY_BOOKING" : "URGENT_BOOKING");
            notification.setStudentUsername(auth.getName());
            notification.setStudentName(displayName);
            notification.setAppointmentId(appointment.getId());
            notification.setMessage(request.urgencyLevel + " booking: " + displayName + " on "
                    + appointment.getAppointmentDate() + " at " + slot.getTimeSlot() + " — " + request.visitReason);
            notification.setCreatedAt(LocalDateTime.now());
            notificationRepository.save(notification);
        }

        return Map.of(
                "message", "Appointment confirmed.",
                "appointmentId", appointment.getId(),
                "date", appointment.getAppointmentDate().toString(),
                "timeSlot", slot.getTimeSlot()
        );
    }

    // student views their own upcoming appointments
    @GetMapping("/my-appointments")
    public List<Appointment> myAppointments(Authentication auth) {
        return appointmentRepository.findByStudentNameAndStatus(auth.getName(), "BOOKED");
    }

    public static class CancelRequest {
        public Long appointmentId;
    }

    // cancel an upcoming appointment, freeing the slot back up
    @Transactional
    @PostMapping("/cancel")
    public Object cancelAppointment(@RequestBody CancelRequest request, Authentication auth) {
        Appointment appointment = appointmentRepository.findById(request.appointmentId).orElse(null);

        if (appointment == null) {
            return Map.of("error", "Appointment not found.");
        }
        if (!appointment.getStudentName().equals(auth.getName())) {
            return Map.of("error", "You do not have permission to modify this appointment.");
        }
        if (!"BOOKED".equals(appointment.getStatus())) {
            return Map.of("error", "This appointment can no longer be changed.");
        }

        appointment.setStatus("CANCELLED");
        appointmentRepository.save(appointment);
        scheduleRepository.freeSlot(appointment.getScheduleSlotId());

        return Map.of("message", "Appointment cancelled.");
    }

    public static class RescheduleRequest {
        public Long appointmentId;
        public Long newSlotId;
    }

    // reschedule to a new open slot, freeing the old one
    @Transactional
    @PostMapping("/reschedule")
    public Object rescheduleAppointment(@RequestBody RescheduleRequest request, Authentication auth) {
        Appointment appointment = appointmentRepository.findById(request.appointmentId).orElse(null);

        if (appointment == null) {
            return Map.of("error", "Appointment not found.");
        }
        if (!appointment.getStudentName().equals(auth.getName())) {
            return Map.of("error", "You do not have permission to modify this appointment.");
        }
        if (!"BOOKED".equals(appointment.getStatus())) {
            return Map.of("error", "This appointment can no longer be changed.");
        }

        DoctorSchedule peekSlot = scheduleRepository.findById(request.newSlotId).orElse(null);
        // DEF-07: rescheduling into a slot dated before today must be rejected as well
        if (peekSlot != null && peekSlot.getScheduleDate() != null
                && peekSlot.getScheduleDate().isBefore(LocalDate.now())) {
            return Map.of("error", "This slot has already passed. Please select a current or future slot.");
        }
        if (peekSlot != null) {
            boolean conflictsWithOtherAppointment = appointmentRepository
                    .findByStudentNameAndStatusAndAppointmentDateAndTimeSlot(
                            auth.getName(), "BOOKED", peekSlot.getScheduleDate(), peekSlot.getTimeSlot())
                    .stream()
                    .anyMatch(a -> !a.getId().equals(appointment.getId()));
            if (conflictsWithOtherAppointment) {
                return Map.of("error", "You already have an appointment booked for this date and time slot.");
            }
        }

        DoctorSchedule newSlot;
        synchronized (this) {
            newSlot = scheduleRepository.findById(request.newSlotId).orElse(null);
            if (newSlot == null || newSlot.isBooked()) {
                return Map.of("error", "This slot is no longer available.");
            }
            newSlot.setBooked(true);
            scheduleRepository.save(newSlot);
        }

        scheduleRepository.freeSlot(appointment.getScheduleSlotId());

        appointment.setAppointmentDate(newSlot.getScheduleDate());
        appointment.setTimeSlot(newSlot.getTimeSlot());
        appointment.setScheduleSlotId(newSlot.getId());
        appointmentRepository.save(appointment);

        return Map.of(
                "message", "Appointment rescheduled.",
                "newDate", newSlot.getScheduleDate().toString(),
                "newTimeSlot", newSlot.getTimeSlot()
        );
    }
}