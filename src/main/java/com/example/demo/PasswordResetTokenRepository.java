package com.example.demo;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, Long> {

    // the newest code that has not been used yet
    Optional<PasswordResetToken> findFirstByStudentUsernameAndUsedFalseOrderByCreatedAtDesc(String studentUsername);

    List<PasswordResetToken> findByStudentUsernameAndUsedFalse(String studentUsername);

    // how many codes were requested recently (used to limit repeat requests)
    long countByStudentUsernameAndCreatedAtAfter(String studentUsername, LocalDateTime since);
}
