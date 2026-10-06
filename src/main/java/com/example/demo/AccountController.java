package com.example.demo;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

// Self-service password change, available to every authenticated role -
// distinct from the admin-triggered reset in AdminUserController, which sets
// a password without knowing the old one. This is how a student (or any
// user) turns an admin-given temporary password into one only they know.
@RestController
@RequestMapping("/account")
public class AccountController {

    private static final int MIN_PASSWORD_LENGTH = 6;
    private static final int MAX_PASSWORD_LENGTH = 100;

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public AccountController(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public static class ChangePasswordRequest {
        public String currentPassword;
        public String newPassword;
    }

    @PostMapping("/change-password")
    public Object changePassword(@RequestBody ChangePasswordRequest request, Authentication auth) {
        if (request.currentPassword == null || request.currentPassword.isBlank()
                || request.newPassword == null || request.newPassword.isBlank()) {
            return Map.of("error", "Current and new password are required.");
        }
        String passwordError = PasswordRules.check(request.newPassword, auth.getName());
        if (passwordError != null) {
            return Map.of("error", passwordError);
        }
        if (request.newPassword.equals(request.currentPassword)) {
            return Map.of("error", "New password must be different from the current password.");
        }

        User user = userRepository.findByUsername(auth.getName())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Account not found"));

        if (!passwordEncoder.matches(request.currentPassword, user.getPassword())) {
            return Map.of("error", "Current password is incorrect.");
        }

        user.setPassword(passwordEncoder.encode(request.newPassword));
        userRepository.save(user);

        return Map.of("message", "Password changed successfully.");
    }
}
