package com.example.demo;

import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDate;
import java.util.List;

public interface AppointmentRepository extends JpaRepository<Appointment, Long> {

    List<Appointment> findByAppointmentDateBetween(LocalDate start, LocalDate end);

    List<Appointment> findByStatus(String status);

    List<Appointment> findByDoctorName(String doctorName);
}