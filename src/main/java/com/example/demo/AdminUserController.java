package com.example.demo;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/admin/users")
public class AdminUserController {

    private static final List<String> STAFF_ROLES = List.of("ADMIN", "DOCTOR", "NURSE");
    private static final int MIN_USERNAME_LENGTH = 3;
    private static final int MAX_USERNAME_LENGTH = 50;
    private static final int MIN_PASSWORD_LENGTH = 6;
    private static final int MAX_PASSWORD_LENGTH = 100;

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
    // this endpoint only ever returns account metadata, never
    // any patient/appointment data, even though it lives under Admin.
    public static class UserView {
        public Long id;
        public String username;
        public String role;
        public String email;
        public boolean active;
        public String fullName;
        public String country;
        public String maritalStatus;
        public Boolean feePaid;
        public Double feeAmount;
        public boolean hasReceipt;
        public String receiptRejectionReason;
        public String receiptAutoCheckNote;

        public UserView(User u) {
            this.id = u.getId();
            this.username = u.getUsername();
            this.role = u.getRole();
            this.email = u.getEmail();
            this.active = u.isActive();
            // Registration/fee fields only apply to student accounts
            if ("STUDENT".equals(u.getRole())) {
                this.fullName = u.getFullName();
                this.country = u.getCountry();
                this.maritalStatus = u.getMaritalStatus();
                this.feePaid = u.isFeePaid();
                this.feeAmount = u.getFeeAmount();
                this.hasReceipt = u.getReceiptFile() != null;
                this.receiptRejectionReason = u.getReceiptRejectionReason();
                this.receiptAutoCheckNote = u.getReceiptAutoCheckNote();
            }
        }
    }

    // view all registered accounts and their status
    @GetMapping
    public List<UserView> getAllUsers() {
        return userRepository.findAll().stream().map(UserView::new).collect(Collectors.toList());
    }

    // lets the admin user-detail page load a single account
    @GetMapping("/{id}")
    public UserView getUser(@PathVariable Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User account not found"));
        return new UserView(user);
    }

    public static class CreateUserRequest {
        public String username;
        public String password;
        public String role; // ADMIN, DOCTOR, or NURSE
        public String email; // optional; where password reset codes are sent
    }

    // "the admin is able to add ... staff ... accounts"
    @PostMapping
    public Object createUser(@RequestBody CreateUserRequest request) {
        if (request.username == null || request.username.isBlank()
                || request.password == null || request.password.isBlank()) {
            return Map.of("error", "Username and password are required.");
        }
        String username = request.username.trim();
        if (username.length() < MIN_USERNAME_LENGTH || username.length() > MAX_USERNAME_LENGTH) {
            return Map.of("error", "Username must be between " + MIN_USERNAME_LENGTH + " and " + MAX_USERNAME_LENGTH + " characters.");
        }
        if (username.matches(".*\\s.*")) {
            return Map.of("error", "Username cannot contain spaces.");
        }
        String passwordError = PasswordRules.check(request.password, username);
        if (passwordError != null) {
            return Map.of("error", passwordError);
        }
        if (request.role == null || !STAFF_ROLES.contains(request.role)) {
            return Map.of("error", "Role must be one of: ADMIN, DOCTOR, NURSE.");
        }
        if (userRepository.findByUsername(username).isPresent()) {
            return Map.of("error", "This username is already registered.");
        }
        request.username = username;
        String email = request.email == null ? "" : request.email.trim();
        if (!email.isEmpty() && (email.length() > 150 || !email.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$"))) {
            return Map.of("error", "Please enter a valid email address (or leave it blank).");
        }

        // one email address can belong to only one account
        if (!email.isEmpty() && userRepository.findAll().stream()
                .anyMatch(u -> u.getEmail() != null && u.getEmail().equalsIgnoreCase(email))) {
            return Map.of("error", "This email address is already used by another account.");
        }

        User user = new User(request.username, passwordEncoder.encode(request.password), request.role);
        if (!email.isEmpty()) user.setEmail(email);
        userRepository.save(user);

        return new UserView(user);
    }

    public static class ResetPasswordRequest {
        public String newPassword;
    }

    // lets an admin set a new password for any account (e.g. a student or
    // staff member locked out of their own account)
    @PostMapping("/{id}/reset-password")
    public Object resetPassword(@PathVariable Long id, @RequestBody ResetPasswordRequest request) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User account not found"));

        if (request.newPassword == null || request.newPassword.isBlank()) {
            return Map.of("error", "A new password is required.");
        }
        String passwordError = PasswordRules.check(request.newPassword, user.getUsername());
        if (passwordError != null) {
            return Map.of("error", passwordError);
        }

        user.setPassword(passwordEncoder.encode(request.newPassword));
        userRepository.save(user);

        return Map.of("message", "Password reset for " + user.getUsername() + ".");
    }

    public static class ToggleRequest {
        public boolean confirm = false;
    }

    // activate or deactivate an account
    @PostMapping("/{id}/toggle")
    public Map<String, Object> toggleStatus(@PathVariable Long id, @RequestBody(required = false) ToggleRequest request) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User account not found"));

        if ("ADMIN".equals(user.getRole())) {
            return Map.of("requiresConfirmation", false, "error", "Admin accounts cannot be activated or deactivated.");
        }

        boolean confirm = request != null && request.confirm;

        // warn before deactivating an account with active dependent records
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

    // lets an admin approve a student's uploaded payment receipt, unlocking booking
    @PostMapping("/{id}/verify-receipt")
    public Object verifyReceipt(@PathVariable Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User account not found"));

        if (user.getReceiptFile() == null) {
            return Map.of("error", "This account has no uploaded receipt to verify.");
        }

        user.setFeePaid(true);
        user.setReceiptRejectionReason(null);
        userRepository.save(user);

        return new UserView(user);
    }

    public static class RejectReceiptRequest {
        public String reason;
    }

    // lets an admin reject a student's uploaded payment receipt, clearing it so
    // the student can re-upload a corrected one from their dashboard
    @PostMapping("/{id}/reject-receipt")
    public Object rejectReceipt(@PathVariable Long id, @RequestBody(required = false) RejectReceiptRequest request) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User account not found"));

        if (user.getReceiptFile() == null) {
            return Map.of("error", "This account has no uploaded receipt to reject.");
        }

        if (request != null && request.reason != null && request.reason.trim().length() > 200) {
            return Map.of("error", "The rejection reason must be 200 characters or fewer.");
        }
        String reason = (request != null && request.reason != null && !request.reason.isBlank())
                ? request.reason.trim()
                : "Your receipt was rejected. Please upload a valid payment receipt.";

        // NOTE: keep the rejected receipt on file instead of deleting it
        // (DEF-03). Deleting it here left the account with nothing to act on -
        // hasReceipt showed false, and re-approving a null receipt threw a 500.
        // The student can still see their rejected receipt and the reason, and
        // StudentController.uploadReceipt() already overwrites this file when
        // they re-upload, so nothing else needs to change for that flow.
        user.setFeePaid(false);
        user.setReceiptRejectionReason(reason);
        userRepository.save(user);

        return new UserView(user);
    }

    // lets an admin download a student's uploaded payment receipt
    @GetMapping("/{id}/receipt")
    public ResponseEntity<byte[]> getReceipt(@PathVariable Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User account not found"));

        if (user.getReceiptFile() == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No receipt has been uploaded for this account");
        }

        String fileName = user.getReceiptFileName() != null ? user.getReceiptFileName() : "receipt.pdf";
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(fileName).build().toString())
                .body(user.getReceiptFile());
    }

    private boolean hasDependentRecords(String username) {
        boolean inAppointments = appointmentRepository.findAll().stream()
                .anyMatch(a -> username.equals(a.getConsultedByDoctor()));
        boolean inCheckIns = checkInRepository.findAll().stream()
                .anyMatch(c -> username.equals(c.getCheckedInBy()));
        return inAppointments || inCheckIns;
    }
}
