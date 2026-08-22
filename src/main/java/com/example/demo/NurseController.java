package com.example.demo;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/nurse")
@CrossOrigin(origins = "*")
public class NurseController {

    @Autowired
    private PatientRepository patientRepository;

    @PostMapping("/checkin")
    public Patient checkInPatient(@RequestBody Patient patient) {
        patient.setCheckInTime(LocalDateTime.now());

        int bp = patient.getSystolicBp() != null ? patient.getSystolicBp() : 0;
        double temp = patient.getTemperature() != null ? patient.getTemperature() : 0.0;
        int hr = patient.getHeartRate() != null ? patient.getHeartRate() : 0;

        if (bp > 140 || temp >= 38.5 || hr > 100) {
            patient.setUrgencyLevel("HIGH");
        } else if (bp > 120 || temp >= 37.5) {
            patient.setUrgencyLevel("MEDIUM");
        } else {
            patient.setUrgencyLevel("LOW");
        }

        return patientRepository.save(patient);
    }

    @GetMapping("/queue")
    public List<Patient> getPrioritizedQueue() {
        return patientRepository.findAllPrioritizedQueue();
    }
}