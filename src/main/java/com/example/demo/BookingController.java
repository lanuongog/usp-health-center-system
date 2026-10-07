package com.example.demo;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Locale;
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

    // Students no longer choose an urgency - every online booking is Routine;
    // emergencies go through the portal's Emergency button instead.
    private static final String STUDENT_BOOKING_URGENCY = "Routine";
    // Reason for visit: at most 300 words, with a character cap so one huge
    // "word" can't get through.
    private static final int MAX_REASON_WORDS = 300;
    private static final int MAX_REASON_CHARS = 3000;
    private static final DateTimeFormatter SLOT_TIME_FORMAT = new DateTimeFormatterBuilder()
            .parseCaseInsensitive().appendPattern("h:mm a").toFormatter(Locale.US);

    private final DoctorScheduleRepository scheduleRepository;
    private final AppointmentRepository appointmentRepository;
    private final CheckInRepository checkInRepository;
    private final UserRepository userRepository;
    private final NotificationRepository notificationRepository;
    private final DelayNoticeService delayNoticeService;

    public BookingController(DoctorScheduleRepository scheduleRepository,
                            AppointmentRepository appointmentRepository,
                            CheckInRepository checkInRepository,
                            UserRepository userRepository,
                            NotificationRepository notificationRepository,
                            DelayNoticeService delayNoticeService) {
        this.scheduleRepository = scheduleRepository;
        this.appointmentRepository = appointmentRepository;
        this.checkInRepository = checkInRepository;
        this.userRepository = userRepository;
        this.notificationRepository = notificationRepository;
        this.delayNoticeService = delayNoticeService;
    }

    public static class BookingRequest {
        public Long slotId;
        public String visitReason;
    }

    // A slot is in the past if its date is before today, or it's today and its
    // start time (e.g. "09:00 AM" from "09:00 AM - 09:30 AM") has already passed.
    static boolean isSlotInPast(DoctorSchedule slot) {
        LocalDate date = slot.getScheduleDate();
        if (date == null) return true;
        LocalDate today = LocalDate.now();
        if (date.isBefore(today)) return true;
        if (date.isAfter(today) || slot.getTimeSlot() == null) return false;
        try {
            LocalTime start = LocalTime.parse(slot.getTimeSlot().split("-")[0].trim(), SLOT_TIME_FORMAT);
            return !start.isAfter(LocalTime.now());
        } catch (DateTimeParseException e) {
            return false;
        }
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
    @PostMapping("/book")
    public Object bookAppointment(@RequestBody BookingRequest request, Authentication auth) {
        User student = userRepository.findByUsername(auth.getName()).orElse(null);
        if (student == null || !student.isFeePaid()) {
            return Map.of("error", "Your payment receipt is still pending verification by an admin. Please wait for approval before booking an appointment.");
        }
        if (request.visitReason == null || request.visitReason.isBlank()) {
            return Map.of("error", "Reason for visit is required.");
        }
        String reasonText = request.visitReason.trim();
        if (reasonText.split("\\s+").length > MAX_REASON_WORDS) {
            return Map.of("error", "Reason for visit must be " + MAX_REASON_WORDS + " words or fewer.");
        }
        if (reasonText.length() > MAX_REASON_CHARS) {
            return Map.of("error", "Reason for visit is too long.");
        }
        if (request.slotId == null) {
            return Map.of("error", "Please select an available slot.");
        }

        DoctorSchedule slot = scheduleRepository.findById(request.slotId).orElse(null);
        if (slot == null) {
            return Map.of("error", "This slot is no longer available.");
        }
        if (isSlotInPast(slot)) {
            return Map.of("error", "You can't book a date or time that has already passed.");
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
        appointment.setUrgencyLevel(STUDENT_BOOKING_URGENCY);
        appointment.setStatus("BOOKED");
        appointment.setScheduleSlotId(slot.getId());
        appointmentRepository.save(appointment);

        // Booked for today after an emergency was already raised today -
        // let them know up front that their visit may be delayed.
        boolean delayed = delayNoticeService.notifyLateBookingIfEmergencyToday(appointment);

        return Map.of(
                "message", "Appointment confirmed.",
                "emergencyDelay", delayed,
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

        DoctorSchedule peekSlot = request.newSlotId == null ? null : scheduleRepository.findById(request.newSlotId).orElse(null);
        if (peekSlot != null && isSlotInPast(peekSlot)) {
            return Map.of("error", "You can't reschedule to a date or time that has already passed.");
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

        boolean delayed = delayNoticeService.notifyLateBookingIfEmergencyToday(appointment);

        return Map.of(
                "message", "Appointment rescheduled.",
                "emergencyDelay", delayed,
                "newDate", newSlot.getScheduleDate().toString(),
                "newTimeSlot", newSlot.getTimeSlot()
        );
    }
}