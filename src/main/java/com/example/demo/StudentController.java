package com.example.demo;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
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

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public StudentController(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
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
        if (fullName == null || fullName.isBlank()) {
            return Map.of("error", "Full name is required.");
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
        try {
            student.setReceiptFile(receipt.getBytes());
            student.setReceiptFileName(receipt.getOriginalFilename());
        } catch (IOException e) {
            return Map.of("error", "Unable to read the uploaded receipt. Please try again.");
        }
        userRepository.save(student);

        return Map.of(
                "message", "Registration successful. You can now log in. Your payment receipt is pending admin verification before you can book appointments.",
                "studentId", studentId,
                "feeAmount", feeAmount,
                "feePaid", false
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

        try {
            student.setReceiptFile(receipt.getBytes());
            student.setReceiptFileName(receipt.getOriginalFilename());
        } catch (IOException e) {
            return Map.of("error", "Unable to read the uploaded receipt. Please try again.");
        }
        student.setFeePaid(false);
        student.setReceiptRejectionReason(null);
        userRepository.save(student);

        return Map.of("message", "Receipt uploaded. It is now pending admin verification.");
    }

    private String validateReceipt(MultipartFile receipt) {
        if (!"application/pdf".equals(receipt.getContentType())) {
            return "The payment receipt must be a PDF file.";
        }
        if (receipt.getSize() > MAX_RECEIPT_SIZE_BYTES) {
            return "The payment receipt must be smaller than 5MB.";
        }
        return null;
    }
}