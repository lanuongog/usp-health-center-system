package com.example.demo;

// Outcome of a best-effort automatic check of an uploaded receipt: this only
// confirms the document's TEXT (read directly, or via OCR for a photographed
// receipt) contains the expected signals - it does not confirm a real payment
// occurred (no bank/gateway integration), and a forged receipt with the right
// text would pass every check here. Because of that, this is informational
// only and never auto-approves - every uploaded receipt still goes through
// manual admin review, same as before this check existed. It exists purely to
// give the reviewing admin a head start on what to look for.
public class ReceiptVerificationResult {

    private final boolean amountFound;
    private final boolean nameFound;
    private final boolean accountNumberMatches;
    private final boolean issuerVerified;
    private final boolean textExtracted;
    private final boolean viaOcr;

    public ReceiptVerificationResult(boolean amountFound, boolean nameFound, boolean accountNumberMatches,
                                      boolean issuerVerified, boolean textExtracted, boolean viaOcr) {
        this.amountFound = amountFound;
        this.nameFound = nameFound;
        this.accountNumberMatches = accountNumberMatches;
        this.issuerVerified = issuerVerified;
        this.textExtracted = textExtracted;
        this.viaOcr = viaOcr;
    }

    // Always false: a text-matching check can't tell a genuine receipt from a
    // well-made forgery, so every receipt is queued for a human to look at
    // regardless of how many signals matched. See the class comment.
    public boolean isAutoApproved() {
        return false;
    }

    public String summary() {
        if (!textExtracted) {
            return "System check: no readable text found in the PDF, even with OCR - manual review required.";
        }
        String source = viaOcr ? " (read via OCR - a scanned/photographed receipt, so accuracy is limited)" : "";
        return "System check" + source + ": "
                + (issuerVerified ? "✓ USP receipt format" : "✗ USP receipt format not confirmed")
                + " | " + (accountNumberMatches ? "✓ account number matches your student ID" : "✗ account number not confirmed")
                + " | " + (amountFound ? "✓ fee amount found" : "✗ fee amount not found")
                + " | " + (nameFound ? "✓ student name found" : "✗ student name not found")
                + " - needs manual review";
    }
}
