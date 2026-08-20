package com.example.demo;

import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/student")
public class BookingController {

    @Autowired
    private DoctorScheduleRepository scheduleRepository;

    @Autowired
    private AppointmentRepository appointmentRepository;

    public static class BookingRequest {
        public Long slotId;
        public String visitReason;
        public String urgencyLevel;
    }

    // FR-01-09 to 14: book an available slot, tied to the logged-in student
    @PostMapping("/book")
    public Object bookAppointment(@RequestBody BookingRequest request, Authentication auth) {
        if (request.visitReason == null || request.visitReason.isBlank()) {
            return Map.of("error", "Reason for visit is required.");
        }
        if (request.urgencyLevel == null || request.urgencyLevel.isBlank()) {
            return Map.of("error", "Urgency level is required.");
        }

        DoctorSchedule slot = scheduleRepository.findById(request.slotId).orElse(null);
        if (slot == null) {
            return Map.of("error", "This slot is no longer available.");
        }

        // Prevents two students booking the same slot (FR-01-13)
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
        appointment.setDoctorName(slot.getDoctorName());
        appointment.setAppointmentDate(slot.getScheduleDate());
        appointment.setVisitReason(request.visitReason);
        appointment.setUrgencyLevel(request.urgencyLevel);
        appointment.setStatus("BOOKED");
        appointment.setScheduleSlotId(slot.getId());
        appointmentRepository.save(appointment);

        return Map.of(
                "message", "Appointment confirmed.",
                "appointmentId", appointment.getId(),
                "doctorName", appointment.getDoctorName(),
                "date", appointment.getAppointmentDate().toString(),
                "timeSlot", slot.getTimeSlot()
        );
    }

    // FR-01-15: student views their own upcoming appointments
    @GetMapping("/my-appointments")
    public List<Appointment> myAppointments(Authentication auth) {
        return appointmentRepository.findByStudentNameAndStatus(auth.getName(), "BOOKED");
    }

    public static class CancelRequest {
        public Long appointmentId;
    }

    // FR-01-17, 19: cancel an upcoming appointment, freeing the slot back up
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

        if (appointment.getScheduleSlotId() != null) {
            scheduleRepository.findById(appointment.getScheduleSlotId()).ifPresent(slot -> {
                slot.setBooked(false);
                scheduleRepository.save(slot);
            });
        }

        return Map.of("message", "Appointment cancelled.");
    }

    public static class RescheduleRequest {
        public Long appointmentId;
        public Long newSlotId;
    }

    // FR-01-16, 18: reschedule to a new open slot, freeing the old one
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

        DoctorSchedule newSlot;
        synchronized (this) {
            newSlot = scheduleRepository.findById(request.newSlotId).orElse(null);
            if (newSlot == null || newSlot.isBooked()) {
                return Map.of("error", "This slot is no longer available.");
            }
            newSlot.setBooked(true);
            scheduleRepository.save(newSlot);
        }

        if (appointment.getScheduleSlotId() != null) {
            scheduleRepository.findById(appointment.getScheduleSlotId()).ifPresent(oldSlot -> {
                oldSlot.setBooked(false);
                scheduleRepository.save(oldSlot);
            });
        }

        appointment.setDoctorName(newSlot.getDoctorName());
        appointment.setAppointmentDate(newSlot.getScheduleDate());
        appointment.setScheduleSlotId(newSlot.getId());
        appointmentRepository.save(appointment);

        return Map.of(
                "message", "Appointment rescheduled.",
                "newDoctorName", newSlot.getDoctorName(),
                "newDate", newSlot.getScheduleDate().toString(),
                "newTimeSlot", newSlot.getTimeSlot()
        );
    }
}