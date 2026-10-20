package com.example.demo;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
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
    private final AppointmentRepository appointmentRepository;
    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private final AuditLogService auditLogService;
    private final DoctorAbsenceRepository absenceRepository;
    private final DoctorAbsenceStudentRepository absenceStudentRepository;

    public ScheduleController(DoctorScheduleRepository scheduleRepository,
                              AppointmentRepository appointmentRepository,
                              NotificationRepository notificationRepository,
                              UserRepository userRepository,
                              AuditLogService auditLogService,
                              DoctorAbsenceRepository absenceRepository,
                              DoctorAbsenceStudentRepository absenceStudentRepository) {
        this.scheduleRepository = scheduleRepository;
        this.appointmentRepository = appointmentRepository;
        this.notificationRepository = notificationRepository;
        this.userRepository = userRepository;
        this.auditLogService = auditLogService;
        this.absenceRepository = absenceRepository;
        this.absenceStudentRepository = absenceStudentRepository;
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

    private static final int MAX_OPEN_DATES_RANGE_DAYS = 120;

    // Days that have at least one bookable time (open, not removed, not already
    // past), each with its earliest open time. Lets the booking calendar mark
    // which days have a doctor and point students to the next available day,
    // instead of leaving them clicking through empty dates. Uses the same rules
    // as booking, so a suggested day always has a time that can actually be booked.
    @GetMapping("/open-dates")
    public List<Map<String, Object>> getOpenDates(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        LocalDate start = from.isBefore(LocalDate.now()) ? LocalDate.now() : from;
        LocalDate end = to.isAfter(start.plusDays(MAX_OPEN_DATES_RANGE_DAYS)) ? start.plusDays(MAX_OPEN_DATES_RANGE_DAYS) : to;
        if (end.isBefore(start)) return List.of();

        Map<LocalDate, DoctorSchedule> earliestByDate = new java.util.TreeMap<>();
        for (DoctorSchedule slot : scheduleRepository.findByScheduleDateBetweenAndArchivedFalseAndBookedFalse(start, end)) {
            if (BookingController.isSlotInPast(slot)) continue;
            earliestByDate.merge(slot.getScheduleDate(), slot, (a, b) -> {
                LocalTime ta = BookingController.slotStartTime(a);
                LocalTime tb = BookingController.slotStartTime(b);
                if (ta == null) return b;
                return (tb != null && tb.isBefore(ta)) ? b : a;
            });
        }
        return earliestByDate.values().stream()
                .map(slot -> Map.<String, Object>of("date", slot.getScheduleDate().toString(), "firstTime", slot.getTimeSlot()))
                .collect(Collectors.toList());
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

    // doctor/admin creates a new consultation slot. A range longer than one
    // appointment (e.g. 09:30-18:00 from an older page) is working hours, so it is
    // split into 30-minute slots rather than saved as one all-day appointment.
    @PostMapping("/manage")
    public Object createSlot(@RequestBody DoctorSchedule slot, Authentication auth) {
        validateOwnership(slot.getDoctorName(), auth);
        validateRequiredFields(slot);

        LocalTime[] range = SlotPlanner.parseRange(slot.getTimeSlot());
        if (range != null && SlotPlanner.minutesOf(range) > SlotPlanner.MAX_APPOINTMENT_MINUTES) {
            return createSplitSlots(slot.getDoctorName().trim(), slot.getScheduleDate(),
                    range[0], range[1], SlotPlanner.DEFAULT_MINUTES, auth);
        }

        validateNoConflict(slot, null);
        // Each doctor's slot is booked independently, so a newly created slot
        // always starts open regardless of other doctors' bookings.
        slot.setBooked(false);
        DoctorSchedule createdSlot = scheduleRepository.save(slot);
        auditLogService.recordAccess(auth, "SCHEDULE_SLOT_CREATED", null,
                createdSlot.getDoctorName() + " " + createdSlot.getScheduleDate() + " " + createdSlot.getTimeSlot());
        return createdSlot;
    }

    public static class SlotRangeRequest {
        public String doctorName;
        public LocalDate scheduleDate;
        public String startTime;   // "10:00" (24h)
        public String endTime;     // "14:00" (24h)
        public Integer slotMinutes; // appointment length, defaults to 30
    }

    private static final Set<Integer> ALLOWED_SLOT_MINUTES = Set.of(15, 20, 30, 45, 60);
    private static final int MAX_SLOTS_PER_REQUEST = 48;

    // doctor/admin enters working hours for a day (e.g. 10:00-14:00) and they are
    // split into individual appointment slots, one student each (see SlotPlanner).
    @PostMapping("/manage/range")
    public Map<String, Object> createSlotRange(@RequestBody SlotRangeRequest request, Authentication auth) {
        if (isBlank(request.doctorName) || request.scheduleDate == null
                || isBlank(request.startTime) || isBlank(request.endTime)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Complete the doctor, date, start and end fields.");
        }
        String doctorName = request.doctorName.trim();
        validateOwnership(doctorName, auth);
        if (doctorName.length() > MAX_DOCTOR_NAME_LENGTH) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Doctor name must be " + MAX_DOCTOR_NAME_LENGTH + " characters or fewer.");
        }
        if (request.scheduleDate.isBefore(LocalDate.now())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Schedule date cannot be in the past.");
        }

        LocalTime start;
        LocalTime end;
        try {
            start = LocalTime.parse(request.startTime.trim());
            end = LocalTime.parse(request.endTime.trim());
        } catch (DateTimeParseException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Start and end must be valid times.");
        }
        if (!end.isAfter(start)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The end time must be after the start time.");
        }
        int minutes = request.slotMinutes == null ? SlotPlanner.DEFAULT_MINUTES : request.slotMinutes;
        if (!ALLOWED_SLOT_MINUTES.contains(minutes)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Appointment length must be 15, 20, 30, 45 or 60 minutes.");
        }
        return createSplitSlots(doctorName, request.scheduleDate, start, end, minutes, auth);
    }

    // Times in the lunch break, already passed today, or overlapping one of the
    // doctor's existing slots are skipped rather than failing the whole request.
    private Map<String, Object> createSplitSlots(String doctorName, LocalDate date, LocalTime start, LocalTime end, int minutes, Authentication auth) {
        List<LocalTime[]> existing = scheduleRepository
                .findByDoctorNameAndScheduleDateAndArchivedFalse(doctorName, date).stream()
                .map(s -> SlotPlanner.parseRange(s.getTimeSlot()))
                .filter(r -> r != null)
                .toList();
        LocalTime notAfter = date.equals(LocalDate.now()) ? LocalTime.now() : null;
        SlotPlanner.Plan plan = SlotPlanner.plan(start, end, minutes, existing, notAfter);

        if (plan.timeSlots().size() > MAX_SLOTS_PER_REQUEST) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "That would create more than " + MAX_SLOTS_PER_REQUEST + " slots. Choose a shorter range.");
        }
        if (plan.timeSlots().isEmpty()) {
            String why = plan.skippedOverlap() > 0 ? "those times overlap slots you already have"
                    : plan.skippedLunch() > 0 ? "those times fall in the 1–2 PM lunch break"
                    : plan.skippedPast() > 0 ? "those times have already passed"
                    : "the range is shorter than one appointment";
            throw new ResponseStatusException(HttpStatus.CONFLICT, "No slots were created because " + why + ".");
        }

        List<DoctorSchedule> toCreate = plan.timeSlots().stream().map(timeSlot -> {
            DoctorSchedule slot = new DoctorSchedule();
            slot.setDoctorName(doctorName);
            slot.setScheduleDate(date);
            slot.setTimeSlot(timeSlot);
            slot.setBooked(false);
            return slot;
        }).toList();
        List<DoctorSchedule> saved = scheduleRepository.saveAll(toCreate);
        auditLogService.recordAccess(auth, "SCHEDULE_SLOTS_CREATED", null,
                doctorName + " " + date + ": " + saved.size() + " slot(s) " + start + "-" + end);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("created", saved.size());
        result.put("skippedLunch", plan.skippedLunch());
        result.put("skippedPast", plan.skippedPast());
        result.put("skippedOverlap", plan.skippedOverlap());
        result.put("slots", saved);
        return result;
    }

    // doctor/admin updates an existing consultation slot (this endpoint did not exist before)
    @PutMapping("/manage/{id}")
    public DoctorSchedule updateSlot(@PathVariable Long id, @RequestBody DoctorSchedule updated, Authentication auth) {
        DoctorSchedule existing = scheduleRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Schedule slot not found"));

        validateOwnership(existing.getDoctorName(), auth);
        validateRequiredFields(updated);
        validateNoConflict(updated, id);

        // A booked slot belongs to a student's appointment, which keeps its own copy of the
        // date and time. Moving the slot would leave the student looking at the old time
        // (and never told), so a booked slot can't be moved: use "Mark unavailable" instead.
        boolean timeChanged = !existing.getScheduleDate().equals(updated.getScheduleDate())
                || !existing.getTimeSlot().equals(updated.getTimeSlot());
        if (existing.isBooked() && timeChanged) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "A student is booked into this slot, so its date or time can't be changed. "
                            + "Use \"Mark unavailable\" to cancel and notify the student instead.");
        }
        String before = existing.getScheduleDate() + " " + existing.getTimeSlot();

        existing.setScheduleDate(updated.getScheduleDate());
        existing.setTimeSlot(updated.getTimeSlot());
        // booked status is left untouched - editing a slot's date/time doesn't
        // change whether this specific row already has a student booked into it
        // doctorName is intentionally not changed by an update - a slot belongs to the doctor that created it

        try {
            DoctorSchedule savedSlot = scheduleRepository.save(existing);
            auditLogService.recordAccess(auth, "SCHEDULE_SLOT_UPDATED", null,
                    existing.getDoctorName() + ": " + before + " -> " + savedSlot.getScheduleDate() + " " + savedSlot.getTimeSlot());
            return savedSlot;
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
        auditLogService.recordAccess(auth, "SCHEDULE_SLOT_REMOVED", null,
                existing.getDoctorName() + " " + existing.getScheduleDate() + " " + existing.getTimeSlot());
        return Map.of("message", "Slot removed.");
    }

    static final String DOCTOR_UNAVAILABLE_REASON = "Doctor unavailable";
    private static final DateTimeFormatter NOTICE_DATE_FORMAT = DateTimeFormatter.ofPattern("EEE d MMM", Locale.ENGLISH);
    private static final DateTimeFormatter NOTICE_TIME_FORMAT = DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH);

    public static class UnavailableRequest {
        public String doctorName;
        public LocalDate date;
    }

    // doctor/admin marks a doctor as not working on a day (e.g. off sick). Every
    // remaining time that day is removed, each booked student's appointment is
    // cancelled, and each of them gets an in-app notice pointing them to rebook
    // or go to Raiwaqa / CWM. Times already passed and students already checked
    // in are left alone - staff deal with those in person. The response lists
    // the affected students' contact details so a nurse can follow up by phone.
    @PostMapping("/manage/unavailable")
    @Transactional
    public Map<String, Object> markDoctorUnavailable(@RequestBody UnavailableRequest request, Authentication auth) {
        if (isBlank(request.doctorName) || request.date == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Choose the doctor and the date.");
        }
        String doctorName = request.doctorName.trim();
        validateOwnership(doctorName, auth);
        if (request.date.isBefore(LocalDate.now())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "That day has already passed.");
        }

        List<DoctorSchedule> remaining = scheduleRepository
                .findByDoctorNameAndScheduleDateAndArchivedFalse(doctorName, request.date).stream()
                .filter(s -> !BookingController.isSlotInPast(s))
                .toList();
        if (remaining.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "There are no remaining times for this doctor on that day.");
        }

        Map<Long, Appointment> activeBySlot = new java.util.HashMap<>();
        for (Appointment a : appointmentRepository.findByScheduleSlotIdIn(remaining.stream().map(DoctorSchedule::getId).toList())) {
            if ("BOOKED".equals(a.getStatus()) || "CHECKED_IN".equals(a.getStatus())) {
                activeBySlot.put(a.getScheduleSlotId(), a);
            }
        }

        DoctorAbsence absence = absenceRepository.findByDoctorNameAndAbsenceDate(doctorName, request.date)
                .orElseGet(() -> {
                    DoctorAbsence created = new DoctorAbsence();
                    created.setDoctorName(doctorName);
                    created.setAbsenceDate(request.date);
                    return created;
                });
        absence.setMarkedBy(auth.getName());
        absence.setMarkedAt(LocalDateTime.now());
        absence = absenceRepository.save(absence);

        int removed = 0;
        int keptCheckedIn = 0;
        List<Map<String, Object>> affected = new ArrayList<>();
        for (DoctorSchedule slot : remaining) {
            Appointment appointment = activeBySlot.get(slot.getId());
            if (appointment != null && "CHECKED_IN".equals(appointment.getStatus())) {
                keptCheckedIn++;
                continue;
            }
            slot.setArchived(true);
            slot.setBooked(false);
            scheduleRepository.save(slot);
            removed++;
            if (appointment == null) continue;

            appointment.setStatus("CANCELLED");
            appointment.setCancellationReason(DOCTOR_UNAVAILABLE_REASON);
            appointmentRepository.save(appointment);

            String studentId = appointment.getStudentName();
            User student = userRepository.findByUsername(studentId).orElse(null);
            String fullName = student != null && student.getFullName() != null ? student.getFullName() : studentId;

            Notification notice = new Notification();
            notice.setType("APPOINTMENT_CANCELLED");
            notice.setRecipientUsername(studentId);
            notice.setStudentUsername(studentId);
            notice.setStudentName(fullName);
            notice.setAppointmentId(appointment.getId());
            notice.setMessage(doctorUnavailableMessage(slot));
            notice.setCreatedAt(LocalDateTime.now());
            notificationRepository.save(notice);

            auditLogService.recordAccess(auth, "APPOINTMENT_CANCELLED_DOCTOR_UNAVAILABLE", studentId,
                    "Appointment #" + appointment.getId() + " on " + slot.getScheduleDate() + " " + slot.getTimeSlot()
                            + " (" + doctorName + ")");

            DoctorAbsenceStudent absenceStudent = new DoctorAbsenceStudent();
            absenceStudent.setAbsenceId(absence.getId());
            absenceStudent.setAppointmentId(appointment.getId());
            absenceStudent.setStudentId(studentId);
            absenceStudent.setFullName(fullName);
            absenceStudent.setTimeSlot(slot.getTimeSlot());
            absenceStudentRepository.save(absenceStudent);

            Map<String, Object> row = new LinkedHashMap<>();
            row.put("studentId", studentId);
            row.put("fullName", fullName);
            row.put("phoneNumber", student != null ? student.getPhoneNumber() : null);
            row.put("email", student != null ? student.getEmail() : null);
            row.put("timeSlot", slot.getTimeSlot());
            affected.add(row);
        }

        absence.setRemovedSlots(absence.getRemovedSlots() + removed);
        absence.setCancelledCount(absence.getCancelledCount() + affected.size());
        absence.setKeptCheckedIn(absence.getKeptCheckedIn() + keptCheckedIn);
        absenceRepository.save(absence);
        // One line for the whole action, even when nobody had booked yet.
        auditLogService.recordAccess(auth, "DOCTOR_MARKED_UNAVAILABLE", null,
                doctorName + " on " + request.date + ": removed " + removed + " time(s), cancelled "
                        + affected.size() + " appointment(s)" + (keptCheckedIn > 0 ? ", " + keptCheckedIn + " already checked in" : ""));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("removedSlots", removed);
        result.put("cancelled", affected.size());
        result.put("keptCheckedIn", keptCheckedIn);
        result.put("affected", affected);
        return result;
    }

    // e.g. "Your appointment on Tue 21 Oct at 10:00 AM has been cancelled because ..."
    private static String doctorUnavailableMessage(DoctorSchedule slot) {
        LocalTime start = BookingController.slotStartTime(slot);
        String time = start != null ? start.format(NOTICE_TIME_FORMAT) : slot.getTimeSlot();
        return "Your appointment on " + slot.getScheduleDate().format(NOTICE_DATE_FORMAT) + " at " + time
                + " has been cancelled because no doctor is available that day. Please book another day"
                + " or visit Raiwaqa Health Centre or CWM Hospital with your student ID.";
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