package com.example.demo;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

// The lockout in LoginAttemptService used to apply only to the login page's own
// endpoint (/api/check-login). Every other endpoint accepts HTTP Basic credentials
// directly, so a password could be guessed there without limit. This filter runs
// before authentication on EVERY request: a locked username is refused outright,
// and each wrong Basic password counts toward the same lockout.
//
// A wrong credential that is identical to the last wrong one for that username
// counts once, not again. Otherwise a stale browser tab (open since before a password
// change or reset) repeating its old password every few seconds would lock the real
// user out of their own account. A guesser changes the password each time, so each
// guess still counts.
public class LoginLockoutFilter extends OncePerRequestFilter {

    private final LoginAttemptService loginAttemptService;
    private final Map<String, String> lastFailedCredential = new ConcurrentHashMap<>();

    public LoginLockoutFilter(LoginAttemptService loginAttemptService) {
        this.loginAttemptService = loginAttemptService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        String username = basicUsername(header);

        if (username != null && loginAttemptService.isLocked(username)) {
            long minutes = loginAttemptService.minutesUntilUnlock(username);
            response.setStatus(429);
            response.setContentType("application/json");
            response.getWriter().write("{\"message\": \"Too many failed login attempts. Please try again in "
                    + minutes + " minute(s).\"}");
            return;
        }

        chain.doFilter(request, response);

        if (username == null) return;
        String key = username.trim().toLowerCase();
        if (response.getStatus() == HttpServletResponse.SC_UNAUTHORIZED) {
            String fingerprint = fingerprint(header);
            String previous = lastFailedCredential.put(key, fingerprint);
            if (!fingerprint.equals(previous)) {
                loginAttemptService.recordFailure(username);
            }
        } else {
            lastFailedCredential.remove(key);
            loginAttemptService.recordSuccess(username);
        }
    }

    // The username from an "Authorization: Basic ..." header, or null if there isn't one.
    private static String basicUsername(String header) {
        if (header == null || !header.regionMatches(true, 0, "Basic ", 0, 6)) return null;
        try {
            String decoded = new String(Base64.getDecoder().decode(header.substring(6).trim()), StandardCharsets.UTF_8);
            int colon = decoded.indexOf(':');
            String name = colon < 0 ? decoded : decoded.substring(0, colon);
            return name.isBlank() ? null : name;
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static String fingerprint(String header) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(header.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            return header;
        }
    }
}
