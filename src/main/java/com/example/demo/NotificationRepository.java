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

    // A student is sent at most one delay notice a day.
    boolean existsByTypeAndRecipientUsernameAndCreatedAtGreaterThanEqual(
            String type, String recipientUsername, LocalDateTime since);

    // Today's staff delay announcement, if any (DELAY_ANNOUNCED).
    Notification findFirstByTypeAndCreatedAtGreaterThanEqualOrderByCreatedAtDesc(String type, LocalDateTime since);

    // A student's emergency alerts that nobody has resolved yet.
    List<Notification> findByTypeAndStudentUsernameAndResolvedAtIsNullOrderByCreatedAtDesc(String type, String studentUsername);

    // Staff-feed emergencies raised since a given time (today's emergencies).
    List<Notification> findByRecipientUsernameIsNullAndTypeInAndCreatedAtGreaterThanEqual(List<String> types, LocalDateTime since);
}
