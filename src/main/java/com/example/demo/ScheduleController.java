package com.example.demo;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/schedule")
public class ScheduleController {

    @Autowired
    private DoctorScheduleRepository scheduleRepository;

    @Autowired
    private AppointmentRepository appointmentRepository;

    // Endpoint to get list of distinct doctor names for dropdown filters
    @GetMapping("/doctors")
    public List<String> getDoctors() {
        return scheduleRepository.findDistinctDoctorNames();
    }

    // FR-02-20 to 23: students view open (unbooked) slots for a date, optionally filtered by doctor
    @GetMapping("/available")
    public List<DoctorSchedule> getAvailable(
            @RequestParam(required = false) String doctorName,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {

        List<DoctorSchedule> slots = (doctorName != null && !doctorName.isBlank())
                ? scheduleRepository.findByDoctorNameAndScheduleDate(doctorName.trim(), date)
                : scheduleRepository.findByScheduleDate(date);

        List<DoctorSchedule> open = slots.stream().filter(s -> !s.isBooked()).collect(Collectors.toList());

        return open;
    }

    // FR-02-24: doctor/admin creates a new consultation slot
    @PostMapping("/manage")
    public DoctorSchedule createSlot(@RequestBody DoctorSchedule slot, Authentication auth) {
        validateOwnership(slot.getDoctorName(), auth);
        validateRequiredFields(slot);
        validateNoConflict(slot, null);

        slot.setBooked(false);
        return scheduleRepository.save(slot);
    }

    // FR-02-25: doctor/admin updates an existing consultation slot (this endpoint did not exist before)
    @PutMapping("/manage/{id}")
    public DoctorSchedule updateSlot(@PathVariable Long id, @RequestBody DoctorSchedule updated, Authentication auth) {
        DoctorSchedule existing = scheduleRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Schedule slot not found"));

        validateOwnership(existing.getDoctorName(), auth);
        validateRequiredFields(updated);
        validateNoConflict(updated, id);

        existing.setScheduleDate(updated.getScheduleDate());
        existing.setTimeSlot(updated.getTimeSlot());
        // doctorName is intentionally not changed by an update - a slot belongs to the doctor that created it

        try {
            return scheduleRepository.save(existing);
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Schedule update failed. Previous schedule has been retained");
        }
    }

    // FR-02-26: doctor/admin removes a slot
    @DeleteMapping("/manage/{id}")
    public Map<String, String> deleteSlot(@PathVariable Long id, Authentication auth) {
        DoctorSchedule existing = scheduleRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Schedule slot not found"));

        validateOwnership(existing.getDoctorName(), auth);

        scheduleRepository.deleteById(id);
        return Map.of("message", "Slot removed.");
    }

    // Doctors and admins need to see the FULL schedule (booked + open) to manage it,
    // unlike /available which only shows open slots to students.
    @GetMapping("/manage")
    public List<DoctorSchedule> getManagedSchedule(Authentication auth) {
        return scheduleRepository.findAll().stream()
                .filter(s -> auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"))
                        || s.getDoctorName().equals(auth.getName()))
                .collect(Collectors.toList());
    }

    // FR-02-28: restrict schedule management to the assigned doctor or an administrator.
    // A doctor may only manage slots under their own name; an admin may manage any doctor's slots.
    private void validateOwnership(String slotDoctorName, Authentication auth) {
        boolean isAdmin = auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        if (isAdmin) return;

        if (!auth.getName().equals(slotDoctorName)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You do not have permission to modify this schedule");
        }
    }

    // 4.2.2.4: required schedule information is missing
    private void validateRequiredFields(DoctorSchedule slot) {
        if (isBlank(slot.getDoctorName()) || slot.getScheduleDate() == null || isBlank(slot.getTimeSlot())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Display validation messages for the missing fields");
        }
    }

    // FR-02-27 / 4.2.2.4: prevent a new/updated slot from conflicting with an existing booked appointment
    // or duplicating another open slot for the same doctor, date, and time.
    private void validateNoConflict(DoctorSchedule slot, Long excludeSlotId) {
        boolean conflict = scheduleRepository
                .findByDoctorNameAndScheduleDate(slot.getDoctorName(), slot.getScheduleDate())
                .stream()
                .anyMatch(s -> !s.getId().equals(excludeSlotId) && s.getTimeSlot().equals(slot.getTimeSlot()));

        if (conflict) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This time slot conflicts with an existing booking");
        }
    }

    private boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }
}
