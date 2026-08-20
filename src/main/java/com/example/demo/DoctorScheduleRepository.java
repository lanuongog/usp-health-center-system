package com.example.demo;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface DoctorScheduleRepository extends JpaRepository<DoctorSchedule, Long> {
    List<DoctorSchedule> findByScheduleDate(LocalDate date);
    List<DoctorSchedule> findByDoctorNameAndScheduleDate(String doctorName, LocalDate date);
}