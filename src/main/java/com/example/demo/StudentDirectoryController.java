package com.example.demo;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// Lets a nurse or doctor look up any registered student's record details, even
// one who has never booked an appointment - the source of truth for "who is
// this student" is the school's own registered-student accounts, not just
// whoever happens to have an appointment on file.
@RestController
@RequestMapping("/directory")
public class StudentDirectoryController {

    private final UserRepository userRepository;

    public StudentDirectoryController(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    // A safe, read-only view of a student account for staff lookups - never
    // exposes the password hash or the raw receipt file.
    public static class StudentDirectoryEntry {
        public Long id;
        public String studentId;
        public String fullName;
        public String country;
        public boolean feePaid;
        public Double feeAmount;
        public boolean hasReceipt;
        public boolean active;

        public StudentDirectoryEntry(User u) {
            this.id = u.getId();
            this.studentId = u.getUsername();
            this.fullName = u.getFullName();
            this.country = u.getCountry();
            this.feePaid = u.isFeePaid();
            this.feeAmount = u.getFeeAmount();
            this.hasReceipt = u.getReceiptFile() != null;
            this.active = u.isActive();
        }
    }

    @GetMapping("/students")
    public List<StudentDirectoryEntry> getAllStudents() {
        return userRepository.findAll().stream()
                .filter(u -> "STUDENT".equals(u.getRole()))
                .map(StudentDirectoryEntry::new)
                .collect(Collectors.toList());
    }

    // A quick payment/receipt lookup so a nurse or doctor can confirm a
    // student's Health Services Fee status before booking or checking them in
    // - read-only; approving/rejecting a receipt stays an admin-only action.
    // Tries the ID as typed first, then upper-cased: new registrations are
    // normalized to SXXXXXXXX, but legacy accounts predate that format and
    // are stored with their original casing.
    @GetMapping("/students/{studentId}")
    public Object getStudent(@PathVariable String studentId) {
        String trimmed = studentId.trim();
        return userRepository.findByUsername(trimmed)
                .or(() -> userRepository.findByUsername(trimmed.toUpperCase()))
                .filter(u -> "STUDENT".equals(u.getRole()))
                .<Object>map(StudentDirectoryEntry::new)
                .orElse(Map.of("error", "No registered student found with that Student ID."));
    }
}
