package com.example.demo;

// Outcome of a best-effort automatic check of an uploaded receipt PDF: this
// only confirms the document's TEXT contains the expected amount and name -
// it does not confirm a real payment occurred (no bank/gateway integration).
// Auto-approval only happens when both signals are found; anything else
// falls back to the existing manual admin review, exactly as before.
public class ReceiptVerificationResult {

    private final boolean amountFound;
    private final boolean nameFound;
    private final boolean textExtracted;

    public ReceiptVerificationResult(boolean amountFound, boolean nameFound, boolean textExtracted) {
        this.amountFound = amountFound;
        this.nameFound = nameFound;
        this.textExtracted = textExtracted;
    }

    public boolean isAutoApproved() {
        return amountFound && nameFound;
    }

    public String summary() {
        if (!textExtracted) {
            return "System check: no readable text found in the PDF (may be a scanned image) - manual review required.";
        }
        return "System check: " + (amountFound ? "✓ fee amount found" : "✗ fee amount not found")
                + " | " + (nameFound ? "✓ student name found" : "✗ student name not found")
                + (isAutoApproved() ? " - auto-approved" : " - needs manual review");
    }
}
