package com.example.demo;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// Self-service "Forgot password" for STUDENT accounts, usable without being logged in.
//   1. POST /auth/forgot-password  { studentId }                      -> emails a 6-digit code
//   2. POST /auth/reset-password   { studentId, code, newPassword }   -> sets the new password
// Staff accounts are intentionally excluded - their passwords are still reset by an admin.
@RestController
@RequestMapping("/auth")
public class PasswordResetController {

    private static final Logger log = LoggerFactory.getLogger(PasswordResetController.class);

    private static final Pattern STUDENT_ID_PATTERN = Pattern.compile("^S\\d{8}$");
    private static final Pattern CODE_PATTERN = Pattern.compile("^\\d{6}$");
    private static final int MIN_PASSWORD_LENGTH = 6;
    private static final int MAX_PASSWORD_LENGTH = 100;
    private static final int CODE_VALID_MINUTES = 15;
    private static final int MAX_REQUESTS_PER_WINDOW = 3;   // per student, per 15 minutes
    private static final int MAX_FAILED_ATTEMPTS = 5;       // wrong guesses before the code is burned

    // Same wording whether or not the Student ID exists, so the form can't be used
    // to find out which IDs are registered.
    private static final String SENT_MESSAGE =
            "If that Student ID is registered, a 6-digit code has been sent to the student's USP email address. "
            + "It expires in " + CODE_VALID_MINUTES + " minutes.";
    private static final String BAD_CODE_MESSAGE = "Invalid or expired code. Please request a new one.";

    private final UserRepository userRepository;
    private final PasswordResetTokenRepository tokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final LoginAttemptService loginAttemptService;
    private final ObjectProvider<JavaMailSender> mailSenderProvider;
    private final SecureRandom random = new SecureRandom();

    @Value("${app.mail.from:no-reply@usp-health-center.local}")
    private String mailFrom;

    @Value("${app.mail.student-domain:student.usp.ac.fj}")
    private String studentEmailDomain;

    // DEVELOPMENT ONLY: when true, the code is also written to the server log so the
    // flow can be tested without a mail server. Leave false in any shared deployment.
    @Value("${app.password-reset.log-code:false}")
    private boolean logCode;

    public PasswordResetController(UserRepository userRepository,
                                   PasswordResetTokenRepository tokenRepository,
                                   PasswordEncoder passwordEncoder,
                                   LoginAttemptService loginAttemptService,
                                   ObjectProvider<JavaMailSender> mailSenderProvider) {
        this.userRepository = userRepository;
        this.tokenRepository = tokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.loginAttemptService = loginAttemptService;
        this.mailSenderProvider = mailSenderProvider;
    }

    public static class ForgotRequest {
        public String studentId;
    }

    public static class ResetRequest {
        public String studentId;
        public String code;
        public String newPassword;
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<Map<String, String>> forgotPassword(@RequestBody ForgotRequest request) {
        String studentId = normalizeStudentId(request == null ? null : request.studentId);
        if (studentId == null) {
            return ResponseEntity.badRequest().body(Map.of("error",
                    "Student ID must be in the format S followed by 8 digits (e.g. S12345678)."));
        }

        User student = userRepository.findByUsername(studentId)
                .filter(u -> "STUDENT".equals(u.getRole()) && u.isActive())
                .orElse(null);

        if (student != null) {
            LocalDateTime now = LocalDateTime.now();
            long recent = tokenRepository.countByStudentUsernameAndCreatedAtAfter(
                    studentId, now.minusMinutes(CODE_VALID_MINUTES));
            if (recent < MAX_REQUESTS_PER_WINDOW) {
                // a new request replaces any earlier unused code
                tokenRepository.findByStudentUsernameAndUsedFalse(studentId).forEach(t -> {
                    t.setUsed(true);
                    tokenRepository.save(t);
                });

                String code = String.format("%06d", random.nextInt(1_000_000));
                tokenRepository.save(new PasswordResetToken(
                        studentId, passwordEncoder.encode(code), now, now.plusMinutes(CODE_VALID_MINUTES)));
                sendCode(studentId, student.getFullName(), code);
            }
            // over the limit: silently do nothing - the response below is identical
        }

        return ResponseEntity.ok(Map.of("message", SENT_MESSAGE));
    }

    @PostMapping("/reset-password")
    public ResponseEntity<Map<String, String>> resetPassword(@RequestBody ResetRequest request) {
        String studentId = normalizeStudentId(request == null ? null : request.studentId);
        if (studentId == null) {
            return ResponseEntity.badRequest().body(Map.of("error",
                    "Student ID must be in the format S followed by 8 digits (e.g. S12345678)."));
        }
        if (request.code == null || !CODE_PATTERN.matcher(request.code.trim()).matches()) {
            return ResponseEntity.badRequest().body(Map.of("error", "The code must be exactly 6 digits."));
        }
        if (request.newPassword == null || request.newPassword.length() < MIN_PASSWORD_LENGTH
                || request.newPassword.length() > MAX_PASSWORD_LENGTH) {
            return ResponseEntity.badRequest().body(Map.of("error",
                    "Password must be between " + MIN_PASSWORD_LENGTH + " and " + MAX_PASSWORD_LENGTH + " characters."));
        }

        PasswordResetToken token = tokenRepository
                .findFirstByStudentUsernameAndUsedFalseOrderByCreatedAtDesc(studentId).orElse(null);
        User student = userRepository.findByUsername(studentId)
                .filter(u -> "STUDENT".equals(u.getRole()) && u.isActive()).orElse(null);

        if (token == null || student == null || LocalDateTime.now().isAfter(token.getExpiresAt())) {
            return ResponseEntity.badRequest().body(Map.of("error", BAD_CODE_MESSAGE));
        }

        if (!passwordEncoder.matches(request.code.trim(), token.getCodeHash())) {
            token.setFailedAttempts(token.getFailedAttempts() + 1);
            if (token.getFailedAttempts() >= MAX_FAILED_ATTEMPTS) {
                token.setUsed(true); // too many wrong guesses - the code is burned
            }
            tokenRepository.save(token);
            return ResponseEntity.badRequest().body(Map.of("error", BAD_CODE_MESSAGE));
        }

        student.setPassword(passwordEncoder.encode(request.newPassword));
        userRepository.save(student);
        token.setUsed(true);
        tokenRepository.save(token);
        loginAttemptService.recordSuccess(studentId); // also clears any failed-login lockout

        return ResponseEntity.ok(Map.of("message", "Password reset successfully. You can now sign in with your new password."));
    }

    private String normalizeStudentId(String raw) {
        if (raw == null) return null;
        String id = raw.trim().toUpperCase();
        return STUDENT_ID_PATTERN.matcher(id).matches() ? id : null;
    }

    private void sendCode(String studentId, String fullName, String code) {
        String to = studentId.toLowerCase() + "@" + studentEmailDomain;
        JavaMailSender sender = mailSenderProvider.getIfAvailable();
        boolean sent = false;
        if (sender != null) {
            try {
                SimpleMailMessage message = buildMessage(mailFrom, to, fullName, code, CODE_VALID_MINUTES);
                sender.send(message);
                sent = true;
            } catch (Exception e) {
                log.warn("Could not send password reset email to {}: {}", to, e.getMessage());
            }
        } else {
            log.warn("No mail server configured (spring.mail.host is not set) - password reset email not sent.");
        }
        if (logCode) {
            log.info("[DEV ONLY] Password reset code for {}: {} (email sent: {})", studentId, code, sent);
        }
    }

    // the email text lives in one place
    private SimpleMailMessage buildMessage(String from, String to, String fullName, String code, int minutes) {
        SimpleMailMessage m = new SimpleMailMessage();
        m.setFrom(from);
        m.setTo(to);
        m.setSubject("USP Health Center - password reset code");
        m.setText("Hello " + (fullName == null || fullName.isBlank() ? "" : fullName) + ",\n\n"
                + "Your password reset code is: " + code + "\n\n"
                + "It expires in " + minutes + " minutes and can be used once. "
                + "If you did not ask to reset your password, you can ignore this email - your password has not changed.\n\n"
                + "USP Health Center");
        return m;
    }
}
