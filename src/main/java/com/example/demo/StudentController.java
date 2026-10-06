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
    private static final double LOCAL_FEE = 50.00;
    private static final double REGIONAL_FEE = 80.00;
    private static final List<String> MARITAL_STATUSES = List.of("Single", "Married");
    private static final long MAX_RECEIPT_SIZE_BYTES = 5L * 1024 * 1024;
    // Official USU student ID format: "S" followed by exactly 8 digits.
    private static final Pattern STUDENT_ID_PATTERN = Pattern.compile("^S\\d{8}$");
    private static final int MIN_PASSWORD_LENGTH = 6;
    private static final int MAX_PASSWORD_LENGTH = 100;
    private static final int MAX_FULL_NAME_LENGTH = 100;
    private static final int MAX_EMERGENCY_REASON_LENGTH = 300;

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final NotificationRepository notificationRepository;
    private final ReceiptVerificationService receiptVerificationService;
    private final DelayNoticeService delayNoticeService;

    public StudentController(UserRepository userRepository, PasswordEncoder passwordEncoder,
                              NotificationRepository notificationRepository,
                              ReceiptVerificationService receiptVerificationService,
                              DelayNoticeService delayNoticeService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.notificationRepository = notificationRepository;
        this.receiptVerificationService = receiptVerificationService;
        this.delayNoticeService = delayNoticeService;
    }

    // student self-registration logic
    @PostMapping("/register")
    public Object register(@RequestParam String studentId,
                            @RequestParam String password,
                            @RequestParam String fullName,
                            @RequestParam String maritalStatus,
                            @RequestParam String country,
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
        if (maritalStatus == null || !MARITAL_STATUSES.contains(maritalStatus)) {
            return Map.of("error", "Please select a valid marital status.");
        }
        if (country == null || !MEMBER_COUNTRIES.contains(country)) {
            return Map.of("error", "Please select a valid country.");
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

        double feeAmount = LOCAL_COUNTRY.equals(country) ? LOCAL_FEE : REGIONAL_FEE;

        // encrypts student password
        User student = new User(studentId, passwordEncoder.encode(password), "STUDENT");
        student.setFullName(fullName.trim());
        student.setMaritalStatus(maritalStatus);
        student.setCountry(country);
        student.setFeeAmount(feeAmount);
        student.setFeePaid(false);
        byte[] receiptBytes;
        try {
            receiptBytes = receipt.getBytes();
            student.setReceiptFile(receiptBytes);
            student.setReceiptFileName(receipt.getOriginalFilename());
        } catch (IOException e) {
            return Map.of("error", "Unable to read the uploaded receipt. Please try again.");
        }

        // Best-effort automatic check: if the receipt's own text contains the
        // expected fee amount and the student's name, skip the admin queue
        // entirely. Anything less certain falls back to manual review exactly
        // as before - this never blocks registration, only speeds up the
        // clear-cut cases.
        ReceiptVerificationResult check = receiptVerificationService.verify(receiptBytes, feeAmount, fullName.trim());
        student.setReceiptAutoCheckNote(check.summary());
        student.setFeePaid(check.isAutoApproved());

        userRepository.save(student);

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
        return Map.of(
                "countries", MEMBER_COUNTRIES,
                "localCountry", LOCAL_COUNTRY,
                "localFee", LOCAL_FEE,
                "regionalFee", REGIONAL_FEE
        );
    }

    // Lets a logged-in student see their own registration/fee details.
    @GetMapping("/profile")
    public Object getProfile(Authentication auth) {
        return userRepository.findByUsername(auth.getName())
                .<Object>map(u -> Map.of(
                        "fullName", u.getFullName() != null ? u.getFullName() : "",
                        "country", u.getCountry() != null ? u.getCountry() : "",
                        "maritalStatus", u.getMaritalStatus() != null ? u.getMaritalStatus() : "",
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
        } catch (IOException e) {
            return Map.of("error", "Unable to read the uploaded receipt. Please try again.");
        }
        student.setReceiptRejectionReason(null);

        double feeAmount = student.getFeeAmount() != null ? student.getFeeAmount() : 0.0;
        ReceiptVerificationResult check = receiptVerificationService.verify(receiptBytes, feeAmount, student.getFullName());
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
        public String reason;
    }

    // Guards the "one emergency alert per day" check-then-save so a double
    // click (two requests arriving together) can't slip two alerts through.
    private final Object emergencyAlertLock = new Object();

    private boolean hasSentEmergencyAlertToday(String username) {
        return notificationRepository.existsByTypeAndStudentUsernameAndCreatedAtGreaterThanEqual(
                "EMERGENCY_ALERT", username, LocalDate.now().atStartOfDay());
    }

    // Lets the portal show today's request as already sent (and when) after a reload.
    @GetMapping("/emergency-alert/status")
    public Map<String, Object> getEmergencyAlertStatus(Authentication auth) {
        Notification sent = notificationRepository.findFirstByTypeAndStudentUsernameAndCreatedAtGreaterThanEqualOrderByCreatedAtDesc(
                "EMERGENCY_ALERT", auth.getName(), LocalDate.now().atStartOfDay());
        if (sent == null) {
            return Map.of("sentToday", false);
        }
        return Map.of("sentToday", true, "sentAt", sent.getCreatedAt().toString());
    }

    // A quick, low-friction heads-up a student sends before they've arrived -
    // it only notifies staff so they can prepare; it does NOT create an
    // appointment or check the student in, since they aren't physically here
    // yet. The actual check-in still happens through the nurse/doctor
    // dashboard's Emergency Check-in action once the student arrives.
    // Limited to one alert per student per day.
    @PostMapping("/emergency-alert")
    public Object sendEmergencyAlert(@RequestBody EmergencyAlertRequest request, Authentication auth) {
        if (request.reason == null || request.reason.isBlank()) {
            return Map.of("error", "Please briefly describe what's happening.");
        }
        if (request.reason.trim().length() > MAX_EMERGENCY_REASON_LENGTH) {
            return Map.of("error", "Description must be " + MAX_EMERGENCY_REASON_LENGTH + " characters or fewer.");
        }

        User student = userRepository.findByUsername(auth.getName()).orElse(null);
        String displayName = (student != null && student.getFullName() != null && !student.getFullName().isBlank())
                ? student.getFullName() : auth.getName();

        synchronized (emergencyAlertLock) {
            if (hasSentEmergencyAlertToday(auth.getName())) {
                return Map.of("error", "You have already sent an emergency alert today - Health Center staff have been "
                        + "notified and are expecting you. If this is life-threatening, contact campus security or "
                        + "emergency services immediately.");
            }

            Notification alert = new Notification();
            alert.setType("EMERGENCY_ALERT");
            alert.setStudentUsername(auth.getName());
            alert.setStudentName(displayName);
            alert.setMessage("EMERGENCY ALERT from " + displayName + " (" + auth.getName() + "): " + request.reason.trim()
                    + " — not yet checked in, will need immediate attention on arrival.");
            alert.setCreatedAt(LocalDateTime.now());
            notificationRepository.save(alert);
        }

        // Students with a visit today are warned their appointment may be delayed.
        delayNoticeService.notifyStudentsWithVisitToday(auth.getName(), displayName, null);

        return Map.of("message", "Alert sent. Health Center staff have been notified and will prioritize you on "
                + "arrival. If this is life-threatening, also contact campus security or emergency services immediately.");
    }

    // Drives the "emergency today - your appointment may be delayed" banner.
    // Only shown when the student has a normal (non-emergency) booking for
    // TODAY and some other student has raised an emergency today; bookings on
    // other days, and the emergency patient themselves, are unaffected.
    @GetMapping("/today-delay")
    public Map<String, Object> getTodayDelay(Authentication auth) {
        LocalDate today = LocalDate.now();
        // The emergency patient themselves is never told they'll be delayed.
        if (hasSentEmergencyAlertToday(auth.getName())) {
            return Map.of("delayed", false);
        }
        Appointment todaysVisit = delayNoticeService.findNormalVisitToday(auth.getName());
        if (todaysVisit == null) {
            return Map.of("delayed", false);
        }

        boolean emergencyToday = notificationRepository
                .findByRecipientUsernameIsNullAndTypeInAndCreatedAtGreaterThanEqual(
                        List.of("EMERGENCY_ALERT", "EMERGENCY_BOOKING"), today.atStartOfDay())
                .stream()
                .anyMatch(n -> !auth.getName().equals(n.getStudentUsername()));
        if (!emergencyToday) {
            return Map.of("delayed", false);
        }

        return Map.of(
                "delayed", true,
                "timeSlot", todaysVisit.getTimeSlot() != null ? todaysVisit.getTimeSlot() : "",
                "message", DelayNoticeService.DELAY_MESSAGE
        );
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