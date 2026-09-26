package com.example.demo;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

@Service
public class AuditLogService {

    private final AuditLogRepository auditLogRepository;
    private final UserRepository userRepository;

    public AuditLogService(AuditLogRepository auditLogRepository, UserRepository userRepository) {
        this.auditLogRepository = auditLogRepository;
        this.userRepository = userRepository;
    }

    public void recordAccess(Authentication auth, String action, String targetStudent, String details) {
        String actorUsername = auth != null ? auth.getName() : "unknown";
        String actorRole = userRepository.findByUsername(actorUsername)
                .map(User::getRole)
                .orElse("unknown");
        auditLogRepository.save(new AuditLog(actorUsername, actorRole, action, targetStudent, details));
    }
}
