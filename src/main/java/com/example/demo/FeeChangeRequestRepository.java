package com.example.demo;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface FeeChangeRequestRepository extends JpaRepository<FeeChangeRequest, Long> {
    Optional<FeeChangeRequest> findFirstByStatus(String status);
    List<FeeChangeRequest> findTop50ByOrderByProposedAtDesc();
}
