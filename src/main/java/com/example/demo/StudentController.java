package com.example.demo;

import java.util.List;
import java.util.Map;

import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/student")
public class StudentController {

    // The 12 USP member countries. Fiji is the "local" fee tier; every other
    // member country is billed at the "regional" rate.
    private static final String LOCAL_COUNTRY = "Fiji";
    private static final List<String> USP_MEMBER_COUNTRIES = List.of(
            "Fiji", "Samoa", "Vanuatu", "Solomon Islands", "Cook Islands",
            "Tonga", "Tuvalu", "Niue", "Kiribati", "Marshall Islands", "Nauru", "Tokelau"
    );
    private static final double LOCAL_FEE = 50.00;
    private static final double REGIONAL_FEE = 80.00;
    private static final List<String> MARITAL_STATUSES = List.of("Single", "Married");

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public StudentController(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public static class RegisterRequest {
        public String studentId;
        public String password;
        public String fullName;
        public String maritalStatus;
        public String country;
        public boolean feePaid;
    }

    // FR-01-01 to 04: student self-registration logic
    @PostMapping("/register")
    public Object register(@RequestBody RegisterRequest request) {
        if (request.studentId == null || request.studentId.isBlank() ||
                request.password == null || request.password.isBlank()) {
            return Map.of("error", "Student ID and password are required.");
        }
        if (request.fullName == null || request.fullName.isBlank()) {
            return Map.of("error", "Full name is required.");
        }
        if (request.maritalStatus == null || !MARITAL_STATUSES.contains(request.maritalStatus)) {
            return Map.of("error", "Please select a valid marital status.");
        }
        if (request.country == null || !USP_MEMBER_COUNTRIES.contains(request.country)) {
            return Map.of("error", "Please select a valid country.");
        }
        if (!request.feePaid) {
            return Map.of("error", "You must confirm that you have paid your USP Health Services Fee before registering.");
        }

        if (userRepository.findByUsername(request.studentId).isPresent()) {
            return Map.of("error", "This student ID is already registered. Please log in.");
        }

        double feeAmount = LOCAL_COUNTRY.equals(request.country) ? LOCAL_FEE : REGIONAL_FEE;

        // encrypts student password
        User student = new User(request.studentId, passwordEncoder.encode(request.password), "STUDENT");
        student.setFullName(request.fullName.trim());
        student.setMaritalStatus(request.maritalStatus);
        student.setCountry(request.country);
        student.setFeeAmount(feeAmount);
        student.setFeePaid(request.feePaid);
        userRepository.save(student);

        return Map.of(
                "message", "Registration successful. You can now log in.",
                "studentId", request.studentId,
                "feeAmount", feeAmount,
                "feePaid", request.feePaid
        );
    }

    // Lets the registration form show the fee for a country before submitting.
    @GetMapping("/fee-schedule")
    public Map<String, Object> getFeeSchedule() {
        return Map.of(
                "countries", USP_MEMBER_COUNTRIES,
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
                        "feeAmount", u.getFeeAmount() != null ? u.getFeeAmount() : 0.0
                ))
                .orElse(Map.of("error", "Account not found"));
    }
}