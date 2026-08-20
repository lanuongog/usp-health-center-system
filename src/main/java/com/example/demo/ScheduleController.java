package com.example.demo;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/schedule")
public class ScheduleController {

    @Autowired
    private DoctorScheduleRepository scheduleRepository;

    // FR-02-20 to 23: students view open (unbooked) slots for a date, optionally filtered by doctor
    @GetMapping("/available")
    public List<DoctorSchedule> getAvailable(
            @RequestParam(required = false) String doctorName,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {

        List<DoctorSchedule> slots = (doctorName != null)
                ? scheduleRepository.findByDoctorNameAndScheduleDate(doctorName, date)
                : scheduleRepository.findByScheduleDate(date);

        return slots.stream().filter(s -> !s.isBooked()).collect(Collectors.toList());
    }

    // FR-02-24: doctor/admin creates a new consultation slot
    @PostMapping("/manage")
    public DoctorSchedule createSlot(@RequestBody DoctorSchedule slot) {
        slot.setBooked(false);
        return scheduleRepository.save(slot);
    }

    // FR-02-26: doctor/admin removes a slot
    @DeleteMapping("/manage/{id}")
    public Map<String, String> deleteSlot(@PathVariable Long id) {
        scheduleRepository.deleteById(id);
        return Map.of("message", "Slot removed.");
    }
}