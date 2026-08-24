package com.example.demo;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface DoctorScheduleRepository extends JpaRepository<DoctorSchedule, Long> {
    List<DoctorSchedule> findByScheduleDate(LocalDate date);
    List<DoctorSchedule> findByDoctorNameAndScheduleDate(String doctorName, LocalDate date);

    @Query("SELECT DISTINCT s.doctorName FROM DoctorSchedule s ORDER BY s.doctorName")
    List<String> findDistinctDoctorNames();
}