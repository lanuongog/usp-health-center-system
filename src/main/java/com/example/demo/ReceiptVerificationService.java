package com.example.demo;

import java.io.IOException;
import java.util.Locale;
import java.util.regex.Pattern;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Service;

// Extracts text from an uploaded receipt PDF and checks it for the expected
// fee amount and the student's name, so an obviously-matching receipt can be
// approved instantly instead of waiting in the admin queue. This is pattern
// matching against the document's text, not real payment verification -
// there is no bank/payment-gateway integration (that's explicitly out of
// scope), so it deliberately only auto-approves on a confident match and
// otherwise leaves the existing manual admin review untouched.
@Service
public class ReceiptVerificationService {

    private static final int MIN_SURNAME_LENGTH = 3;

    public ReceiptVerificationResult verify(byte[] pdfBytes, double feeAmount, String fullName) {
        String text = extractText(pdfBytes);
        if (text == null || text.isBlank()) {
            return new ReceiptVerificationResult(false, false, false);
        }

        String normalized = text.toLowerCase(Locale.ROOT);

        String amountWithCents = String.format(Locale.ROOT, "%.2f", feeAmount);
        boolean amountFound = normalized.contains(amountWithCents)
                || containsStandaloneNumber(normalized, String.valueOf((long) feeAmount));

        boolean nameFound = false;
        if (fullName != null && !fullName.isBlank()) {
            String[] parts = fullName.trim().toLowerCase(Locale.ROOT).split("\\s+");
            String surname = parts[parts.length - 1];
            if (surname.length() >= MIN_SURNAME_LENGTH) {
                nameFound = containsWord(normalized, surname);
            }
        }

        return new ReceiptVerificationResult(amountFound, nameFound, true);
    }

    private boolean containsWord(String haystack, String word) {
        if (word == null || word.isBlank()) return false;
        return Pattern.compile("\\b" + Pattern.quote(word) + "\\b").matcher(haystack).find();
    }

    // Plain \b word-boundary treats "." as a non-word character, so it would
    // wrongly match "50" inside "12.50" or "50.25" (a decimal point counts as
    // a boundary either side of the digits). This requires the number to not
    // be directly adjacent to another digit or a decimal point at all.
    private boolean containsStandaloneNumber(String haystack, String number) {
        if (number == null || number.isBlank()) return false;
        return Pattern.compile("(?<![\\d.])" + Pattern.quote(number) + "(?![\\d.])").matcher(haystack).find();
    }

    private String extractText(byte[] pdfBytes) {
        try (PDDocument document = Loader.loadPDF(pdfBytes)) {
            return new PDFTextStripper().getText(document);
        } catch (IOException | RuntimeException e) {
            // Corrupt/unreadable/encrypted PDF - fall back to manual review
            // rather than failing the upload outright.
            return null;
        }
    }
}
