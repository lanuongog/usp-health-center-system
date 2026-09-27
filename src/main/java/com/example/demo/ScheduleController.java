package com.example.demo;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/schedule")
public class ScheduleController {

    private final DoctorScheduleRepository scheduleRepository;

    public ScheduleController(DoctorScheduleRepository scheduleRepository) {
        this.scheduleRepository = scheduleRepository;
    }

    // students view slots for a date (booked and open), optionally filtered by doctor,
    // so the booking page can show booked times as taken rather than hiding them
    @GetMapping("/available")
    public List<DoctorSchedule> getAvailable(
            @RequestParam(required = false) String doctorName,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {

        List<DoctorSchedule> slots = (doctorName != null)
                ? scheduleRepository.findByDoctorNameAndScheduleDateAndArchivedFalse(doctorName, date)
                : scheduleRepository.findByScheduleDateAndArchivedFalse(date);

        // no appointment slots scheduled at all for the selected date
        if (slots.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No available appointments for the selected date");
        }
        return slots;
    }

    // Returns the distinct list of doctor names for the homepage search bar
    @GetMapping("/doctors")
    public List<String> getDoctorNames() {
        return scheduleRepository.findAll().stream()
                .filter(d -> !d.isArchived())
                .map((DoctorSchedule d) -> d.getDoctorName())
                .distinct()
                .collect(Collectors.toList());
    }

    // doctor/admin creates a new consultation slot
    @PostMapping("/manage")
    public DoctorSchedule createSlot(@RequestBody DoctorSchedule slot, Authentication auth) {
        validateOwnership(slot.getDoctorName(), auth);
        validateRequiredFields(slot);
        validateNoConflict(slot, null);

        // Each doctor's slot is booked independently, so a newly created slot
        // always starts open regardless of other doctors' bookings.
        slot.setBooked(false);
        return scheduleRepository.save(slot);
    }

    // doctor/admin updates an existing consultation slot (this endpoint did not exist before)
    @PutMapping("/manage/{id}")
    public DoctorSchedule updateSlot(@PathVariable Long id, @RequestBody DoctorSchedule updated, Authentication auth) {
        DoctorSchedule existing = scheduleRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Schedule slot not found"));

        validateOwnership(existing.getDoctorName(), auth);
        validateRequiredFields(updated);
        validateNoConflict(updated, id);

        existing.setScheduleDate(updated.getScheduleDate());
        existing.setTimeSlot(updated.getTimeSlot());
        // booked status is left untouched - editing a slot's date/time doesn't
        // change whether this specific row already has a student booked into it
        // doctorName is intentionally not changed by an update - a slot belongs to the doctor that created it

        try {
            return scheduleRepository.save(existing);
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Schedule update failed. Previous schedule has been retained");
        }
    }

    // doctor/admin removes a slot - archived (soft-removed), never hard-deleted,
    // so the record is preserved for audit purposes and simply hidden from
    // availability, management, and conflict checks going forward.
    @DeleteMapping("/manage/{id}")
    public Map<String, String> archiveSlot(@PathVariable Long id, Authentication auth) {
        DoctorSchedule existing = scheduleRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Schedule slot not found"));

        validateOwnership(existing.getDoctorName(), auth);

        if (existing.isBooked()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Cannot remove a slot that has an active booking.");
        }
        if (existing.isArchived()) {
            return Map.of("message", "This slot was already removed.");
        }

        existing.setArchived(true);
        scheduleRepository.save(existing);
        return Map.of("message", "Slot removed.");
    }

    // Doctors and admins need to see the FULL schedule (booked + open) to manage it,
    // unlike /available which only shows open slots to students. Archived slots are
    // excluded - they've been removed and are no longer part of the active schedule.
    @GetMapping("/manage")
    public List<DoctorSchedule> getManagedSchedule(Authentication auth) {
        return scheduleRepository.findAll().stream()
                .filter(s -> !s.isArchived())
                .filter(s -> auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"))
                        || s.getDoctorName().equals(auth.getName()))
                .collect(Collectors.toList());
    }

    // restrict schedule management to the assigned doctor or an administrator.
    // A doctor may only manage slots under their own name; an admin may manage any doctor's slots.
    private void validateOwnership(String slotDoctorName, Authentication auth) {
        boolean isAdmin = auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        if (isAdmin) return;

        if (!auth.getName().equals(slotDoctorName)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You do not have permission to modify this schedule");
        }
    }

    private static final int MAX_DOCTOR_NAME_LENGTH = 100;
    private static final int MAX_TIME_SLOT_LENGTH = 50;

    // required schedule information is missing, or exceeds a sane length
    private void validateRequiredFields(DoctorSchedule slot) {
        if (isBlank(slot.getDoctorName()) || slot.getScheduleDate() == null || isBlank(slot.getTimeSlot())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Display validation messages for the missing fields");
        }
        if (slot.getDoctorName().trim().length() > MAX_DOCTOR_NAME_LENGTH) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Doctor name must be " + MAX_DOCTOR_NAME_LENGTH + " characters or fewer.");
        }
        if (slot.getTimeSlot().trim().length() > MAX_TIME_SLOT_LENGTH) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Time slot must be " + MAX_TIME_SLOT_LENGTH + " characters or fewer.");
        }
        if (slot.getScheduleDate().isBefore(LocalDate.now())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Schedule date cannot be in the past.");
        }
    }

    // prevent a new/updated slot from conflicting with an existing booked appointment
    // or duplicating another open slot for the same doctor, date, and time.
    private void validateNoConflict(DoctorSchedule slot, Long excludeSlotId) {
        boolean conflict = scheduleRepository
                .findByDoctorNameAndScheduleDateAndArchivedFalse(slot.getDoctorName(), slot.getScheduleDate())
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