package com.example.demo;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationRepository extends JpaRepository<Notification, Long> {
    // Staff broadcast feed (recipientUsername is null).
    List<Notification> findByRecipientUsernameIsNullOrderByCreatedAtDesc();
    long countByRecipientUsernameIsNullAndReadFalse();

    // Per-student feed (e.g. "your appointment may be delayed" notices).
    List<Notification> findByRecipientUsernameOrderByCreatedAtDesc(String recipientUsername);
    long countByRecipientUsernameAndReadFalse(String recipientUsername);

    // Used to cap students at one emergency alert per rolling 24 hours.
    boolean existsByTypeAndStudentUsernameAndCreatedAtGreaterThanEqual(String type, String studentUsername, LocalDateTime since);

    // Used so a booking only gets one delay notice per emergency student.
    boolean existsByTypeAndAppointmentIdAndStudentUsername(String type, Long appointmentId, String studentUsername);
}
