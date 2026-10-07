package com.example.demo;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.springframework.stereotype.Service;

// Renders a consultation report (the map built by DoctorController) as an A4
// PDF, laid out like the on-screen report: header, then Visit / Nurse
// findings / Doctor consultation sections of label-value rows.
@Service
public class ConsultationReportPdfService {

    private static final float MARGIN = 56;
    private static final float LABEL_WIDTH = 150;
    private static final float ROW_PADDING = 9;

    private static final Color INK = new Color(0x17, 0x32, 0x4A);
    private static final Color INK_SOFT = new Color(0x5E, 0x72, 0x81);
    private static final Color BRAND = new Color(0x0E, 0x6F, 0x74);
    private static final Color LINE = new Color(0xDC, 0xE4, 0xE8);

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("EEE d MMM yyyy", Locale.ENGLISH);
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("EEE d MMM yyyy, HH:mm", Locale.ENGLISH);

    private final PDType1Font regular = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
    private final PDType1Font bold = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);

    public byte[] render(Map<String, Object> r) throws IOException {
        try (PDDocument doc = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Writer w = new Writer(doc);

            w.text("USP HEALTH CENTER · LAUCALA CAMPUS", bold, 9, BRAND);
            w.gap(14);
            w.text("Consultation report", bold, 20, INK);
            w.gap(26);
            w.text(str(r.get("studentName")), bold, 15, INK);
            w.gap(4);
            w.text("Student ID " + str(r.get("studentId")) + " · A-" + str(r.get("appointmentId")), regular, 10, INK_SOFT);
            w.gap(10);

            w.section("Visit");
            String date = formatDate(str(r.get("appointmentDate")));
            String slot = formatSlot(str(r.get("timeSlot")));
            w.row("Date", slot.isEmpty() ? date : date + " · " + slot);
            w.row("Reason for visit", str(r.get("visitReason")));
            w.row("Priority", str(r.get("urgencyLevel")));

            w.section("Nurse findings");
            w.row("Checked in by", str(r.get("checkedInBy")));
            w.row("Check-in time", formatDateTime(str(r.get("checkInTime"))));
            w.row("Temperature", withUnit(str(r.get("temperature")), "°C"));
            w.row("Blood pressure", withUnit(str(r.get("bloodPressure")), "mmHg"));
            w.row("Presenting complaint", str(r.get("presentingComplaint")));

            w.section("Doctor consultation");
            w.row("Diagnosis", str(r.get("diagnosis")));
            w.row("Notes", str(r.get("consultationNotes")));
            w.row("Consulted by", doctorLabel(str(r.get("consultedByDoctor"))));

            w.gap(16);
            w.text("Generated " + formatDateTime(str(r.get("generatedAt"))) + " (Fiji time)", regular, 8.5f, INK_SOFT);
            w.gap(12);
            w.text("Confidential medical record. Contains the student's reason for visit, nurse findings and doctor's consultation.",
                    regular, 8.5f, INK_SOFT);

            w.close();
            doc.save(out);
            return out.toByteArray();
        }
    }

    // ---------- Layout ----------

    private class Writer {
        private final PDDocument doc;
        private PDPageContentStream cs;
        private float y;
        private final float width = PDRectangle.A4.getWidth() - 2 * MARGIN;

        Writer(PDDocument doc) throws IOException {
            this.doc = doc;
            newPage();
        }

        private void newPage() throws IOException {
            if (cs != null) cs.close();
            PDPage page = new PDPage(PDRectangle.A4);
            doc.addPage(page);
            cs = new PDPageContentStream(doc, page);
            y = PDRectangle.A4.getHeight() - MARGIN;
        }

        private void ensure(float height) throws IOException {
            if (y - height < MARGIN) newPage();
        }

        void gap(float h) { y -= h; }

        void text(String s, PDType1Font font, float size, Color color) throws IOException {
            for (String line : wrap(s, font, size, width)) {
                ensure(size + 4);
                y -= size;
                draw(line, font, size, color, MARGIN);
                y -= 4;
            }
        }

        void section(String title) throws IOException {
            ensure(60);
            y -= 22;
            draw(title.toUpperCase(Locale.ENGLISH), regular, 8.5f, INK_SOFT, MARGIN);
            y -= 6;
        }

        void row(String label, String value) throws IOException {
            float size = 10.5f;
            float leading = 14;
            List<String> lines = wrap(value.isEmpty() ? "—" : value, bold, size, width - LABEL_WIDTH);
            float height = ROW_PADDING * 2 + leading * lines.size();
            ensure(height);

            float top = y;
            // draw() uses the current y as the text baseline.
            y = top - ROW_PADDING - size;
            draw(label, regular, size, INK_SOFT, MARGIN);
            for (String line : lines) {
                draw(line, bold, size, INK, MARGIN + LABEL_WIDTH);
                y -= leading;
            }
            y = top - height;
            cs.setStrokingColor(LINE);
            cs.setLineWidth(0.6f);
            cs.moveTo(MARGIN, y);
            cs.lineTo(MARGIN + width, y);
            cs.stroke();
        }

        private void draw(String s, PDType1Font font, float size, Color color, float x) throws IOException {
            cs.beginText();
            cs.setFont(font, size);
            cs.setNonStrokingColor(color);
            cs.newLineAtOffset(x, y);
            cs.showText(s);
            cs.endText();
        }

        void close() throws IOException { cs.close(); }
    }

    // Word-wraps to the given width; also strips characters the standard PDF
    // fonts can't encode (e.g. emoji) so free-text clinical notes never break the export.
    private List<String> wrap(String text, PDType1Font font, float size, float maxWidth) throws IOException {
        List<String> lines = new ArrayList<>();
        for (String paragraph : encodable(text, font).split("\\r?\\n")) {
            StringBuilder line = new StringBuilder();
            for (String word : paragraph.split(" ")) {
                String candidate = line.length() == 0 ? word : line + " " + word;
                if (width(candidate, font, size) <= maxWidth) {
                    line.setLength(0);
                    line.append(candidate);
                    continue;
                }
                if (line.length() > 0) {
                    lines.add(line.toString());
                    line.setLength(0);
                }
                // A single word longer than the line is broken by character.
                while (width(word, font, size) > maxWidth) {
                    int cut = word.length() - 1;
                    while (cut > 1 && width(word.substring(0, cut), font, size) > maxWidth) cut--;
                    lines.add(word.substring(0, cut));
                    word = word.substring(cut);
                }
                line.append(word);
            }
            lines.add(line.toString());
        }
        return lines;
    }

    private float width(String s, PDType1Font font, float size) throws IOException {
        return font.getStringWidth(s) / 1000 * size;
    }

    private String encodable(String s, PDType1Font font) {
        StringBuilder sb = new StringBuilder();
        s.codePoints().forEach(cp -> {
            String ch = new String(Character.toChars(cp));
            if (cp == '\n' || cp == '\r') { sb.append(ch); return; }
            if (cp == '\t') { sb.append(' '); return; }
            try {
                font.encode(ch);
                sb.append(ch);
            } catch (IOException | IllegalArgumentException e) {
                sb.append('?');
            }
        });
        return sb.toString();
    }

    // ---------- Formatting ----------

    private static String str(Object o) {
        return o == null ? "" : o.toString().trim();
    }

    private static String withUnit(String value, String unit) {
        if (value.isEmpty() || value.toLowerCase(Locale.ENGLISH).contains(unit.toLowerCase(Locale.ENGLISH))) return value;
        return value + " " + unit;
    }

    private static String formatDate(String iso) {
        try {
            return LocalDate.parse(iso).format(DATE);
        } catch (DateTimeParseException e) {
            return iso;
        }
    }

    private static String formatDateTime(String iso) {
        if (iso.isEmpty()) return "";
        try {
            return LocalDateTime.parse(iso).format(DATE_TIME);
        } catch (DateTimeParseException e) {
            return iso;
        }
    }

    // "09:00 AM - 09:30 AM" -> "09:00–09:30"; anything else (e.g. "Emergency") is kept as-is.
    private static String formatSlot(String slot) {
        String[] parts = slot.split("-");
        if (parts.length != 2) return slot;
        String start = to24h(parts[0].trim());
        String end = to24h(parts[1].trim());
        return (start == null || end == null) ? slot : start + "–" + end;
    }

    private static String to24h(String t) {
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("(\\d{1,2}):(\\d{2})\\s*(AM|PM)", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(t);
        if (!m.matches()) return null;
        int h = Integer.parseInt(m.group(1)) % 12;
        if (m.group(3).equalsIgnoreCase("PM")) h += 12;
        return String.format("%02d:%s", h, m.group(2));
    }

    // Schedule usernames such as "dr.patel" -> "Dr Patel".
    private static String doctorLabel(String name) {
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("^dr\\.?\\s*(.+)$", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(name);
        if (!m.matches()) return name;
        StringBuilder sb = new StringBuilder("Dr");
        for (String part : m.group(1).split("[\\s._]+")) {
            if (!part.isEmpty()) sb.append(' ').append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return sb.toString();
    }
}
