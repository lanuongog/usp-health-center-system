package com.example.demo;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/doctor")
public class DoctorController {

    @Autowired
    private AppointmentRepository appointmentRepository;

    @Autowired
    private CheckInRepository checkInRepository;

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
                .filter(c -> c.getAppointment().getId().equals(appointmentId))
                .map(CheckIn::getCheckInTime)
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

    // FR-04-48/49: a student's visit history
    @GetMapping("/history/{studentName}")
    public List<Appointment> getMedicalHistory(@PathVariable String studentName) {
        return appointmentRepository.findByStudentName(studentName);
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
                .orElseThrow(() -> new RuntimeException("Appointment not found: " + request.appointmentId));

        if (appointment.getConsultedByDoctor() != null &&
                !appointment.getConsultedByDoctor().equals(auth.getName())) {
            throw new RuntimeException("This consultation was already recorded by another doctor.");
        }

        appointment.setDiagnosis(request.diagnosis);
        appointment.setConsultationNotes(request.consultationNotes);
        appointment.setConsultedByDoctor(auth.getName());
        appointment.setStatus("COMPLETED");

        return appointmentRepository.save(appointment);
    }
}