package com.example.demo;

import java.time.LocalDateTime;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

// Two kinds of alert live in this one table:
//  - Staff-facing (recipientUsername == null): a student taps the one-click
//    emergency button, or a nurse/doctor completes an emergency check-in.
//    Shown to every nurse/doctor/admin.
//  - Student-facing (recipientUsername == that student's username): a "your
//    appointment may be delayed" notice sent to students who have a visit
//    TODAY when an emergency is raised or checked in ahead of them.
@Entity
@Table(name = "notifications")
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String type; // EMERGENCY_BOOKING, URGENT_BOOKING, DELAY_NOTICE, APPOINTMENT_CANCELLED
    private String studentUsername; // the student this alert is ABOUT
    private String studentName;
    private String recipientUsername; // null = staff broadcast; set = that student only
    private String message;
    private LocalDateTime createdAt;
    private boolean read = false;
    private Long appointmentId;

    // Emergency alert lifecycle (EMERGENCY_ALERT only). An alert keeps
    // re-appearing on every nurse/doctor screen until it is resolved -
    // acknowledging only records who is responding.
    private String acknowledgedBy;
    private LocalDateTime acknowledgedAt;
    private String resolvedBy; // staff username, or the student themselves if they cancelled it
    private LocalDateTime resolvedAt;

    // Unresolved alerts older than this are treated as stale, so a forgotten
    // one doesn't keep popping up on staff screens forever.
    public static final int EMERGENCY_ACTIVE_HOURS = 12;

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

    public String getAcknowledgedBy() { return acknowledgedBy; }
    public void setAcknowledgedBy(String acknowledgedBy) { this.acknowledgedBy = acknowledgedBy; }

    public LocalDateTime getAcknowledgedAt() { return acknowledgedAt; }
    public void setAcknowledgedAt(LocalDateTime acknowledgedAt) { this.acknowledgedAt = acknowledgedAt; }

    public String getResolvedBy() { return resolvedBy; }
    public void setResolvedBy(String resolvedBy) { this.resolvedBy = resolvedBy; }

    public LocalDateTime getResolvedAt() { return resolvedAt; }
    public void setResolvedAt(LocalDateTime resolvedAt) { this.resolvedAt = resolvedAt; }

    // Serialized as "activeEmergency" so the staff pages know which alerts
    // still need to pop up.
    public boolean isActiveEmergency() {
        return "EMERGENCY_ALERT".equals(type) && resolvedAt == null && createdAt != null
                && createdAt.isAfter(LocalDateTime.now().minusHours(EMERGENCY_ACTIVE_HOURS));
    }

    // The student withdrew their own alert (e.g. tapped the button by mistake).
    public boolean isCancelledByStudent() {
        return resolvedBy != null && resolvedBy.equals(studentUsername);
    }

    public void resolve(String byUsername) {
        this.resolvedBy = byUsername;
        this.resolvedAt = LocalDateTime.now();
        this.read = true;
    }
}
