package com.example.demo;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/doctor")
public class DoctorController {

    private final AppointmentRepository appointmentRepository;
    private final CheckInRepository checkInRepository;
    private final DoctorScheduleRepository scheduleRepository;

    public DoctorController(AppointmentRepository appointmentRepository,
                           CheckInRepository checkInRepository,
                           DoctorScheduleRepository scheduleRepository) {
        this.appointmentRepository = appointmentRepository;
        this.checkInRepository = checkInRepository;
        this.scheduleRepository = scheduleRepository;
    }

    // Lower number = seen sooner
    private int urgencyRank(String urgencyLevel) {
        if (urgencyLevel == null) return 3;
        switch (urgencyLevel) {
            case "Emergency": return 0;
            case "Urgent": return 1;
            case "Routine": return 2;
            default: return 3;
        }
    }

    private LocalDateTime getCheckInTime(Long appointmentId) {
        return checkInRepository.findAll().stream()
                .filter(c -> c.getAppointment() != null && c.getAppointment().getId().equals(appointmentId))
                .map((CheckIn c) -> c.getCheckInTime())
                .findFirst()
                .orElse(LocalDateTime.MAX);
    }

    // FR-04-43 to FR-04-45: checked-in students, ordered by urgency then check-in time
    @GetMapping("/queue")
    public List<Appointment> getQueue() {
        List<Appointment> checkedIn = appointmentRepository.findByStatus("CHECKED_IN");
        return checkedIn.stream()
                .sorted(Comparator
                        .comparingInt((Appointment a) -> urgencyRank(a.getUrgencyLevel()))
                        .thenComparing(a -> getCheckInTime(a.getId())))
                .collect(Collectors.toList());
    }

    // View all booked / scheduled appointments that have not yet been checked in by nurse
    @GetMapping("/booked")
    public List<Appointment> getBookedAppointments() {
        return appointmentRepository.findByStatus("BOOKED");
    }

    // View all appointments for the doctor dashboard overview
    @GetMapping("/all")
    public List<Appointment> getAllAppointments() {
        return appointmentRepository.findAll();
    }

    // FR-04-49: the nurse's recorded vitals and presenting complaint for the current visit
    @GetMapping("/checkin/{appointmentId}")
    public Map<String, Object> getCheckIn(@PathVariable Long appointmentId) {
        CheckIn checkIn = checkInRepository.findByAppointmentId(appointmentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "This student has not been checked in yet"));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("temperature", checkIn.getTemperature());
        result.put("bloodPressure", checkIn.getBloodPressure());
        result.put("presentingComplaint", checkIn.getPresentingComplaint());
        result.put("checkInTime", checkIn.getCheckInTime() != null ? checkIn.getCheckInTime().toString() : null);
        return result;
    }

    // FR-04-48/49: a student's visit history
    @GetMapping("/history/{studentName}")
    public List<Appointment> getMedicalHistory(@PathVariable String studentName) {
        List<Appointment> history = appointmentRepository.findByStudentName(studentName);
        if (history.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Medical record not found");
        }
        return history;
    }

    public static class ConsultationRequest {
        public Long appointmentId;
        public String diagnosis;
        public String consultationNotes;
    }

    // FR-04-52 to FR-04-57: record diagnosis/notes, mark completed, exit the queue
    @PostMapping("/consultation")
    public Appointment recordConsultation(@RequestBody ConsultationRequest request, Authentication auth) {
        Appointment appointment = appointmentRepository.findById(request.appointmentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Medical record not found"));

        // 4.4.3.4: required consultation information is missing
        if (isBlank(request.diagnosis) || isBlank(request.consultationNotes)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Display validation messages for incomplete fields");
        }

        // FR-04-57: prevent a doctor from editing another doctor's submitted consultation
        if (appointment.getConsultedByDoctor() != null &&
                !appointment.getConsultedByDoctor().equals(auth.getName())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "This consultation was already recorded by another doctor.");
        }

        appointment.setDiagnosis(request.diagnosis);
        appointment.setConsultationNotes(request.consultationNotes);
        appointment.setConsultedByDoctor(auth.getName());
        appointment.setStatus("COMPLETED");

        Appointment saved = appointmentRepository.save(appointment);

        // Free up the schedule slot so other students can book it again if desired
        if (saved.getScheduleSlotId() != null) {
            scheduleRepository.findById(saved.getScheduleSlotId()).ifPresent(slot -> {
                slot.setBooked(false);
                scheduleRepository.save(slot);
            });
        }

        return saved;
    }

    private boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }
}