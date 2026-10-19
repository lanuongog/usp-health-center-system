package com.example.demo;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface AppointmentRepository extends JpaRepository<Appointment, Long> {

    List<Appointment> findByAppointmentDateBetween(LocalDate start, LocalDate end);

    List<Appointment> findByStatus(String status);

    List<Appointment> findByStudentName(String studentName);

    List<Appointment> findByDoctorName(String doctorName);

    List<Appointment> findByConsultedByDoctor(String consultedByDoctor);

    List<Appointment> findByStudentNameAndStatus(String studentName, String status);

    List<Appointment> findByStudentNameAndStatusAndAppointmentDateAndTimeSlot(
            String studentName, String status, LocalDate appointmentDate, String timeSlot);

    // Appointments tied to particular schedule rows (e.g. one doctor's day).
    List<Appointment> findByScheduleSlotIdIn(Collection<Long> scheduleSlotIds);

    long countByStatus(String status);

    @Query("SELECT a.visitReason, COUNT(a) FROM Appointment a GROUP BY a.visitReason")
    List<Object[]> countByVisitReason();

    @Query("SELECT a.doctorName, COUNT(a) FROM Appointment a GROUP BY a.doctorName")
    List<Object[]> countByDoctor();
}
