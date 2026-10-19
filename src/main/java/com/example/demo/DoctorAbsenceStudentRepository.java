package com.example.demo;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface DoctorAbsenceStudentRepository extends JpaRepository<DoctorAbsenceStudent, Long> {
    List<DoctorAbsenceStudent> findByAbsenceIdInOrderByTimeSlotAsc(Collection<Long> absenceIds);
}
