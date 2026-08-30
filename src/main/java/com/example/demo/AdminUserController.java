package com.example.demo;

import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/admin/users")
public class AdminUserController {

    private static final List<String> STAFF_ROLES = List.of("ADMIN", "DOCTOR", "NURSE");

    private final UserRepository userRepository;
    private final AppointmentRepository appointmentRepository;
    private final CheckInRepository checkInRepository;
    private final PasswordEncoder passwordEncoder;

    public AdminUserController(UserRepository userRepository,
                             AppointmentRepository appointmentRepository,
                             CheckInRepository checkInRepository,
                             PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.appointmentRepository = appointmentRepository;
        this.checkInRepository = checkInRepository;
        this.passwordEncoder = passwordEncoder;
    }

    // A safe view of a user account - never exposes the password hash.
    // FR-05-73: this endpoint only ever returns account metadata, never
    // any patient/appointment data, even though it lives under Admin.
    public static class UserView {
        public Long id;
        public String username;
        public String role;
        public boolean active;
        public String fullName;
        public String country;
        public String maritalStatus;
        public Boolean feePaid;
        public Double feeAmount;

        public UserView(User u) {
            this.id = u.getId();
            this.username = u.getUsername();
            this.role = u.getRole();
            this.active = u.isActive();
            // Registration/fee fields only apply to student accounts
            if ("STUDENT".equals(u.getRole())) {
                this.fullName = u.getFullName();
                this.country = u.getCountry();
                this.maritalStatus = u.getMaritalStatus();
                this.feePaid = u.isFeePaid();
                this.feeAmount = u.getFeeAmount();
            }
        }
    }

    // FR-05-68/69: view all registered accounts and their status
    @GetMapping
    public List<UserView> getAllUsers() {
        return userRepository.findAll().stream().map(UserView::new).collect(Collectors.toList());
    }

    public static class CreateUserRequest {
        public String username;
        public String password;
        public String role; // ADMIN, DOCTOR, or NURSE
    }

    // SRS 3.1 Admin Interfaces: "the admin is able to add ... staff ... accounts"
    @PostMapping
    public Object createUser(@RequestBody CreateUserRequest request) {
        if (request.username == null || request.username.isBlank()
                || request.password == null || request.password.isBlank()) {
            return Map.of("error", "Username and password are required.");
        }
        if (request.role == null || !STAFF_ROLES.contains(request.role)) {
            return Map.of("error", "Role must be one of: ADMIN, DOCTOR, NURSE.");
        }
        if (userRepository.findByUsername(request.username).isPresent()) {
            return Map.of("error", "This username is already registered.");
        }

        User user = new User(request.username, passwordEncoder.encode(request.password), request.role);
        userRepository.save(user);

        return new UserView(user);
    }

    public static class ToggleRequest {
        public boolean confirm = false;
    }

    // FR-05-70/71: activate or deactivate an account
    @PostMapping("/{id}/toggle")
    public Map<String, Object> toggleStatus(@PathVariable Long id, @RequestBody(required = false) ToggleRequest request) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User account not found"));

        boolean confirm = request != null && request.confirm;

        // 4.5.3.4: warn before deactivating an account with active dependent records
        if (user.isActive() && !confirm) {
            boolean hasDependentRecords = hasDependentRecords(user.getUsername());
            if (hasDependentRecords) {
                return Map.of(
                        "requiresConfirmation", true,
                        "message", "This account has active records. Are you sure you want to deactivate it?"
                );
            }
        }

        user.setActive(!user.isActive());
        userRepository.save(user);

        return Map.of(
                "requiresConfirmation", false,
                "id", user.getId(),
                "username", user.getUsername(),
                "active", user.isActive()
        );
    }

    private boolean hasDependentRecords(String username) {
        boolean inAppointments = appointmentRepository.findAll().stream()
                .anyMatch(a -> username.equals(a.getConsultedByDoctor()));
        boolean inCheckIns = checkInRepository.findAll().stream()
                .anyMatch(c -> username.equals(c.getCheckedInBy()));
        return inAppointments || inCheckIns;
    }
}
