package com.example.demo;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface AppointmentRepository extends JpaRepository<Appointment, Long> {

    List<Appointment> findByAppointmentDateBetween(LocalDate start, LocalDate end);

    List<Appointment> findByStatus(String status);

    List<Appointment> findByStudentName(String studentName);

    List<Appointment> findByDoctorName(String doctorName);

    long countByStatus(String status);

    @Query("SELECT a.visitReason, COUNT(a) FROM Appointment a GROUP BY a.visitReason")
    List<Object[]> countByVisitReason();

    @Query("SELECT a.doctorName, COUNT(a) FROM Appointment a GROUP BY a.doctorName")
    List<Object[]> countByDoctor();
}
