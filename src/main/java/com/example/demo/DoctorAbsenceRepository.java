package com.example.demo;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface DoctorAbsenceRepository extends JpaRepository<DoctorAbsence, Long> {
    Optional<DoctorAbsence> findByDoctorNameAndAbsenceDate(String doctorName, LocalDate absenceDate);
    List<DoctorAbsence> findByAbsenceDateBetweenOrderByAbsenceDateAscDoctorNameAsc(LocalDate from, LocalDate to);
}
