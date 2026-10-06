package com.example.demo;

// Single place for password rules, shared by registration, admin account
// creation, admin reset and self-service change.
public final class PasswordRules {
    public static final int MIN = 6;
    public static final int MAX = 100;

    private PasswordRules() {}

    /** Returns an error message, or null when the password is acceptable. */
    public static String check(String password, String username) {
        if (password == null || password.isBlank()) return "A password is required.";
        if (password.length() < MIN || password.length() > MAX) {
            return "Password must be between " + MIN + " and " + MAX + " characters.";
        }
        if (!password.matches(".*[A-Za-z].*") || !password.matches(".*\\d.*")) {
            return "Password must contain at least one letter and one number.";
        }
        if (password.chars().anyMatch(Character::isWhitespace)) {
            return "Password cannot contain spaces.";
        }
        if (username != null && password.equalsIgnoreCase(username)) {
            return "Password cannot be the same as the username.";
        }
        return null;
    }
}
