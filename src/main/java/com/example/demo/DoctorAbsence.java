package com.example.demo;

import java.time.LocalDate;
import java.time.LocalDateTime;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

// A day a doctor was marked unavailable (e.g. off sick). Kept after the
// doctor's times are removed so nurses and admins can see the absence, follow
// up with the affected students, and arrange cover. Marking the same doctor
// and day again adds to this record rather than creating a second one.
@Entity
@Table(name = "doctor_absences")
public class DoctorAbsence {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String doctorName;
    private LocalDate absenceDate;
    private String markedBy;
    private LocalDateTime markedAt;
    private int removedSlots;
    private int cancelledCount;
    private int keptCheckedIn; // students already checked in, left for staff to see in person

    public DoctorAbsence() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getDoctorName() { return doctorName; }
    public void setDoctorName(String doctorName) { this.doctorName = doctorName; }

    public LocalDate getAbsenceDate() { return absenceDate; }
    public void setAbsenceDate(LocalDate absenceDate) { this.absenceDate = absenceDate; }

    public String getMarkedBy() { return markedBy; }
    public void setMarkedBy(String markedBy) { this.markedBy = markedBy; }

    public LocalDateTime getMarkedAt() { return markedAt; }
    public void setMarkedAt(LocalDateTime markedAt) { this.markedAt = markedAt; }

    public int getRemovedSlots() { return removedSlots; }
    public void setRemovedSlots(int removedSlots) { this.removedSlots = removedSlots; }

    public int getCancelledCount() { return cancelledCount; }
    public void setCancelledCount(int cancelledCount) { this.cancelledCount = cancelledCount; }

    public int getKeptCheckedIn() { return keptCheckedIn; }
    public void setKeptCheckedIn(int keptCheckedIn) { this.keptCheckedIn = keptCheckedIn; }
}
