package com.irs.mef.w2sheet;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * One employee's Form W-2 employee copies on one letter sheet printed both sides, as a Pub 1141
 * substitute: Copies B, C, 2 and 2 in quarter pages on the front, the IRS notice and instructions
 * on the back, dashed lines where the sheet is perforated. Flip on the long edge.
 *
 * Rendering fails rather than drop a value or a word of the IRS back text.
 */
public final class W2EmployeeSheet {

    // Quarter pages in reading order: top-left, top-right, bottom-left, bottom-right.
    private static final List<W2Copy> FRONT = List.of(W2Copy.B, W2Copy.C, W2Copy.STATE, W2Copy.STATE);
    private static final float QUARTER_WIDTH = Pen.PAGE_WIDTH / 2f;
    private static final float QUARTER_HEIGHT = Pen.PAGE_HEIGHT / 2f;
    private static final ObjectMapper JSON = new ObjectMapper();

    private W2EmployeeSheet() {}

    public static W2Statement parse(String json) {
        try {
            return JSON.readValue(json, W2Statement.class);
        } catch (JsonProcessingException e) {
            if (e.getCause() instanceof W2SheetException invalid) {
                throw invalid;
            }
            throw new W2SheetException("Not a W-2 statement: " + e.getOriginalMessage(), e);
        }
    }

    public static byte[] render(W2Statement w2) {
        // The sheet has one state row and one local row; a second line would have nowhere to print.
        if (w2.stateLines().size() > 1 || w2.localLines().size() > 1) {
            throw new W2SheetException("The employee sheet prints one state line and one local line, got "
                    + w2.stateLines().size() + " and " + w2.localLines().size());
        }
        EmployeeInstructions instructions = EmployeeInstructions.forTaxYear(w2.taxYear());
        try (PDDocument document = new PDDocument()) {
            document.getDocumentInformation().setTitle("Form W-2 " + w2.taxYear() + " employee copies, "
                    + w2.employee().fullName());
            page(document, pen -> front(pen, w2));
            page(document, pen -> InstructionFlow.draw(pen, backSlots(), instructions));
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            document.save(bytes);
            return bytes.toByteArray();
        } catch (IOException e) {
            throw new W2SheetException("Could not write the W-2 employee sheet: " + e.getMessage(), e);
        }
    }

    private static void front(Pen pen, W2Statement w2) {
        for (int i = 0; i < FRONT.size(); i++) {
            CopyFace.draw(pen, w2, FRONT.get(i), column(i) * QUARTER_WIDTH, row(i) * QUARTER_HEIGHT,
                    QUARTER_WIDTH, QUARTER_HEIGHT);
        }
    }

    // Long-edge duplex mirrors the columns, so the back of Copy B sits top right. The two Copy 2
    // backs are interchangeable, so the bottom row keeps left-to-right reading order instead.
    private static List<InstructionFlow.Slot> backSlots() {
        List<InstructionFlow.Slot> slots = new ArrayList<>();
        for (int i = 0; i < FRONT.size(); i++) {
            int backColumn = row(i) == 0 ? 1 - column(i) : column(i);
            slots.add(new InstructionFlow.Slot(FRONT.get(i), backColumn * QUARTER_WIDTH, row(i) * QUARTER_HEIGHT,
                    QUARTER_WIDTH, QUARTER_HEIGHT));
        }
        return slots;
    }

    private static int column(int quarter) {
        return quarter % 2;
    }

    private static int row(int quarter) {
        return quarter / 2;
    }

    private static void page(PDDocument document, PageDrawing drawing) throws IOException {
        PDPage page = new PDPage(PDRectangle.LETTER);
        document.addPage(page);
        try (PDPageContentStream out = new PDPageContentStream(document, page)) {
            Pen pen = new Pen(out);
            drawing.draw(pen);
            // Pub 1141 2.3.05: the copies must separate easily; dashed lines mark the perforations.
            pen.perforation(QUARTER_WIDTH, 0f, QUARTER_WIDTH, Pen.PAGE_HEIGHT);
            pen.perforation(0f, QUARTER_HEIGHT, Pen.PAGE_WIDTH, QUARTER_HEIGHT);
        }
    }

    @FunctionalInterface
    private interface PageDrawing {
        void draw(Pen pen);
    }
}
