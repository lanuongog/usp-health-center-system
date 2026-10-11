package com.example.demo;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

// Two kinds of alert live in this one table:
//  - Staff-facing (recipientUsername == null): a student sends an emergency
//    alert (max one per day), or a nurse/doctor completes an emergency
//    check-in. Shown to every nurse/doctor/admin.
//  - Student-facing (recipientUsername == that student's username): a "your
//    appointment may be delayed" notice sent to students who have a visit
//    TODAY when an emergency is raised or checked in ahead of them.
@Entity
@Table(name = "notifications")
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String type; // EMERGENCY_BOOKING, URGENT_BOOKING, DELAY_NOTICE
    private String studentUsername; // the student this alert is ABOUT
    private String studentName;
    private String recipientUsername; // null = staff broadcast; set = that student only
    @Column(length = 600)
    private String message;
    private LocalDateTime createdAt;
    private boolean read = false;
    private Long appointmentId;

    public Notification() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public String getStudentUsername() { return studentUsername; }
    public void setStudentUsername(String studentUsername) { this.studentUsername = studentUsername; }

    public String getStudentName() { return studentName; }
    public void setStudentName(String studentName) { this.studentName = studentName; }

    public String getRecipientUsername() { return recipientUsername; }
    public void setRecipientUsername(String recipientUsername) { this.recipientUsername = recipientUsername; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public boolean isRead() { return read; }
    public void setRead(boolean read) { this.read = read; }

    public Long getAppointmentId() { return appointmentId; }
    public void setAppointmentId(Long appointmentId) { this.appointmentId = appointmentId; }
}
