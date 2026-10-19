package com.example.demo;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
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

    // FEE_APPROVER = "Finance": approves fee changes proposed by admins (FeeSettingsService).
    private static final List<String> STAFF_ROLES = List.of("ADMIN", "DOCTOR", "NURSE", FeeSettingsService.APPROVER_ROLE);
    private static final int MIN_USERNAME_LENGTH = 3;
    private static final int MAX_USERNAME_LENGTH = 50;
    private static final int MIN_PASSWORD_LENGTH = 6;
    private static final int MAX_PASSWORD_LENGTH = 100;

    private final UserRepository userRepository;
    private final AppointmentRepository appointmentRepository;
    private final CheckInRepository checkInRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditLogService auditLogService;

    public AdminUserController(UserRepository userRepository,
                             AppointmentRepository appointmentRepository,
                             CheckInRepository checkInRepository,
                             PasswordEncoder passwordEncoder,
                             AuditLogService auditLogService) {
        this.userRepository = userRepository;
        this.appointmentRepository = appointmentRepository;
        this.checkInRepository = checkInRepository;
        this.passwordEncoder = passwordEncoder;
        this.auditLogService = auditLogService;
    }

    // A safe view of a user account - never exposes the password hash.
    // this endpoint only ever returns account metadata, never
    // any patient/appointment data, even though it lives under Admin.
    public static class UserView {
        public Long id;
        public String username;
        public String role;
        public boolean active;
        public String fullName;
        public String country;
        public String phoneNumber;
        public String email;
        public String semesterAddress;
        public String homeAddress;
        public String emergencyContactName;
        public String emergencyContactRelationship;
        public String emergencyContactPhone;
        public String registeredAt;
        public Boolean feePaid;
        public Double feeAmount;
        public boolean hasReceipt;
        public String receiptRejectionReason;
        public String receiptAutoCheckNote;

        public UserView(User u) {
            this.id = u.getId();
            this.username = u.getUsername();
            this.role = u.getRole();
            this.active = u.isActive();
            // Registration/fee fields only apply to student accounts
            if ("STUDENT".equals(u.getRole())) {
                this.fullName = u.getFullName();
                this.country = u.getCountry();
                this.phoneNumber = u.getPhoneNumber();
                this.email = u.getEmail();
                this.semesterAddress = u.getSemesterAddress();
                this.homeAddress = u.getHomeAddress();
                this.emergencyContactName = u.getEmergencyContactName();
                this.emergencyContactRelationship = u.getEmergencyContactRelationship();
                this.emergencyContactPhone = u.getEmergencyContactPhone();
                this.registeredAt = u.getRegisteredAt() != null ? u.getRegisteredAt().toString() : null;
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

    // students waiting for their payment receipt to be approved, longest wait
    // first, so the admin dashboard can alert the admin to review them
    @GetMapping("/pending-receipts")
    public List<Map<String, Object>> getPendingReceipts() {
        return userRepository.findPendingReceipts().stream()
                .sorted(Comparator.comparing((Object[] r) -> (LocalDateTime) r[4], Comparator.nullsFirst(Comparator.naturalOrder())))
                .map(r -> {
                    Map<String, Object> map = new LinkedHashMap<>();
                    map.put("id", r[0]);
                    map.put("username", r[1]);
                    map.put("fullName", r[2]);
                    map.put("feeAmount", r[3]);
                    map.put("uploadedAt", r[4] != null ? r[4].toString() : null);
                    map.put("autoCheckNote", r[5]);
                    return map;
                })
                .collect(Collectors.toList());
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
        public String role; // ADMIN, DOCTOR, NURSE, or FEE_APPROVER (Finance)
    }

    // "the admin is able to add ... staff ... accounts"
    @PostMapping
    public Object createUser(@RequestBody CreateUserRequest request, Authentication auth) {
        if (request.username == null || request.username.isBlank()
                || request.password == null || request.password.isBlank()) {
            return Map.of("error", "Username and password are required.");
        }
        String username = request.username.trim();
        if (username.length() < MIN_USERNAME_LENGTH || username.length() > MAX_USERNAME_LENGTH) {
            return Map.of("error", "Username must be between " + MIN_USERNAME_LENGTH + " and " + MAX_USERNAME_LENGTH + " characters.");
        }
        if (request.password.length() < MIN_PASSWORD_LENGTH || request.password.length() > MAX_PASSWORD_LENGTH) {
            return Map.of("error", "Password must be between " + MIN_PASSWORD_LENGTH + " and " + MAX_PASSWORD_LENGTH + " characters.");
        }
        if (request.role == null || !STAFF_ROLES.contains(request.role)) {
            return Map.of("error", "Role must be one of: ADMIN, DOCTOR, NURSE, FEE_APPROVER.");
        }
        if (userRepository.findByUsername(username).isPresent()) {
            return Map.of("error", "This username is already registered.");
        }
        request.username = username;

        User user = new User(request.username, passwordEncoder.encode(request.password), request.role);
        user.setCreatedBy(auth.getName());
        userRepository.save(user);
        // Every account created is on record, so who could act as whom is traceable.
        if (FeeSettingsService.APPROVER_ROLE.equals(request.role)) {
            auditLogService.recordAccess(auth, "FEE_APPROVER_ADDED", null,
                    "Added Finance (fee approver) account " + user.getUsername());
        } else {
            auditLogService.recordAccess(auth, "STAFF_ACCOUNT_CREATED", null,
                    "Added " + request.role + " account " + user.getUsername());
        }

        return new UserView(user);
    }

    public static class ResetPasswordRequest {
        public String newPassword;
    }

    // lets an admin set a new password for any account (e.g. a student or
    // staff member locked out of their own account)
    @PostMapping("/{id}/reset-password")
    public Object resetPassword(@PathVariable Long id, @RequestBody ResetPasswordRequest request, Authentication auth) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User account not found"));
        // An admin taking over an approver account would defeat the fee two-person rule.
        if (FeeSettingsService.APPROVER_ROLE.equals(user.getRole())) {
            return Map.of("error", "Admins can't reset a Finance account's password. The account holder changes it after signing in.");
        }

        if (request.newPassword == null || request.newPassword.isBlank()) {
            return Map.of("error", "A new password is required.");
        }
        if (request.newPassword.length() < MIN_PASSWORD_LENGTH || request.newPassword.length() > MAX_PASSWORD_LENGTH) {
            return Map.of("error", "Password must be between " + MIN_PASSWORD_LENGTH + " and " + MAX_PASSWORD_LENGTH + " characters.");
        }

        user.setPassword(passwordEncoder.encode(request.newPassword));
        userRepository.save(user);
        auditLogService.recordAccess(auth, "PASSWORD_RESET", "STUDENT".equals(user.getRole()) ? user.getUsername() : null,
                "Reset the password of " + user.getRole() + " account " + user.getUsername());

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

        String reason = (request != null && request.reason != null && !request.reason.isBlank())
                ? request.reason.trim()
                : "Your receipt was rejected. Please upload a valid payment receipt.";

        if (reason.length() > 200) {
            return Map.of("error", "Rejection reason must be 200 characters or fewer.");
        }

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
