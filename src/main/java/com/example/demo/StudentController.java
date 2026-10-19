package com.example.demo;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/student")
public class StudentController {

    // The 12 Pacific Island member countries. Fiji is the "local" fee tier; every
    // other member country is billed at the "regional" rate.
    private static final String LOCAL_COUNTRY = "Fiji";
    private static final List<String> MEMBER_COUNTRIES = List.of(
            "Fiji", "Samoa", "Vanuatu", "Solomon Islands", "Cook Islands",
            "Tonga", "Tuvalu", "Niue", "Kiribati", "Marshall Islands", "Nauru", "Tokelau"
    );
    private static final long MAX_RECEIPT_SIZE_BYTES = 5L * 1024 * 1024;
    // Official USP student ID format: "S" followed by exactly 8 digits.
    private static final Pattern STUDENT_ID_PATTERN = Pattern.compile("^S11\\d{6}$");
    private static final int MIN_PASSWORD_LENGTH = 6;
    private static final int MAX_PASSWORD_LENGTH = 100;
    private static final int MAX_FULL_NAME_LENGTH = 100;
    private static final int MAX_EMERGENCY_REASON_LENGTH = 300;
    private static final int MAX_ADDRESS_LENGTH = 200;
    private static final int MAX_ADDRESS_WORDS = 30;
    private static final int MAX_RELATIONSHIP_LENGTH = 50;
    // Digits plus the usual separators (+, spaces, dashes, brackets); the digit
    // count is checked separately so "+679 123 4567" and "1234567" both pass.
    private static final Pattern PHONE_PATTERN = Pattern.compile("^\\+?[0-9 ()-]+$");
    private static final int MIN_PHONE_DIGITS = 7;
    private static final int MAX_PHONE_DIGITS = 15;
    // Deliberately loose: one "@", no spaces, and a dot in the domain part.
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    private static final int MAX_EMAIL_LENGTH = 254;

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final NotificationRepository notificationRepository;
    private final ReceiptVerificationService receiptVerificationService;
    private final DelayNoticeService delayNoticeService;
    private final FeeSettingsService feeSettingsService;
    private final AuditLogService auditLogService;

    public StudentController(UserRepository userRepository, PasswordEncoder passwordEncoder,
                              NotificationRepository notificationRepository,
                              ReceiptVerificationService receiptVerificationService,
                              DelayNoticeService delayNoticeService,
                              FeeSettingsService feeSettingsService,
                              AuditLogService auditLogService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.notificationRepository = notificationRepository;
        this.receiptVerificationService = receiptVerificationService;
        this.delayNoticeService = delayNoticeService;
        this.feeSettingsService = feeSettingsService;
        this.auditLogService = auditLogService;
    }

    // student self-registration logic
    @PostMapping("/register")
    public Object register(@RequestParam String studentId,
                            @RequestParam String password,
                            @RequestParam String fullName,
                            @RequestParam String country,
                            @RequestParam String phoneNumber,
                            @RequestParam String email,
                            @RequestParam String semesterAddress,
                            @RequestParam String homeAddress,
                            @RequestParam String emergencyContactName,
                            @RequestParam String emergencyContactRelationship,
                            @RequestParam String emergencyContactPhone,
                            @RequestParam MultipartFile receipt) {
        if (studentId == null || studentId.isBlank() || password == null || password.isBlank()) {
            return Map.of("error", "Student ID and password are required.");
        }
        String normalizedStudentId = studentId.trim().toUpperCase();
        if (!STUDENT_ID_PATTERN.matcher(normalizedStudentId).matches()) {
            return Map.of("error", "Student ID must be in the format S followed by 8 digits (e.g. S12345678).");
        }
        studentId = normalizedStudentId;
        if (password.length() < MIN_PASSWORD_LENGTH || password.length() > MAX_PASSWORD_LENGTH) {
            return Map.of("error", "Password must be between " + MIN_PASSWORD_LENGTH + " and " + MAX_PASSWORD_LENGTH + " characters.");
        }
        if (fullName == null || fullName.isBlank()) {
            return Map.of("error", "Full name is required.");
        }
        if (fullName.trim().length() > MAX_FULL_NAME_LENGTH) {
            return Map.of("error", "Full name must be " + MAX_FULL_NAME_LENGTH + " characters or fewer.");
        }
        if (country == null || !MEMBER_COUNTRIES.contains(country)) {
            return Map.of("error", "Please select a valid country.");
        }
        String contactError = validateContactDetails(phoneNumber, email, semesterAddress, homeAddress,
                emergencyContactName, emergencyContactRelationship, emergencyContactPhone);
        if (contactError != null) {
            return Map.of("error", contactError);
        }
        if (receipt == null || receipt.isEmpty()) {
            return Map.of("error", "Please attach a PDF receipt to prove you have paid your Laucala Health Services Fee before registering.");
        }
        String receiptError = validateReceipt(receipt);
        if (receiptError != null) {
            return Map.of("error", receiptError);
        }

        if (userRepository.findByUsername(studentId).isPresent()) {
            return Map.of("error", "This student ID is already registered. Please log in.");
        }

        // The admin-set fee is copied onto the student now, so later fee changes
        // don't alter what an already-registered student owes.
        FeeSettings fees = feeSettingsService.current();
        double feeAmount = LOCAL_COUNTRY.equals(country) ? fees.getLocalFee() : fees.getRegionalFee();

        // encrypts student password
        User student = new User(studentId, passwordEncoder.encode(password), "STUDENT");
        student.setFullName(fullName.trim());
        student.setCountry(country);
        student.setPhoneNumber(phoneNumber.trim());
        student.setEmail(email.trim().toLowerCase());
        student.setSemesterAddress(semesterAddress.trim());
        student.setHomeAddress(homeAddress.trim());
        student.setEmergencyContactName(emergencyContactName.trim());
        student.setEmergencyContactRelationship(emergencyContactRelationship.trim());
        student.setEmergencyContactPhone(emergencyContactPhone.trim());
        student.setFeeAmount(feeAmount);
        student.setFeePaid(false);
        student.setRegisteredAt(LocalDateTime.now());
        byte[] receiptBytes;
        try {
            receiptBytes = receipt.getBytes();
            student.setReceiptFile(receiptBytes);
            student.setReceiptFileName(receipt.getOriginalFilename());
            student.setReceiptUploadedAt(java.time.LocalDateTime.now());
        } catch (IOException e) {
            return Map.of("error", "Unable to read the uploaded receipt. Please try again.");
        }

        // Best-effort automatic check: if the receipt's own text contains the
        // expected fee amount and the student's name, skip the admin queue
        // entirely. Anything less certain falls back to manual review exactly
        // as before - this never blocks registration, only speeds up the
        // clear-cut cases.
        ReceiptVerificationResult check = receiptVerificationService.verify(receiptBytes, feeAmount, fullName.trim(), studentId);
        student.setReceiptAutoCheckNote(check.summary());
        student.setFeePaid(check.isAutoApproved());

        userRepository.save(student);

        // Contact and address details stay on the account itself; the audit
        // entry only records that the registration happened.
        auditLogService.record(studentId, "STUDENT", "STUDENT_REGISTERED", studentId,
                "Self-registered as " + student.getFullName() + " (" + country + "). Fee FJ$"
                        + String.format("%.2f", feeAmount) + ", receipt "
                        + (student.isFeePaid() ? "auto-verified." : "pending admin review."));

        String message = student.isFeePaid()
                ? "Registration successful! Your payment receipt was automatically verified - you can log in and book appointments right away."
                : "Registration successful. You can now log in. Your payment receipt is pending admin verification before you can book appointments.";

        return Map.of(
                "message", message,
                "studentId", studentId,
                "feeAmount", feeAmount,
                "feePaid", student.isFeePaid()
        );
    }

    // Lets the registration form show the fee for a country before submitting.
    @GetMapping("/fee-schedule")
    public Map<String, Object> getFeeSchedule() {
        FeeSettings fees = feeSettingsService.current();
        return Map.of(
                "countries", MEMBER_COUNTRIES,
                "localCountry", LOCAL_COUNTRY,
                "localFee", fees.getLocalFee(),
                "regionalFee", fees.getRegionalFee()
        );
    }

    // Lets a logged-in student see their own registration/fee details.
    @GetMapping("/profile")
    public Object getProfile(Authentication auth) {
        return userRepository.findByUsername(auth.getName())
                .<Object>map(u -> Map.of(
                        "fullName", u.getFullName() != null ? u.getFullName() : "",
                        "country", u.getCountry() != null ? u.getCountry() : "",
                        "feePaid", u.isFeePaid(),
                        "feeAmount", u.getFeeAmount() != null ? u.getFeeAmount() : 0.0,
                        "hasReceipt", u.getReceiptFile() != null,
                        "receiptRejectionReason", u.getReceiptRejectionReason() != null ? u.getReceiptRejectionReason() : ""
                ))
                .orElse(Map.of("error", "Account not found"));
    }

    // lets a student (re-)upload their payment receipt, e.g. after an admin rejects it
    @PostMapping("/receipt")
    public Object uploadReceipt(@RequestParam MultipartFile receipt, Authentication auth) {
        User student = userRepository.findByUsername(auth.getName())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Account not found"));

        if (receipt == null || receipt.isEmpty()) {
            return Map.of("error", "Please attach a PDF receipt.");
        }
        String receiptError = validateReceipt(receipt);
        if (receiptError != null) {
            return Map.of("error", receiptError);
        }

        byte[] receiptBytes;
        try {
            receiptBytes = receipt.getBytes();
            student.setReceiptFile(receiptBytes);
            student.setReceiptFileName(receipt.getOriginalFilename());
            student.setReceiptUploadedAt(java.time.LocalDateTime.now());
        } catch (IOException e) {
            return Map.of("error", "Unable to read the uploaded receipt. Please try again.");
        }
        student.setReceiptRejectionReason(null);

        double feeAmount = student.getFeeAmount() != null ? student.getFeeAmount() : 0.0;
        ReceiptVerificationResult check = receiptVerificationService.verify(receiptBytes, feeAmount, student.getFullName(), student.getUsername());
        student.setReceiptAutoCheckNote(check.summary());
        student.setFeePaid(check.isAutoApproved());

        userRepository.save(student);

        String message = student.isFeePaid()
                ? "Receipt uploaded and automatically verified - you can book appointments now."
                : "Receipt uploaded. It is now pending admin verification.";
        return Map.of("message", message);
    }

    // A student's own alerts - currently just "your appointment may be delayed"
    // notices raised when a nurse/doctor completes an emergency check-in ahead
    // of a student's existing booking for the same day.
    @GetMapping("/notifications")
    public List<Notification> getMyNotifications(Authentication auth) {
        return notificationRepository.findByRecipientUsernameOrderByCreatedAtDesc(auth.getName());
    }

    @GetMapping("/notifications/unread-count")
    public Map<String, Long> getMyUnreadCount(Authentication auth) {
        return Map.of("count", notificationRepository.countByRecipientUsernameAndReadFalse(auth.getName()));
    }

    @PostMapping("/notifications/{id}/read")
    public Object markMyNotificationRead(@PathVariable Long id, Authentication auth) {
        Notification notification = notificationRepository.findById(id).orElse(null);
        if (notification == null || !auth.getName().equals(notification.getRecipientUsername())) {
            return Map.of("error", "Notification not found.");
        }
        notification.setRead(true);
        return notificationRepository.save(notification);
    }

    public static class EmergencyAlertRequest {
        public String reason; // optional - the portal sends the alert with one click and no details
    }

    // Guards the "one emergency alert per day" check-then-save so a double
    // click (two requests arriving together) can't slip two alerts through.
    private final Object emergencyAlertLock = new Object();

    private boolean hasSentEmergencyAlertToday(String username) {
        return notificationRepository.existsByTypeAndStudentUsernameAndCreatedAtGreaterThanEqual(
                "EMERGENCY_ALERT", username, LocalDate.now().atStartOfDay());
    }

    private Notification findActiveEmergencyAlert(String username) {
        return notificationRepository.findByTypeAndStudentUsernameAndResolvedAtIsNullOrderByCreatedAtDesc(
                "EMERGENCY_ALERT", username).stream()
                .filter(Notification::isActiveEmergency)
                .findFirst()
                .orElse(null);
    }

    // Drives the portal's emergency panel: whether an alert is active, and
    // whether a nurse/doctor has acknowledged it yet. Also reports the most
    // recent alert from today once it's been resolved, so the student sees
    // that it was dealt with.
    @GetMapping("/emergency-alert/status")
    public Map<String, Object> getEmergencyAlertStatus(Authentication auth) {
        Notification alert = findActiveEmergencyAlert(auth.getName());
        if (alert == null) {
            alert = notificationRepository.findFirstByTypeAndStudentUsernameAndCreatedAtGreaterThanEqualOrderByCreatedAtDesc(
                    "EMERGENCY_ALERT", auth.getName(), LocalDate.now().atStartOfDay());
        }
        if (alert == null) {
            return Map.of("active", false, "sentToday", false);
        }
        Map<String, Object> status = new java.util.HashMap<>();
        status.put("active", alert.isActiveEmergency());
        status.put("sentToday", hasSentEmergencyAlertToday(auth.getName()));
        status.put("sentAt", alert.getCreatedAt().toString());
        status.put("acknowledgedBy", alert.getAcknowledgedBy());
        status.put("acknowledgedAt", alert.getAcknowledgedAt() != null ? alert.getAcknowledgedAt().toString() : null);
        status.put("resolvedAt", alert.getResolvedAt() != null ? alert.getResolvedAt().toString() : null);
        status.put("cancelledByStudent", alert.isCancelledByStudent());
        return status;
    }

    // One-click emergency button. No form to fill in - in an emergency the
    // student shouldn't have to type anything. Staff get the student's name,
    // ID and phone number so they can call or find them. It does NOT create an
    // appointment or check the student in; that still happens through the
    // nurse/doctor Emergency Check-in once the student arrives, which also
    // resolves this alert. Limited to one alert per student per day - a
    // cancelled alert still counts.
    @PostMapping("/emergency-alert")
    public Object sendEmergencyAlert(@RequestBody(required = false) EmergencyAlertRequest request, Authentication auth) {
        String reason = (request == null || request.reason == null) ? "" : request.reason.trim();
        if (reason.length() > MAX_EMERGENCY_REASON_LENGTH) {
            return Map.of("error", "Description must be " + MAX_EMERGENCY_REASON_LENGTH + " characters or fewer.");
        }

        User student = userRepository.findByUsername(auth.getName()).orElse(null);
        String displayName = (student != null && student.getFullName() != null && !student.getFullName().isBlank())
                ? student.getFullName() : auth.getName();
        String phone = (student != null && student.getPhoneNumber() != null && !student.getPhoneNumber().isBlank())
                ? student.getPhoneNumber() : "not on file";

        synchronized (emergencyAlertLock) {
            if (findActiveEmergencyAlert(auth.getName()) != null) {
                return Map.of("error", "Your emergency alert is already active - nurses and doctors have been "
                        + "notified. If this is life-threatening, contact campus security or emergency services immediately.");
            }
            if (hasSentEmergencyAlertToday(auth.getName())) {
                return Map.of("error", "You have already used your emergency alert for today. If this is "
                        + "life-threatening, contact campus security or emergency services immediately, or go "
                        + "straight to the Health Center.");
            }

            Notification alert = new Notification();
            alert.setType("EMERGENCY_ALERT");
            alert.setStudentUsername(auth.getName());
            alert.setStudentName(displayName);
            alert.setMessage("EMERGENCY ALERT from " + displayName + " (" + auth.getName() + "), phone " + phone
                    + (reason.isEmpty() ? "" : ": " + reason)
                    + " — the student needs help now. Call them or check the Health Center for their arrival.");
            alert.setCreatedAt(LocalDateTime.now());
            notificationRepository.save(alert);
        }

        // Other students booked today are NOT told of a delay here - this may be
        // a false alarm. A nurse/doctor sends that notice once they know.

        return Map.of("message", "Emergency alert sent. Nurses and doctors have been notified. If this is "
                + "life-threatening, also contact campus security or emergency services immediately.");
    }

    // For an alert sent by mistake. Staff aren't left wondering where it went:
    // every nurse/doctor screen is told it was a false alarm, the alert stays on
    // record as cancelled, and the cancel is audit-logged.
    @PostMapping("/emergency-alert/cancel")
    public Object cancelEmergencyAlert(Authentication auth) {
        Notification alert = findActiveEmergencyAlert(auth.getName());
        if (alert == null) {
            return Map.of("error", "You have no active emergency alert.");
        }
        alert.resolve(auth.getName());
        alert.setMessage(alert.getMessage() + " [CANCELLED by the student at "
                + alert.getResolvedAt().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm")) + " - false alarm]");
        notificationRepository.save(alert);
        auditLogService.record(auth.getName(), "STUDENT", "EMERGENCY_CANCELLED", auth.getName(),
                "Alert #" + alert.getId() + " cancelled by the student as a false alarm");
        return Map.of("message", "Your emergency alert has been cancelled. The clinic has been told it was a false alarm.");
    }

    // Drives the "emergency today - your appointment may be delayed" banner.
    // Only shown when the student has a normal (non-emergency) booking for
    // TODAY and a nurse/doctor has announced a delay today; bookings on other
    // days, and the emergency patient themselves, are unaffected.
    @GetMapping("/today-delay")
    public Map<String, Object> getTodayDelay(Authentication auth) {
        // The emergency patient themselves is never told they'll be delayed.
        if (hasSentEmergencyAlertToday(auth.getName())) {
            return Map.of("delayed", false);
        }
        Appointment todaysVisit = delayNoticeService.findNormalVisitToday(auth.getName());
        if (todaysVisit == null) {
            return Map.of("delayed", false);
        }

        Notification announcement = delayNoticeService.findDelayAnnouncementToday();
        if (announcement == null || auth.getName().equals(announcement.getStudentUsername())) {
            return Map.of("delayed", false);
        }

        return Map.of(
                "delayed", true,
                "timeSlot", todaysVisit.getTimeSlot() != null ? todaysVisit.getTimeSlot() : "",
                "message", DelayNoticeService.DELAY_MESSAGE
        );
    }

    // Contact, residence and emergency contact details collected at registration
    // so clinical staff can reach the student (or someone on their behalf).
    private String validateContactDetails(String phoneNumber, String email, String semesterAddress, String homeAddress,
                                          String emergencyContactName, String emergencyContactRelationship,
                                          String emergencyContactPhone) {
        if (!isValidPhone(phoneNumber)) {
            return "Please enter a valid phone number (" + MIN_PHONE_DIGITS + "-" + MAX_PHONE_DIGITS + " digits).";
        }
        if (email == null || email.trim().length() > MAX_EMAIL_LENGTH || !EMAIL_PATTERN.matcher(email.trim()).matches()) {
            return "Please enter a valid email address.";
        }
        if (semesterAddress == null || semesterAddress.isBlank()) {
            return "Please enter where you live during the semester.";
        }
        if (homeAddress == null || homeAddress.isBlank()) {
            return "Please enter where you live outside the semester.";
        }
        if (semesterAddress.trim().length() > MAX_ADDRESS_LENGTH || homeAddress.trim().length() > MAX_ADDRESS_LENGTH) {
            return "Addresses must be " + MAX_ADDRESS_LENGTH + " characters or fewer.";
        }
        if (wordCount(semesterAddress) > MAX_ADDRESS_WORDS) {
            return "Where you live during the semester must be " + MAX_ADDRESS_WORDS + " words or fewer.";
        }
        if (wordCount(homeAddress) > MAX_ADDRESS_WORDS) {
            return "Where you live outside the semester must be " + MAX_ADDRESS_WORDS + " words or fewer.";
        }
        if (emergencyContactName == null || emergencyContactName.isBlank()) {
            return "Please enter an emergency contact name.";
        }
        if (emergencyContactName.trim().length() > MAX_FULL_NAME_LENGTH) {
            return "Emergency contact name must be " + MAX_FULL_NAME_LENGTH + " characters or fewer.";
        }
        if (emergencyContactRelationship == null || emergencyContactRelationship.isBlank()) {
            return "Please enter your relationship to your emergency contact.";
        }
        if (emergencyContactRelationship.trim().length() > MAX_RELATIONSHIP_LENGTH) {
            return "Relationship must be " + MAX_RELATIONSHIP_LENGTH + " characters or fewer.";
        }
        if (!isValidPhone(emergencyContactPhone)) {
            return "Please enter a valid phone number for your emergency contact (" + MIN_PHONE_DIGITS + "-" + MAX_PHONE_DIGITS + " digits).";
        }
        if (phoneDigits(emergencyContactPhone).equals(phoneDigits(phoneNumber))) {
            return "Your emergency contact's phone number must be different from your own.";
        }
        return null;
    }

    private int wordCount(String text) {
        String trimmed = text.trim();
        return trimmed.isEmpty() ? 0 : trimmed.split("\\s+").length;
    }

    private boolean isValidPhone(String phone) {
        if (phone == null || !PHONE_PATTERN.matcher(phone.trim()).matches()) {
            return false;
        }
        int digits = phoneDigits(phone).length();
        return digits >= MIN_PHONE_DIGITS && digits <= MAX_PHONE_DIGITS;
    }

    private String phoneDigits(String phone) {
        return phone.replaceAll("\\D", "");
    }

    private static final byte[] PDF_SIGNATURE = "%PDF-".getBytes(java.nio.charset.StandardCharsets.US_ASCII);

    private String validateReceipt(MultipartFile receipt) {
        // Content-Type is a header the browser sends and can be spoofed (e.g. a
        // renamed malicious file claiming to be "application/pdf") - the magic-byte
        // check below is what actually confirms the file is a real PDF.
        if (!"application/pdf".equals(receipt.getContentType())) {
            return "The payment receipt must be a PDF file.";
        }
        if (receipt.getSize() > MAX_RECEIPT_SIZE_BYTES) {
            return "The payment receipt must be smaller than 5MB.";
        }
        if (!hasPdfSignature(receipt)) {
            return "This file does not appear to be a valid PDF. Please upload an unmodified PDF receipt.";
        }
        return null;
    }

    private boolean hasPdfSignature(MultipartFile receipt) {
        try {
            byte[] header = new byte[PDF_SIGNATURE.length];
            try (var in = receipt.getInputStream()) {
                int read = in.readNBytes(header, 0, header.length);
                if (read < header.length) return false;
            }
            return java.util.Arrays.equals(header, PDF_SIGNATURE);
        } catch (IOException e) {
            return false;
        }
    }
}