package com.example.demo;

import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Service;

// Extracts text from an uploaded receipt (directly if it's a born-digital PDF,
// or via OCR if it's a phone photo/scan of the printed slip - the common
// case) and checks it for the signals a genuine USP Health Services Fee
// receipt carries, to give the admin reviewing it a head start. This is
// pattern matching against the document's text, not real payment
// verification - there is no bank/payment-gateway integration (that's
// explicitly out of scope) and a forged receipt with the right text would
// pass every check, so it never auto-approves; every receipt still goes
// through manual admin review (see ReceiptVerificationResult).
@Service
public class ReceiptVerificationService {

    private static final int MIN_SURNAME_LENGTH = 3;
    private static final int MIN_EXTRACTED_TEXT_LENGTH = 20;

    // Currency cues that must appear near a bare (non-decimal) number for it to
    // count as the fee amount, so a coincidental "50" elsewhere in an unrelated
    // document (a page number, a phone number, an address) isn't mistaken for it.
    private static final List<String> CURRENCY_CUES = List.of("$", "fjd", "total", "paid", "amount", "fee", "charge");

    // Boilerplate every genuine USP Health Services Fee receipt carries,
    // regardless of cashier/date/receipt number, which vary per receipt.
    private static final List<String> ISSUER_MARKERS = List.of(
            "university of the south pacific", "accounts receivable", "official receipt",
            "thank you for your payment", "void if not signed"
    );
    private static final int MIN_ISSUER_MARKERS = 3;

    private final OcrService ocrService;

    public ReceiptVerificationService(OcrService ocrService) {
        this.ocrService = ocrService;
    }

    public ReceiptVerificationResult verify(byte[] pdfBytes, double feeAmount, String fullName, String studentId) {
        boolean viaOcr = false;
        String text = extractText(pdfBytes);
        if (text == null || text.trim().length() < MIN_EXTRACTED_TEXT_LENGTH) {
            // Likely an image-only PDF (a photographed/scanned paper receipt) -
            // no selectable text layer for PDFTextStripper to read at all.
            text = ocrService.extractText(pdfBytes);
            viaOcr = true;
        }
        if (text == null || text.isBlank()) {
            return new ReceiptVerificationResult(false, false, false, false, false, viaOcr);
        }

        String normalized = text.toLowerCase(Locale.ROOT);

        String amountWithCents = String.format(Locale.ROOT, "%.2f", feeAmount);
        boolean amountFound = normalized.contains(amountWithCents)
                || containsNumberWithCurrencyContext(normalized, String.valueOf((long) feeAmount));

        boolean nameFound = false;
        if (fullName != null && !fullName.isBlank()) {
            String[] parts = fullName.trim().toLowerCase(Locale.ROOT).split("\\s+");
            String surname = parts[parts.length - 1];
            if (surname.length() >= MIN_SURNAME_LENGTH) {
                nameFound = containsWord(normalized, surname);
            }
        }

        boolean accountNumberMatches = studentIdMatchesAccountNumber(text, studentId);
        boolean issuerVerified = countIssuerMarkers(normalized) >= MIN_ISSUER_MARKERS;

        return new ReceiptVerificationResult(amountFound, nameFound, accountNumberMatches, issuerVerified, true, viaOcr);
    }

    private boolean containsWord(String haystack, String word) {
        if (word == null || word.isBlank()) return false;
        return Pattern.compile("\\b" + Pattern.quote(word) + "\\b").matcher(haystack).find();
    }

    // Plain \b word-boundary treats "." as a non-word character, so it would
    // wrongly match "50" inside "12.50" or "50.25" (a decimal point counts as
    // a boundary either side of the digits). This requires the number to not
    // be directly adjacent to another digit or a decimal point at all.
    private boolean containsNumberWithCurrencyContext(String haystack, String number) {
        if (number == null || number.isBlank()) return false;
        Matcher matcher = Pattern.compile("(?<![\\d.])" + Pattern.quote(number) + "(?![\\d.])").matcher(haystack);
        while (matcher.find()) {
            int windowStart = Math.max(0, matcher.start() - 15);
            int windowEnd = Math.min(haystack.length(), matcher.end() + 15);
            String window = haystack.substring(windowStart, windowEnd);
            if (CURRENCY_CUES.stream().anyMatch(window::contains)) {
                return true;
            }
        }
        return false;
    }

    // The receipt's own "Account Number" field is the student ID used to log
    // in - matching it against the logged-in account is a much stronger signal
    // than name/amount alone, since it ties the receipt to this specific
    // account rather than just to a coincidentally-similar document. OCR
    // frequently misreads the leading "S" as "$", so both are accepted.
    private boolean studentIdMatchesAccountNumber(String text, String studentId) {
        if (studentId == null) return false;
        String expectedDigits = studentId.replaceAll("\\D", "");
        if (expectedDigits.length() != 8) return false;

        Matcher matcher = Pattern.compile("account\\s*number[\\s\\S]{0,30}?[sS$]?\\s?(\\d{8})",
                Pattern.CASE_INSENSITIVE).matcher(text);
        while (matcher.find()) {
            if (expectedDigits.equals(matcher.group(1))) {
                return true;
            }
        }
        return false;
    }

    private int countIssuerMarkers(String normalized) {
        return (int) ISSUER_MARKERS.stream().filter(normalized::contains).count();
    }

    private String extractText(byte[] pdfBytes) {
        try (PDDocument document = Loader.loadPDF(pdfBytes)) {
            return new PDFTextStripper().getText(document);
        } catch (IOException | RuntimeException e) {
            // Corrupt/unreadable/encrypted PDF - fall back to OCR/manual review
            // rather than failing the upload outright.
            return null;
        }
    }
}
