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

    // Limits a student to one emergency alert per day.
    boolean existsByTypeAndStudentUsernameAndCreatedAtGreaterThanEqual(String type, String studentUsername, LocalDateTime since);
    Notification findFirstByTypeAndStudentUsernameAndCreatedAtGreaterThanEqualOrderByCreatedAtDesc(
            String type, String studentUsername, LocalDateTime since);

    // Avoids sending a student a second delay notice about the same emergency
    // (e.g. once when the alert is sent, again when they're checked in).
    boolean existsByTypeAndRecipientUsernameAndStudentUsernameAndCreatedAtGreaterThanEqual(
            String type, String recipientUsername, String studentUsername, LocalDateTime since);

    // Unread emergency alerts for one student (cleared when staff check them in).
    List<Notification> findByTypeAndStudentUsernameAndReadFalse(String type, String studentUsername);

    // Staff-feed emergencies raised since a given time (today's emergencies).
    List<Notification> findByRecipientUsernameIsNullAndTypeInAndCreatedAtGreaterThanEqual(List<String> types, LocalDateTime since);
}
