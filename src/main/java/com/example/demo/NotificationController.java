package com.example.demo;

import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
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

    public NotificationController(NotificationRepository notificationRepository) {
        this.notificationRepository = notificationRepository;
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
}
