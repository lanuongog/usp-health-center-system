package com.example.demo;

import jakarta.persistence.*;
import java.time.LocalDateTime;

// NFR-PRIV-03: log account activity related to medical record access for accountability.
@Entity
@Table(name = "audit_logs")
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String actorUsername;
    private String actorRole;
    private String action;
    private String targetStudent;
    private String details;
    private LocalDateTime timestamp;

    public AuditLog() {}

    public AuditLog(String actorUsername, String actorRole, String action, String targetStudent, String details) {
        this.actorUsername = actorUsername;
        this.actorRole = actorRole;
        this.action = action;
        this.targetStudent = targetStudent;
        this.details = details;
        this.timestamp = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getActorUsername() { return actorUsername; }
    public void setActorUsername(String actorUsername) { this.actorUsername = actorUsername; }

    public String getActorRole() { return actorRole; }
    public void setActorRole(String actorRole) { this.actorRole = actorRole; }

    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }

    public String getTargetStudent() { return targetStudent; }
    public void setTargetStudent(String targetStudent) { this.targetStudent = targetStudent; }

    public String getDetails() { return details; }
    public void setDetails(String details) { this.details = details; }

    public LocalDateTime getTimestamp() { return timestamp; }
    public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }
}
