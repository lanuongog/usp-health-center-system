package com.example.demo;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/nurse")
public class NurseController {

    @Autowired
    private AppointmentRepository appointmentRepository;

    @Autowired
    private CheckInRepository checkInRepository;

    // FR-03-29: view students with scheduled appointments who have not yet been checked in
    @GetMapping("/queue")
    public List<Appointment> getQueue() {
        return appointmentRepository.findByStatus("BOOKED");
    }

    public static class CheckInRequest {
        public Long appointmentId;
        public String temperature;
        public String bloodPressure;
        public String presentingComplaint;
    }

    // FR-03-30/31/32/37/38/39/41: check in a booked student, record vitals + complaint,
    // prevent the same student being checked in twice for the same visit
    @PostMapping("/checkin")
    public CheckIn checkInStudent(@RequestBody CheckInRequest request, Authentication auth) {
        Appointment appointment = appointmentRepository.findById(request.appointmentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Student record not found"));

        // FR-03-32: prevent duplicate check-in for the same visit
        if (checkInRepository.findByAppointmentId(appointment.getId()).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This student has already been checked in");
        }
        if (!"BOOKED".equals(appointment.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "No valid appointment found");
        }

        // FR-03-40 (basic validation): required fields must be present
        if (isBlank(request.temperature) || isBlank(request.bloodPressure) || isBlank(request.presentingComplaint)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Display validation messages for incomplete vital information");
        }

        CheckIn checkIn = new CheckIn();
        checkIn.setAppointment(appointment);
        checkIn.setTemperature(request.temperature);
        checkIn.setBloodPressure(request.bloodPressure);
        checkIn.setPresentingComplaint(request.presentingComplaint);
        checkIn.setCheckInTime(LocalDateTime.now());
        checkIn.setCheckedInBy(auth.getName());

        appointment.setStatus("CHECKED_IN");
        appointmentRepository.save(appointment);

        return checkInRepository.save(checkIn);
    }

    @GetMapping("/checkins")
    public List<CheckIn> getAllCheckIns() {
        return checkInRepository.findAll();
    }

    private boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }
}
