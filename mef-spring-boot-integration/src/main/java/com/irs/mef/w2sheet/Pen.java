package com.irs.mef.w2sheet;

import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;

import java.io.IOException;
import java.io.UncheckedIOException;

/**
 * Black-ink drawing on one letter page, measured in points from the top-left corner.
 * The standard 14 fonts are WinAnsi-encoded; a character outside WinAnsi fails the render.
 */
final class Pen {

    static final float PAGE_WIDTH = 612f;
    static final float PAGE_HEIGHT = 792f;
    static final PDFont SANS = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
    static final PDFont SANS_BOLD = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
    static final PDFont MONO_BOLD = new PDType1Font(Standard14Fonts.FontName.COURIER_BOLD);
    private static final float[] PERFORATION_DASH = {3f, 2f};

    private final PDPageContentStream out;

    Pen(PDPageContentStream out) {
        this.out = out;
    }

    static float width(PDFont font, float size, String text) {
        try {
            return font.getStringWidth(text) / 1000f * size;
        } catch (IllegalArgumentException e) {
            throw new W2SheetException("Cannot print '" + text + "': " + e.getMessage(), e);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    void text(PDFont font, float size, float x, float baseline, String text) {
        try {
            out.beginText();
            out.setFont(font, size);
            out.newLineAtOffset(x, PAGE_HEIGHT - baseline);
            out.showText(text);
            out.endText();
        } catch (IllegalArgumentException e) {
            throw new W2SheetException("Cannot print '" + text + "': " + e.getMessage(), e);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    void textRight(PDFont font, float size, float right, float baseline, String text) {
        text(font, size, right - width(font, size, text), baseline, text);
    }

    void textCentered(PDFont font, float size, float center, float baseline, String text) {
        text(font, size, center - width(font, size, text) / 2f, baseline, text);
    }

    void rect(float x, float top, float width, float height, float lineWidth) {
        stroke(lineWidth, () -> out.addRect(x, PAGE_HEIGHT - top - height, width, height));
    }

    void fill(float x, float top, float width, float height) {
        draw(() -> {
            out.addRect(x, PAGE_HEIGHT - top - height, width, height);
            out.fill();
        });
    }

    void perforation(float x1, float top1, float x2, float top2) {
        draw(() -> {
            out.setLineDashPattern(PERFORATION_DASH, 0);
            out.setLineWidth(0.5f);
            out.moveTo(x1, PAGE_HEIGHT - top1);
            out.lineTo(x2, PAGE_HEIGHT - top2);
            out.stroke();
            out.setLineDashPattern(new float[0], 0);
        });
    }

    private void stroke(float lineWidth, Drawing path) {
        draw(() -> {
            out.setLineWidth(lineWidth);
            path.draw();
            out.stroke();
        });
    }

    private void draw(Drawing drawing) {
        try {
            drawing.draw();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @FunctionalInterface
    private interface Drawing {
        void draw() throws IOException;
    }
}
