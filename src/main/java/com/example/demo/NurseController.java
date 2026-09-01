package com.example.demo;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/nurse")
public class NurseController {

    private final AppointmentRepository appointmentRepository;
    private final CheckInRepository checkInRepository;
    private final DoctorScheduleRepository scheduleRepository;
    private final UserRepository userRepository;

    public NurseController(AppointmentRepository appointmentRepository,
                          CheckInRepository checkInRepository,
                          DoctorScheduleRepository scheduleRepository,
                          UserRepository userRepository) {
        this.appointmentRepository = appointmentRepository;
        this.checkInRepository = checkInRepository;
        this.scheduleRepository = scheduleRepository;
        this.userRepository = userRepository;
    }

    // view students with scheduled appointments who have not yet been checked in
    @GetMapping("/queue")
    public List<Appointment> getQueue() {
        return appointmentRepository.findByStatus("BOOKED");
    }

    // every appointment record, most recent date first, enriched with the
    // student's full name, so a nurse can search for a specific student and see
    // when their appointment was and check them in even after a no-show mark
    @GetMapping("/all")
    public List<Map<String, Object>> getAllAppointments() {
        List<Appointment> appointments = appointmentRepository.findAll().stream()
                .sorted(Comparator
                        .comparing((Appointment a) -> a.getAppointmentDate(), Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(a -> a.getId(), Comparator.reverseOrder()))
                .collect(Collectors.toList());

        Map<String, String> fullNamesByUsername = userRepository.findAll().stream()
                .filter(u -> u.getFullName() != null && !u.getFullName().isBlank())
                .collect(Collectors.toMap(User::getUsername, User::getFullName, (a, b) -> a));

        return appointments.stream().map(a -> {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("id", a.getId());
            map.put("studentId", a.getStudentName());
            map.put("studentName", fullNamesByUsername.getOrDefault(a.getStudentName(), a.getStudentName()));
            map.put("appointmentDate", a.getAppointmentDate() != null ? a.getAppointmentDate().toString() : "");
            map.put("timeSlot", a.getTimeSlot());
            map.put("status", a.getStatus());
            return map;
        }).collect(Collectors.toList());
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
        // a student who was marked as a no-show can still be checked in if they
        // turn up after all, in addition to the normal booked-and-waiting case
        if (!"BOOKED".equals(appointment.getStatus()) && !"NO_SHOW".equals(appointment.getStatus())) {
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

    // today's completed check-ins, enriched with the student's and nurse's
    // full names so a doctor can see at a glance who checked a student in
    @GetMapping("/checkins")
    public List<Map<String, Object>> getAllCheckIns() {
        LocalDate today = LocalDate.now();

        Map<String, String> fullNamesByUsername = userRepository.findAll().stream()
                .filter(u -> u.getFullName() != null && !u.getFullName().isBlank())
                .collect(Collectors.toMap(User::getUsername, User::getFullName, (a, b) -> a));

        return checkInRepository.findAll().stream()
                .filter(c -> c.getCheckInTime() != null && c.getCheckInTime().toLocalDate().equals(today))
                .sorted(Comparator.comparing(CheckIn::getCheckInTime).reversed())
                .map(c -> {
                    String studentUsername = c.getAppointment() != null ? c.getAppointment().getStudentName() : null;
                    Map<String, Object> map = new LinkedHashMap<>();
                    map.put("id", c.getId());
                    map.put("studentName", fullNamesByUsername.getOrDefault(studentUsername, studentUsername));
                    map.put("temperature", c.getTemperature());
                    map.put("bloodPressure", c.getBloodPressure());
                    map.put("presentingComplaint", c.getPresentingComplaint());
                    map.put("checkInTime", c.getCheckInTime() != null ? c.getCheckInTime().toString() : null);
                    map.put("checkedInBy", fullNamesByUsername.getOrDefault(c.getCheckedInBy(), c.getCheckedInBy()));
                    return map;
                })
                .collect(Collectors.toList());
    }

    public static class NoShowRequest {
        public Long appointmentId;
    }

    // mark a booked student who never arrived as a no-show, removing them from
    // the check-in queue and freeing the schedule slot for other students
    @PostMapping("/no-show")
    public Appointment markNoShow(@RequestBody NoShowRequest request) {
        Appointment appointment = appointmentRepository.findById(request.appointmentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Student record not found"));

        if (!"BOOKED".equals(appointment.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "No valid appointment found");
        }

        appointment.setStatus("NO_SHOW");
        Appointment saved = appointmentRepository.save(appointment);

        if (saved.getAppointmentDate() != null && saved.getTimeSlot() != null) {
            scheduleRepository.setBookedForTimeSlot(saved.getAppointmentDate(), saved.getTimeSlot(), false);
        }

        return saved;
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
