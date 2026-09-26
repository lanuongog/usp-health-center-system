package com.example.demo;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class LoginCheckController {

    private static final int MAX_FIELD_LENGTH = 200;

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final LoginAttemptService loginAttemptService;

    public LoginCheckController(UserRepository userRepository, PasswordEncoder passwordEncoder,
                                 LoginAttemptService loginAttemptService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.loginAttemptService = loginAttemptService;
    }

    public static class LoginCheckRequest {
        public String username;
        public String password;
        public String requiredRole; // ADMIN, NURSE, DOCTOR, STUDENT, or null/empty for universal login
    }

    // Public endpoint used to authenticate and verify user role, supporting both
    // role-specific pages and the universal login.html page.
    @PostMapping("/check-login")
    public Map<String, Object> checkLogin(@RequestBody LoginCheckRequest request) {
        if (request.username == null || request.username.isBlank() || request.password == null || request.password.isBlank()) {
            return Map.of("ok", false, "message", "Username and password are required.");
        }
        if (request.username.length() > MAX_FIELD_LENGTH || request.password.length() > MAX_FIELD_LENGTH) {
            return Map.of("ok", false, "message", "Invalid username or password.");
        }

        if (loginAttemptService.isLocked(request.username)) {
            long minutes = loginAttemptService.minutesUntilUnlock(request.username);
            return Map.of("ok", false, "message",
                    "Too many failed login attempts. Please try again in " + minutes + " minute(s).");
        }

        var userOpt = userRepository.findByUsername(request.username);

        if (userOpt.isEmpty() || !passwordEncoder.matches(request.password, userOpt.get().getPassword())) {
            loginAttemptService.recordFailure(request.username);
            return Map.of("ok", false, "message", "Invalid username or password.");
        }

        User user = userOpt.get();
        loginAttemptService.recordSuccess(request.username);

        if (!user.isActive()) {
            return Map.of("ok", false, "message", "This account has been deactivated. Contact an administrator.");
        }

        // If a specific role or list of roles is requested, validate it
        if (request.requiredRole != null && !request.requiredRole.isBlank() && !"ANY".equalsIgnoreCase(request.requiredRole.trim())) {
            boolean roleMatches = java.util.Arrays.stream(request.requiredRole.split(","))
                    .map((String s) -> s.trim())
                    .anyMatch(r -> r.equalsIgnoreCase(user.getRole()));

            if (!roleMatches) {
                String message = switch (request.requiredRole.toUpperCase()) {
                    case "NURSE" -> "You do not have permission to perform check-in";
                    case "DOCTOR" -> "You do not have permission to view this page";
                    case "ADMIN" -> "You do not have permission to view this page";
                    case "STUDENT" -> "This portal is reserved for students";
                    default -> "You do not have permission to view this page";
                };
                return Map.of("ok", false, "message", message);
            }
        }

        return Map.of(
            "ok", true,
            "role", user.getRole(),
            "username", user.getUsername(),
            "message", ""
        );
    }
}
