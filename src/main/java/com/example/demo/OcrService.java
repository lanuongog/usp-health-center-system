package com.example.demo;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.springframework.stereotype.Service;

import net.sourceforge.tess4j.ITesseract;
import net.sourceforge.tess4j.OSDResult;
import net.sourceforge.tess4j.Tesseract;

// Most students upload a phone photo/scan of their printed payment receipt
// rather than a digitally-generated PDF, so it has no selectable text layer -
// PDFTextStripper alone would extract nothing from it. This renders each page
// to an image and runs it through Tesseract OCR (bundled inside the tess4j
// dependency, including English + orientation-detection trained data, so no
// separate system install is required) as a fallback for that common case.
@Service
public class OcrService {

    private static final int RENDER_DPI = 300;
    private final Path tessDataDir = Path.of(System.getProperty("java.io.tmpdir"), "usp-health-center-tessdata");

    public String extractText(byte[] pdfBytes) {
        try {
            ensureTessData();
            ITesseract tesseract = new Tesseract();
            tesseract.setDatapath(tessDataDir.toAbsolutePath().toString());

            StringBuilder combined = new StringBuilder();
            try (PDDocument document = org.apache.pdfbox.Loader.loadPDF(pdfBytes)) {
                PDFRenderer renderer = new PDFRenderer(document);
                for (int page = 0; page < document.getNumberOfPages(); page++) {
                    BufferedImage image = renderer.renderImageWithDPI(page, RENDER_DPI);
                    BufferedImage upright = correctOrientation(tesseract, image);
                    combined.append(tesseract.doOCR(upright)).append('\n');
                }
            }
            return combined.toString();
        } catch (Exception e) {
            // OCR failed (corrupt image, native lib issue, etc.) - fall back to manual review
            return null;
        }
    }

    // A phone photo is often sideways or upside down. Tesseract's orientation
    // detector (OSD) tells us how many degrees to rotate to make the text
    // upright; without this, OCR on a rotated page returns near-garbage.
    private BufferedImage correctOrientation(ITesseract tesseract, BufferedImage image) {
        try {
            OSDResult osd = tesseract.getOSD(image);
            int degrees = osd.getOrientDeg();
            if (degrees == 0) return image;
            return rotate(image, degrees);
        } catch (Exception e) {
            // OSD needs a page with enough text to judge orientation from - if it
            // can't tell, just OCR the image as-is rather than failing outright.
            return image;
        }
    }

    private BufferedImage rotate(BufferedImage src, int degrees) {
        boolean swapDimensions = degrees == 90 || degrees == 270;
        int width = src.getWidth();
        int height = src.getHeight();
        int newWidth = swapDimensions ? height : width;
        int newHeight = swapDimensions ? width : height;

        BufferedImage rotated = new BufferedImage(newWidth, newHeight, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = rotated.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, newWidth, newHeight);
        AffineTransform transform = new AffineTransform();
        transform.translate(newWidth / 2.0, newHeight / 2.0);
        transform.rotate(-Math.toRadians(degrees));
        transform.translate(-width / 2.0, -height / 2.0);
        g.drawImage(src, transform, null);
        g.dispose();
        return rotated;
    }

    // The tess4j jar bundles the trained-data files as classpath resources;
    // Tesseract's native library needs them as real files on disk, so extract
    // them once to a stable temp location rather than on every OCR call.
    private synchronized void ensureTessData() throws IOException {
        Files.createDirectories(tessDataDir);
        extractIfMissing("/tessdata/eng.traineddata", "eng.traineddata");
        extractIfMissing("/tessdata/osd.traineddata", "osd.traineddata");
    }

    private void extractIfMissing(String resourcePath, String fileName) throws IOException {
        File dest = tessDataDir.resolve(fileName).toFile();
        if (dest.exists() && dest.length() > 0) return;
        try (InputStream in = OcrService.class.getResourceAsStream(resourcePath);
             FileOutputStream out = new FileOutputStream(dest)) {
            if (in == null) throw new IOException("Missing bundled tessdata resource: " + resourcePath);
            in.transferTo(out);
        }
    }
}
