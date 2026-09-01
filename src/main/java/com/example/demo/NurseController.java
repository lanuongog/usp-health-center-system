package com.example.demo;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/nurse")
public class NurseController {

    private final AppointmentRepository appointmentRepository;
    private final CheckInRepository checkInRepository;

    public NurseController(AppointmentRepository appointmentRepository,
                          CheckInRepository checkInRepository) {
        this.appointmentRepository = appointmentRepository;
        this.checkInRepository = checkInRepository;
    }

    // view students with scheduled appointments who have not yet been checked in
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

    // check in a booked student, record vitals + complaint,
    // prevent the same student being checked in twice for the same visit
    @PostMapping("/checkin")
    public CheckIn checkInStudent(@RequestBody CheckInRequest request, Authentication auth) {
        Appointment appointment = appointmentRepository.findById(request.appointmentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Student record not found"));

        // prevent duplicate check-in for the same visit
        if (checkInRepository.findByAppointmentId(appointment.getId()).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This student has already been checked in");
        }
        if (!"BOOKED".equals(appointment.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "No valid appointment found");
        }

        // required fields must be present
        if (isBlank(request.temperature) || isBlank(request.bloodPressure) || isBlank(request.presentingComplaint)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Display validation messages for incomplete vital information");
        }

        // reject out-of-range or malformed vital sign values
        validateVitals(request.temperature, request.bloodPressure);

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

    // a plausible human body temperature in Celsius
    private static final double MIN_TEMP_C = 30.0;
    private static final double MAX_TEMP_C = 43.0;

    // plausible systolic/diastolic blood pressure bounds (mmHg)
    private static final int MIN_SYSTOLIC = 60;
    private static final int MAX_SYSTOLIC = 250;
    private static final int MIN_DIASTOLIC = 40;
    private static final int MAX_DIASTOLIC = 150;

    private void validateVitals(String temperature, String bloodPressure) {
        double temp;
        try {
            temp = Double.parseDouble(temperature.trim());
        } catch (NumberFormatException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Temperature must be a number in degrees Celsius (e.g. 37.2)");
        }
        if (temp < MIN_TEMP_C || temp > MAX_TEMP_C) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Temperature is out of the valid range (" + MIN_TEMP_C + "–" + MAX_TEMP_C + "°C)");
        }

        String[] parts = bloodPressure.trim().split("/");
        if (parts.length != 2) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Blood pressure must be in the format systolic/diastolic (e.g. 120/80)");
        }

        int systolic;
        int diastolic;
        try {
            systolic = Integer.parseInt(parts[0].trim());
            diastolic = Integer.parseInt(parts[1].trim());
        } catch (NumberFormatException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Blood pressure must be in the format systolic/diastolic (e.g. 120/80)");
        }

        boolean outOfRange = systolic < MIN_SYSTOLIC || systolic > MAX_SYSTOLIC
                || diastolic < MIN_DIASTOLIC || diastolic > MAX_DIASTOLIC;
        if (outOfRange || systolic <= diastolic) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Blood pressure values are out of the valid range");
        }
    }
}
