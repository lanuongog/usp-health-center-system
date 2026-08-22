package com.example.demo;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface PatientRepository extends JpaRepository<Patient, Long> {

    @Query("SELECT p FROM Patient p ORDER BY CASE WHEN p.urgencyLevel = 'HIGH' THEN 1 WHEN p.urgencyLevel = 'MEDIUM' THEN 2 WHEN p.urgencyLevel = 'LOW' THEN 3 ELSE 4 END, p.checkInTime ASC")
    List<Patient> findAllPrioritizedQueue();
}