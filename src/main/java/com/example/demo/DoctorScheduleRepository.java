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

    // A time slot is booked as a unit across every doctor scheduled for that
    // date+time, so these two helpers keep all of a time slot's rows in sync.
    // Archived rows are excluded since a removed slot no longer participates
    // in booking/availability for that date+time.
    default boolean anyBookedForTimeSlot(LocalDate scheduleDate, String timeSlot) {
        return findByScheduleDateAndTimeSlotAndArchivedFalse(scheduleDate, timeSlot).stream().anyMatch(DoctorSchedule::isBooked);
    }

    default void setBookedForTimeSlot(LocalDate scheduleDate, String timeSlot, boolean booked) {
        List<DoctorSchedule> slots = findByScheduleDateAndTimeSlotAndArchivedFalse(scheduleDate, timeSlot);
        slots.forEach(s -> s.setBooked(booked));
        saveAll(slots);
    }
}