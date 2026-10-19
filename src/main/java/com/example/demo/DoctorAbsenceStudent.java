package com.example.demo;

import java.time.LocalDateTime;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

// A student whose appointment was cancelled by a doctor absence. "Contacted"
// is ticked by a nurse or admin once they've reached the student, so two
// people don't phone the same student and the admin can see everyone was told.
@Entity
@Table(name = "doctor_absence_students")
public class DoctorAbsenceStudent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long absenceId;
    private Long appointmentId;
    private String studentId;
    private String fullName;
    private String timeSlot;
    private boolean contacted = false;
    private String contactedBy;
    private LocalDateTime contactedAt;

    public DoctorAbsenceStudent() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getAbsenceId() { return absenceId; }
    public void setAbsenceId(Long absenceId) { this.absenceId = absenceId; }

    public Long getAppointmentId() { return appointmentId; }
    public void setAppointmentId(Long appointmentId) { this.appointmentId = appointmentId; }

    public String getStudentId() { return studentId; }
    public void setStudentId(String studentId) { this.studentId = studentId; }

    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }

    public String getTimeSlot() { return timeSlot; }
    public void setTimeSlot(String timeSlot) { this.timeSlot = timeSlot; }

    public boolean isContacted() { return contacted; }
    public void setContacted(boolean contacted) { this.contacted = contacted; }

    public String getContactedBy() { return contactedBy; }
    public void setContactedBy(String contactedBy) { this.contactedBy = contactedBy; }

    public LocalDateTime getContactedAt() { return contactedAt; }
    public void setContactedAt(LocalDateTime contactedAt) { this.contactedAt = contactedAt; }
}
