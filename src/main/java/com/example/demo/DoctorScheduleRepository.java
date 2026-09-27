package com.example.demo;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface DoctorScheduleRepository extends JpaRepository<DoctorSchedule, Long> {
    List<DoctorSchedule> findByScheduleDate(LocalDate date);
    List<DoctorSchedule> findByDoctorNameAndScheduleDate(String doctorName, LocalDate date);
    List<DoctorSchedule> findByScheduleDateAndTimeSlot(LocalDate scheduleDate, String timeSlot);

    // Archived-excluding variants for anything student/staff-facing (availability,
    // conflict checks, management views) - the plain variants above are kept for
    // internal bookkeeping (e.g. DataSeeder's top-up dedup) where an archived row
    // must still count as "already exists" so it doesn't silently reappear.
    List<DoctorSchedule> findByScheduleDateAndArchivedFalse(LocalDate date);
    List<DoctorSchedule> findByDoctorNameAndScheduleDateAndArchivedFalse(String doctorName, LocalDate date);
    List<DoctorSchedule> findByScheduleDateAndTimeSlotAndArchivedFalse(LocalDate scheduleDate, String timeSlot);

    @Query("SELECT DISTINCT s.doctorName FROM DoctorSchedule s ORDER BY s.doctorName")
    List<String> findDistinctDoctorNames();

    // Each doctor's schedule row tracks its own booked state independently -
    // one doctor's slot at 9am being taken has no bearing on another doctor's
    // 9am slot. Appointment.scheduleSlotId is the single row this frees.
    default void freeSlot(Long slotId) {
        if (slotId == null) return;
        findById(slotId).ifPresent(s -> {
            s.setBooked(false);
            save(s);
        });
    }
}