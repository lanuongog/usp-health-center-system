package com.example.demo;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class LoginCheckController {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    public static class LoginCheckRequest {
        public String username;
        public String password;
        public String requiredRole; // ADMIN, NURSE, or DOCTOR
    }

    // Public endpoint used only to determine WHY a login attempt failed, so the
    // frontend can display the exact wording required by the SRS error tables
    // (e.g. FR-03-01.4, FR-04-01.4, FR-05-03.4) rather than a generic message.
    @PostMapping("/check-login")
    public Map<String, Object> checkLogin(@RequestBody LoginCheckRequest request) {
        var userOpt = userRepository.findByUsername(request.username);

        if (userOpt.isEmpty() || !passwordEncoder.matches(request.password, userOpt.get().getPassword())) {
            return Map.of("ok", false, "message", "Invalid username or password.");
        }

        User user = userOpt.get();

        if (!user.isActive()) {
            return Map.of("ok", false, "message", "This account has been deactivated. Contact an administrator.");
        }

        // requiredRole may be a single role ("NURSE") or comma-separated ("DOCTOR,ADMIN")
        boolean roleMatches = java.util.Arrays.stream(request.requiredRole.split(","))
                .map(String::trim)
                .anyMatch(r -> r.equals(user.getRole()));

        if (!roleMatches) {
            String message = switch (request.requiredRole) {
                case "NURSE" -> "You do not have permission to perform check-in";
                case "DOCTOR" -> "You do not have permission to view this page";
                case "ADMIN" -> "You do not have permission to view this page";
                default -> "You do not have permission to view this page";
            };
            return Map.of("ok", false, "message", message);
        }

        return Map.of("ok", true, "message", "");
    }
}
