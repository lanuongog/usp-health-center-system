package com.example.demo;

import java.util.regex.Pattern;

// Single place for "what may be typed in this box" rules, used by every controller.
public final class InputRules {
    private InputRules() {}

    // Letters (any language), spaces, apostrophes (' and ’) and hyphens. No digits.
    private static final Pattern NAME = Pattern.compile("^[\\p{L}][\\p{L} '’-]*$");
    // Letters, digits, whitespace and common punctuation only.
    private static final Pattern FREE_TEXT = Pattern.compile("^[\\p{L}\\p{N}\\s.,;:!?'’\"()\\-–—/&%+#@°_\\[\\]*]+$");
    private static final Pattern LOCATION = Pattern.compile("^[\\p{L}\\p{N} ,.\\-'’/#()]+$");
    private static final Pattern USERNAME = Pattern.compile("^[A-Za-z0-9._@-]+$");

    public static String name(String value, String label) {
        if (value == null || !NAME.matcher(value.trim()).matches()) {
            return label + " can only contain letters, spaces, apostrophes (') and hyphens (-).";
        }
        return null;
    }

    // Returns an error message, or null when the text is acceptable.
    public static String freeText(String value, String label) {
        if (value == null) return null;
        String v = value.trim();
        if (v.isEmpty()) return null;
        if (!FREE_TEXT.matcher(v).matches()) {
            return label + " contains characters that are not allowed. Use letters, numbers and normal punctuation only.";
        }
        long letters = v.chars().filter(Character::isLetter).count();
        if (letters < 2) {
            return label + " must contain at least 2 letters.";
        }
        return null;
    }

    public static String location(String value, String label) {
        if (value == null || value.isBlank()) return null;
        if (!LOCATION.matcher(value.trim()).matches()) {
            return label + " can only contain letters, numbers, spaces and , . - ' / # ( ).";
        }
        return null;
    }

    public static String username(String value) {
        if (value == null || !USERNAME.matcher(value).matches()) {
            return "Username can only contain letters, numbers and . _ @ - (no spaces or other symbols).";
        }
        return null;
    }
}
