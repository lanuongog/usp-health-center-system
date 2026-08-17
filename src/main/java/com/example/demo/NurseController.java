package com.example.demo;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/nurse")
public class NurseController {

    @Autowired
    private AppointmentRepository appointmentRepository;

    @Autowired
    private CheckInRepository checkInRepository;

    // The check-in queue: appointments booked for today that have not been checked in yet
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

    @PostMapping("/checkin")
    public CheckIn checkInStudent(@RequestBody CheckInRequest request, Authentication auth) {
        Appointment appointment = appointmentRepository.findById(request.appointmentId)
                .orElseThrow(() -> new RuntimeException("Appointment not found: " + request.appointmentId));

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
}
