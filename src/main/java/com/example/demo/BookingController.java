package com.example.demo;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/student")
public class BookingController {

    // Matches the start time of a "09:00 AM - 09:30 AM" style slot label.
    private static final Pattern SLOT_START_TIME = Pattern.compile("^\\s*(\\d{1,2}):(\\d{2})\\s*(AM|PM)", Pattern.CASE_INSENSITIVE);
    private static final int MAX_REASON_LENGTH = 300;

    private final DoctorScheduleRepository scheduleRepository;
    private final AppointmentRepository appointmentRepository;
    private final CheckInRepository checkInRepository;
    private final UserRepository userRepository;

    public BookingController(DoctorScheduleRepository scheduleRepository,
                            AppointmentRepository appointmentRepository,
                            CheckInRepository checkInRepository,
                            UserRepository userRepository) {
        this.scheduleRepository = scheduleRepository;
        this.appointmentRepository = appointmentRepository;
        this.checkInRepository = checkInRepository;
        this.userRepository = userRepository;
    }

    public static class BookingRequest {
        public Long slotId;
        public String visitReason;
    }

    // A slot can't be booked once its date has passed, or - for today - once
    // its start time has passed. Labels that can't be parsed fall back to
    // the date check alone.
    private boolean isInPast(DoctorSchedule slot) {
        LocalDate date = slot.getScheduleDate();
        if (date == null) return true;
        LocalDate today = LocalDate.now();
        if (date.isBefore(today)) return true;
        if (date.isAfter(today) || slot.getTimeSlot() == null) return false;

        Matcher m = SLOT_START_TIME.matcher(slot.getTimeSlot());
        if (!m.find()) return false;
        int hour = Integer.parseInt(m.group(1)) % 12;
        if ("PM".equalsIgnoreCase(m.group(3))) hour += 12;
        LocalTime start = LocalTime.of(hour, Integer.parseInt(m.group(2)));
        return !LocalDateTime.of(date, start).isAfter(LocalDateTime.now());
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
        if (request.visitReason.trim().length() > MAX_REASON_LENGTH) {
            return Map.of("error", "Reason for visit must be " + MAX_REASON_LENGTH + " characters or fewer.");
        }
        if (request.slotId == null) {
            return Map.of("error", "Please select an available slot.");
        }

        DoctorSchedule slot = scheduleRepository.findById(request.slotId).orElse(null);
        if (slot == null) {
            return Map.of("error", "This slot is no longer available.");
        }
        if (isInPast(slot)) {
            return Map.of("error", "This slot is in the past. Please choose an upcoming date and time.");
        }

        boolean alreadyBookedAtThisTime = !appointmentRepository.findByStudentNameAndStatusAndAppointmentDateAndTimeSlot(
                auth.getName(), "BOOKED", slot.getScheduleDate(), slot.getTimeSlot()).isEmpty();
        if (alreadyBookedAtThisTime) {
            return Map.of("error", "You already have an appointment booked for this date and time slot.");
        }

        // Prevents two students booking the same time slot: a time is only offered
        // to one student at a time even when multiple doctors are on duty for it, so
        // booking one doctor's row for a date+time books every doctor's row for it.
        synchronized (this) {
            slot = scheduleRepository.findById(request.slotId).orElse(null);
            if (slot == null || slot.isBooked()
                    || scheduleRepository.anyBookedForTimeSlot(slot.getScheduleDate(), slot.getTimeSlot())) {
                return Map.of("error", "This slot is no longer available.");
            }
            scheduleRepository.setBookedForTimeSlot(slot.getScheduleDate(), slot.getTimeSlot(), true);
        }

        Appointment appointment = new Appointment();
        appointment.setStudentName(auth.getName());
        // No doctor is assigned at booking time - any doctor may pick up this
        // patient once checked in, and the treating doctor is recorded as
        // consultedByDoctor when the consultation is actually completed.
        appointment.setAppointmentDate(slot.getScheduleDate());
        appointment.setTimeSlot(slot.getTimeSlot());
        appointment.setVisitReason(request.visitReason.trim());
        // Students no longer pick an urgency - every booking is Routine, and
        // genuine emergencies go through the emergency alert / check-in flow.
        appointment.setUrgencyLevel("Routine");
        appointment.setStatus("BOOKED");
        appointment.setScheduleSlotId(slot.getId());
        appointmentRepository.save(appointment);

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

        if (appointment.getAppointmentDate() != null && appointment.getTimeSlot() != null) {
            scheduleRepository.setBookedForTimeSlot(appointment.getAppointmentDate(), appointment.getTimeSlot(), false);
        }

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

        DoctorSchedule peekSlot = scheduleRepository.findById(request.newSlotId).orElse(null);
        if (peekSlot != null && isInPast(peekSlot)) {
            return Map.of("error", "This slot is in the past. Please choose an upcoming date and time.");
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
            if (newSlot == null || newSlot.isBooked()
                    || scheduleRepository.anyBookedForTimeSlot(newSlot.getScheduleDate(), newSlot.getTimeSlot())) {
                return Map.of("error", "This slot is no longer available.");
            }
            scheduleRepository.setBookedForTimeSlot(newSlot.getScheduleDate(), newSlot.getTimeSlot(), true);
        }

        if (appointment.getAppointmentDate() != null && appointment.getTimeSlot() != null) {
            scheduleRepository.setBookedForTimeSlot(appointment.getAppointmentDate(), appointment.getTimeSlot(), false);
        }

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