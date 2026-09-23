package com.example.demo;

import java.time.LocalDate;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "doctor_schedule")
public class DoctorSchedule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String doctorName;
    private LocalDate scheduleDate;
    private String timeSlot; // e.g. "09:00 AM - 09:30 AM"
    private boolean booked = false;
    // Slots are archived (soft-removed) rather than hard-deleted, so removed
    // slots stay in the database for audit purposes but are hidden from
    // students/staff and excluded from availability and conflict checks.
    // Nullable Boolean (not a primitive) so Hibernate adds this as a nullable
    // column on an existing table - a NOT NULL column can't be added to a
    // table that already has rows without a default, which breaks ddl-auto
    // on every pre-existing database. isArchived() treats null as false, so
    // pre-existing rows are correctly treated as not archived.
    private Boolean archived = Boolean.FALSE;

    public DoctorSchedule() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getDoctorName() { return doctorName; }
    public void setDoctorName(String doctorName) { this.doctorName = doctorName; }

    public LocalDate getScheduleDate() { return scheduleDate; }
    public void setScheduleDate(LocalDate scheduleDate) { this.scheduleDate = scheduleDate; }

    public String getTimeSlot() { return timeSlot; }
    public void setTimeSlot(String timeSlot) { this.timeSlot = timeSlot; }

    public boolean isBooked() { return booked; }
    public void setBooked(boolean booked) { this.booked = booked; }

    public boolean isArchived() { return archived != null && archived; }
    public void setArchived(boolean archived) { this.archived = archived; }

    // Package-visible raw accessor (may be null for rows that predate this
    // column) used only by DataSeeder's one-time backfill migration.
    Boolean getArchivedRaw() { return archived; }
}