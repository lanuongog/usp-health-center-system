package com.example.demo;

import java.time.LocalDate;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "appointments")
public class Appointment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String studentName;
    private String doctorName;
    private LocalDate appointmentDate;
    private String timeSlot;
    private String visitReason;
    private String urgencyLevel; // e.g. "Routine", "Urgent", "Emergency"
    private String status; // e.g. "PENDING", "COMPLETED", "NO_SHOW", "CANCELLED"
    private Long scheduleSlotId;
    private String diagnosis;
    private String consultationNotes;
    private String consultedByDoctor;

    public Appointment() {}

    // Getters and setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getStudentName() { return studentName; }
    public void setStudentName(String studentName) { this.studentName = studentName; }

    public String getDoctorName() { return doctorName; }
    public void setDoctorName(String doctorName) { this.doctorName = doctorName; }

    public LocalDate getAppointmentDate() { return appointmentDate; }
    public void setAppointmentDate(LocalDate appointmentDate) { this.appointmentDate = appointmentDate; }

    public String getTimeSlot() { return timeSlot; }
    public void setTimeSlot(String timeSlot) { this.timeSlot = timeSlot; }

    public String getVisitReason() { return visitReason; }
    public void setVisitReason(String visitReason) { this.visitReason = visitReason; }

    public String getUrgencyLevel() { return urgencyLevel; }
    public void setUrgencyLevel(String urgencyLevel) { this.urgencyLevel = urgencyLevel; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getDiagnosis() { return diagnosis; }
    public void setDiagnosis(String diagnosis) { this.diagnosis = diagnosis; }

    public String getConsultationNotes() { return consultationNotes; }
    public void setConsultationNotes(String consultationNotes) { this.consultationNotes = consultationNotes; }

    public String getConsultedByDoctor() { return consultedByDoctor; }
    public void setConsultedByDoctor(String consultedByDoctor) { this.consultedByDoctor = consultedByDoctor; }

    public Long getScheduleSlotId() { return scheduleSlotId; }
    public void setScheduleSlotId(Long scheduleSlotId) { this.scheduleSlotId = scheduleSlotId; }
}