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

    // FR-03-29: View students with scheduled appointments who have not yet been checked in
    @GetMapping("/queue")
    public List<Appointment> getQueue() {
        return appointmentRepository.findByStatus("BOOKED");
    }

    public static class CheckInRequest {
        public Long appointmentId;
        public String studentId; // Added for walk-ins
        public String temperature;
        public String bloodPressure;
        public String presentingComplaint;
    }

    // FR-03-30/31/32/37/38/39/41/42: Check in booked student with vitals & complaint
    @PostMapping("/checkin")
    public CheckIn checkInStudent(@RequestBody CheckInRequest request, Authentication auth) {
        Appointment appointment = appointmentRepository.findById(request.appointmentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Student record not found"));

        // FR-03-32: Prevent duplicate check-in for the same visit
        if (checkInRepository.findByAppointmentId(appointment.getId()).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This student has already been checked in");
        }
        if (!"BOOKED".equals(appointment.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "No valid appointment found");
        }

        // Validate presence and numeric ranges of vitals
        validateVitals(request.temperature, request.bloodPressure, request.presentingComplaint);

        CheckIn checkIn = new CheckIn();
        checkIn.setAppointment(appointment);
        checkIn.setTemperature(request.temperature);
        checkIn.setBloodPressure(request.bloodPressure);
        checkIn.setPresentingComplaint(request.presentingComplaint);
        checkIn.setCheckInTime(LocalDateTime.now());
        checkIn.setCheckedInBy(auth != null ? auth.getName() : "Nurse");

        // Mark ready for doctor queue handoff
        appointment.setStatus("READY_FOR_DOCTOR");
        appointmentRepository.save(appointment);

        return checkInRepository.save(checkIn);
    }

    // FR-03-33 to FR-03-36: Walk-in student check-in without prior booking
    @PostMapping("/walkin")
    public CheckIn walkInCheckIn(@RequestBody CheckInRequest request, Authentication auth) {
        if (isBlank(request.studentId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Student ID is required for walk-in check-in");
        }

        // Validate vitals
        validateVitals(request.temperature, request.bloodPressure, request.presentingComplaint);

        CheckIn checkIn = new CheckIn();
        checkIn.setTemperature(request.temperature);
        checkIn.setBloodPressure(request.bloodPressure);
        checkIn.setPresentingComplaint(request.presentingComplaint);
        checkIn.setCheckInTime(LocalDateTime.now());
        checkIn.setCheckedInBy(auth != null ? auth.getName() : "Nurse");

        return checkInRepository.save(checkIn);
    }

    @GetMapping("/checkins")
    public List<CheckIn> getAllCheckIns() {
        return checkInRepository.findAll();
    }

    // Helper method for field and vital range validation (FR-03-40)
    private void validateVitals(String tempStr, String bpStr, String complaint) {
        if (isBlank(tempStr) || isBlank(bpStr) || isBlank(complaint)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Display validation messages for incomplete vital information");
        }

        try {
            double temp = Double.parseDouble(tempStr);
            if (temp < 30.0 || temp > 45.0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Temperature value is out of realistic medical range (30-45°C)");
            }
        } catch (NumberFormatException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Temperature must be a valid number");
        }

        if (!bpStr.matches("\\d{2,3}/\\d{2,3}")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Blood pressure must be in format Systolic/Diastolic (e.g., 120/80)");
        }
    }

    private boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }
}
