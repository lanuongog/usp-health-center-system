package com.example.demo;

import java.time.LocalDateTime;

import jakarta.persistence.*;

@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String username;

    @Column(nullable = false)
    private String password;

    @Column(nullable = false)
    private String role; // ADMIN, DOCTOR, NURSE, STUDENT

    @Column(nullable = false)
    private boolean active = true;

    // Student registration details (null/unset for non-student accounts)
    private String fullName;
    private String country; // one of the 12 USP member countries
    private String phoneNumber;
    private String semesterAddress; // where the student lives during the semester
    private String homeAddress; // where the student lives outside the semester (breaks/holidays)
    private String emergencyContactName;
    private String emergencyContactRelationship;
    private String emergencyContactPhone;
    @Column(nullable = false)
    private boolean feePaid = false;
    private Double feeAmount; // Health Services Fee owed, in FJD, set at registration time

    @Column(columnDefinition = "bytea")
    private byte[] receiptFile; // uploaded PDF proof of payment
    private String receiptFileName;
    private String receiptRejectionReason; // set by an admin when rejecting a receipt
    private String receiptAutoCheckNote; // system's automated amount/name check on the current receipt
    private LocalDateTime receiptUploadedAt; // when the current receipt was uploaded, for the admin's pending queue
    private LocalDateTime registeredAt; // when the student self-registered (null for staff and older accounts)

    public User() {}

    public User(String username, String password, String role) {
        this.username = username;
        this.password = password;
        this.role = role;
        this.active = true;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }

    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }

    public String getCountry() { return country; }
    public void setCountry(String country) { this.country = country; }

    public String getPhoneNumber() { return phoneNumber; }
    public void setPhoneNumber(String phoneNumber) { this.phoneNumber = phoneNumber; }

    public String getSemesterAddress() { return semesterAddress; }
    public void setSemesterAddress(String semesterAddress) { this.semesterAddress = semesterAddress; }

    public String getHomeAddress() { return homeAddress; }
    public void setHomeAddress(String homeAddress) { this.homeAddress = homeAddress; }

    public String getEmergencyContactName() { return emergencyContactName; }
    public void setEmergencyContactName(String emergencyContactName) { this.emergencyContactName = emergencyContactName; }

    public String getEmergencyContactRelationship() { return emergencyContactRelationship; }
    public void setEmergencyContactRelationship(String emergencyContactRelationship) { this.emergencyContactRelationship = emergencyContactRelationship; }

    public String getEmergencyContactPhone() { return emergencyContactPhone; }
    public void setEmergencyContactPhone(String emergencyContactPhone) { this.emergencyContactPhone = emergencyContactPhone; }

    public boolean isFeePaid() { return feePaid; }
    public void setFeePaid(boolean feePaid) { this.feePaid = feePaid; }

    public Double getFeeAmount() { return feeAmount; }
    public void setFeeAmount(Double feeAmount) { this.feeAmount = feeAmount; }

    public byte[] getReceiptFile() { return receiptFile; }
    public void setReceiptFile(byte[] receiptFile) { this.receiptFile = receiptFile; }

    public String getReceiptFileName() { return receiptFileName; }
    public void setReceiptFileName(String receiptFileName) { this.receiptFileName = receiptFileName; }

    public String getReceiptRejectionReason() { return receiptRejectionReason; }
    public void setReceiptRejectionReason(String receiptRejectionReason) { this.receiptRejectionReason = receiptRejectionReason; }

    public String getReceiptAutoCheckNote() { return receiptAutoCheckNote; }
    public void setReceiptAutoCheckNote(String receiptAutoCheckNote) { this.receiptAutoCheckNote = receiptAutoCheckNote; }

    public LocalDateTime getReceiptUploadedAt() { return receiptUploadedAt; }
    public void setReceiptUploadedAt(LocalDateTime receiptUploadedAt) { this.receiptUploadedAt = receiptUploadedAt; }

    public LocalDateTime getRegisteredAt() { return registeredAt; }
    public void setRegisteredAt(LocalDateTime registeredAt) { this.registeredAt = registeredAt; }
}
