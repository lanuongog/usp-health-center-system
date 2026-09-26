package com.example.demo;

import java.time.LocalDateTime;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Service;

// Login throttling / account lockout: unlimited failed logins with no delay
// let an attacker brute-force a password. Tracked in-memory per username -
// stateless Basic Auth means there's no server session to hang this off of.
@Service
public class LoginAttemptService {

    private static final int MAX_ATTEMPTS = 5;
    private static final long LOCKOUT_MINUTES = 15;

    private static class Attempts {
        int failedCount = 0;
        LocalDateTime lockedUntil = null;
    }

    private final ConcurrentHashMap<String, Attempts> attemptsByUsername = new ConcurrentHashMap<>();

    public boolean isLocked(String username) {
        Attempts a = attemptsByUsername.get(normalize(username));
        if (a == null || a.lockedUntil == null) return false;
        if (LocalDateTime.now().isAfter(a.lockedUntil)) {
            attemptsByUsername.remove(normalize(username));
            return false;
        }
        return true;
    }

    public long minutesUntilUnlock(String username) {
        Attempts a = attemptsByUsername.get(normalize(username));
        if (a == null || a.lockedUntil == null) return 0;
        long minutes = java.time.Duration.between(LocalDateTime.now(), a.lockedUntil).toMinutes();
        return Math.max(minutes + 1, 1);
    }

    public void recordFailure(String username) {
        Attempts a = attemptsByUsername.computeIfAbsent(normalize(username), k -> new Attempts());
        a.failedCount++;
        if (a.failedCount >= MAX_ATTEMPTS) {
            a.lockedUntil = LocalDateTime.now().plusMinutes(LOCKOUT_MINUTES);
        }
    }

    public void recordSuccess(String username) {
        attemptsByUsername.remove(normalize(username));
    }

    private String normalize(String username) {
        return username == null ? "" : username.trim().toLowerCase();
    }
}
