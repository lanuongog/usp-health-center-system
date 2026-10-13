package com.example.demo;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

// Staff-facing alert feed (nurse/doctor/admin): emergency SOS requests and
// urgent/emergency bookings, newest first, so staff are notified of anything
// needing immediate attention without having to comb through every record.
@RestController
@RequestMapping("/notifications")
public class NotificationController {

    private final NotificationRepository notificationRepository;
    private final AuditLogService auditLogService;

    public NotificationController(NotificationRepository notificationRepository, AuditLogService auditLogService) {
        this.notificationRepository = notificationRepository;
        this.auditLogService = auditLogService;
    }

    private Notification findEmergencyAlert(Long id) {
        return notificationRepository.findById(id)
                .filter(n -> "EMERGENCY_ALERT".equals(n.getType()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Emergency alert not found"));
    }

    @GetMapping
    public List<Notification> getAll() {
        return notificationRepository.findByRecipientUsernameIsNullOrderByCreatedAtDesc();
    }

    @GetMapping("/unread-count")
    public Map<String, Long> getUnreadCount() {
        return Map.of("count", notificationRepository.countByRecipientUsernameIsNullAndReadFalse());
    }

    @PostMapping("/{id}/read")
    public Notification markRead(@PathVariable Long id) {
        Notification notification = notificationRepository.findById(id)
                .filter(n -> n.getRecipientUsername() == null)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Notification not found"));
        notification.setRead(true);
        return notificationRepository.save(notification);
    }

    @PostMapping("/read-all")
    public Map<String, String> markAllRead() {
        List<Notification> unread = notificationRepository.findByRecipientUsernameIsNullOrderByCreatedAtDesc().stream()
                .filter(n -> !n.isRead())
                .toList();
        unread.forEach(n -> n.setRead(true));
        notificationRepository.saveAll(unread);
        return Map.of("message", "All notifications marked as read.");
    }

    // "I'm responding" - records who picked it up so other staff (and the
    // student) can see it. The alert stays active and keeps re-appearing on
    // staff screens until it's resolved. The first responder is kept.
    @PostMapping("/{id}/acknowledge")
    public Notification acknowledgeEmergency(@PathVariable Long id, Authentication auth) {
        Notification alert = findEmergencyAlert(id);
        if (alert.getAcknowledgedBy() == null) {
            alert.setAcknowledgedBy(auth.getName());
            alert.setAcknowledgedAt(LocalDateTime.now());
            notificationRepository.save(alert);
            auditLogService.recordAccess(auth, "EMERGENCY_ACKNOWLEDGED", alert.getStudentUsername(), "Alert #" + id);
        }
        return alert;
    }

    // The emergency has been dealt with - stops it popping up for everyone.
    // An Emergency Check-in resolves the student's alert automatically.
    @PostMapping("/{id}/resolve")
    public Notification resolveEmergency(@PathVariable Long id, Authentication auth) {
        Notification alert = findEmergencyAlert(id);
        if (alert.getResolvedAt() == null) {
            alert.resolve(auth.getName());
            notificationRepository.save(alert);
            auditLogService.recordAccess(auth, "EMERGENCY_RESOLVED", alert.getStudentUsername(), "Alert #" + id);
        }
        return alert;
    }
}
