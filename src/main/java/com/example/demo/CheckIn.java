package com.example.demo;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "check_ins")
public class CheckIn {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "appointment_id", nullable = true)
    private Appointment appointment;

    private String studentId;
    private String temperature;
    private String bloodPressure;

    @Column(columnDefinition = "TEXT")
    private String presentingComplaint;

    private LocalDateTime checkInTime;
    private String checkedInBy;

    public CheckIn() {}

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Appointment getAppointment() { return appointment; }
    public void setAppointment(Appointment appointment) { this.appointment = appointment; }

    public String getStudentId() { return studentId; }
    public void setStudentId(String studentId) { this.studentId = studentId; }

    public String getTemperature() { return temperature; }
    public void setTemperature(String temperature) { this.temperature = temperature; }

    public String getBloodPressure() { return bloodPressure; }
    public void setBloodPressure(String bloodPressure) { this.bloodPressure = bloodPressure; }

    public String getPresentingComplaint() { return presentingComplaint; }
    public void setPresentingComplaint(String presentingComplaint) { this.presentingComplaint = presentingComplaint; }

    public LocalDateTime getCheckInTime() { return checkInTime; }
    public void setCheckInTime(LocalDateTime checkInTime) { this.checkInTime = checkInTime; }

    public String getCheckedInBy() { return checkedInBy; }
    public void setCheckedInBy(String checkedInBy) { this.checkedInBy = checkedInBy; }
}
