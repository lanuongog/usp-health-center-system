package com.example.demo;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface DoctorScheduleRepository extends JpaRepository<DoctorSchedule, Long> {
    List<DoctorSchedule> findByScheduleDate(LocalDate date);
    List<DoctorSchedule> findByDoctorNameAndScheduleDate(String doctorName, LocalDate date);
    List<DoctorSchedule> findByScheduleDateAndTimeSlot(LocalDate scheduleDate, String timeSlot);

    @Query("SELECT DISTINCT s.doctorName FROM DoctorSchedule s ORDER BY s.doctorName")
    List<String> findDistinctDoctorNames();

    // A time slot is booked as a unit across every doctor scheduled for that
    // date+time, so these two helpers keep all of a time slot's rows in sync.
    default boolean anyBookedForTimeSlot(LocalDate scheduleDate, String timeSlot) {
        return findByScheduleDateAndTimeSlot(scheduleDate, timeSlot).stream().anyMatch(DoctorSchedule::isBooked);
    }

    default void setBookedForTimeSlot(LocalDate scheduleDate, String timeSlot, boolean booked) {
        List<DoctorSchedule> slots = findByScheduleDateAndTimeSlot(scheduleDate, timeSlot);
        slots.forEach(s -> s.setBooked(booked));
        saveAll(slots);
    }
}