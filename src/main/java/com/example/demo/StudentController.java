package com.example.demo;

import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/student")
public class StudentController {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    public static class RegisterRequest {
        public String studentId;
        public String password;
        public String fullName;
    }

    // FR-01-01 to 04: student self-registration logic
    @PostMapping("/register")
    public Object register(@RequestBody RegisterRequest request) {
        if (request.studentId == null || request.studentId.isBlank() ||
                request.password == null || request.password.isBlank()) {
            return Map.of("error", "Student ID and password are required.");
        }

        if (userRepository.findByUsername(request.studentId).isPresent()) {
            return Map.of("error", "This student ID is already registered. Please log in.");
        }
        // encrypts student password
        User student = new User(request.studentId, passwordEncoder.encode(request.password), "STUDENT");
        userRepository.save(student);
        return Map.of("message", "Registration successful. You can now log in.", "studentId", request.studentId);
    }
}