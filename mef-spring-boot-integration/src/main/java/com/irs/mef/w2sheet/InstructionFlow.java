package com.irs.mef.w2sheet;

import com.irs.mef.w2sheet.EmployeeInstructions.Paragraph;

import java.util.List;

/**
 * Flows the IRS back text across the four quarter-page backs. The Notice to Employee starts behind
 * Copy B (it carries the EITC notice, Pub 1141 2.3.20); the Instructions for Employee start behind
 * Copy C and continue behind the Copy 2s (Pub 1141 3.1.05). Text that does not fit fails the render.
 */
final class InstructionFlow {

    /** One quarter-page back, in front order (B, C, 2, 2), placed where the duplex flip puts it. */
    record Slot(W2Copy copy, float left, float top, float width, float height) {}

    private static final float PAD_X = 14f;
    private static final float PAD_TOP = 14f;
    private static final float PAD_BOTTOM = 12f;
    private static final float SIDE_LABEL_SIZE = 5.5f;
    private static final float SIDE_LABEL_HEIGHT = 10f;
    private static final float BODY_SIZE = 6.4f;
    private static final float BODY_LEADING = 7.3f;
    private static final float HEADING_SIZE = 8f;
    private static final float HEADING_LEADING = 10f;
    private static final float PARAGRAPH_GAP = 1.6f;
    private static final int INSTRUCTIONS_FIRST_SLOT = 1;

    private final Pen pen;
    private final List<Slot> slots;
    private int slot = -1;
    private float cursor;
    private int unplacedWords;

    private InstructionFlow(Pen pen, List<Slot> slots) {
        this.pen = pen;
        this.slots = slots;
    }

    static void draw(Pen pen, List<Slot> slots, EmployeeInstructions text) {
        InstructionFlow flow = new InstructionFlow(pen, slots);
        flow.nextSlot();
        flow.place(text.notice());
        while (flow.slot < INSTRUCTIONS_FIRST_SLOT && flow.slot < slots.size()) {
            flow.nextSlot();
        }
        flow.place(text.instructions());
        if (flow.unplacedWords > 0) {
            throw new W2SheetException("The IRS employee instructions do not fit the back of the sheet: "
                    + flow.unplacedWords + " words left over");
        }
    }

    private void place(List<Paragraph> paragraphs) {
        for (Paragraph paragraph : paragraphs) {
            if (slot >= slots.size()) {
                unplacedWords += paragraph.wordCount();
            } else if (paragraph.isHeading()) {
                heading(paragraph.heading());
            } else {
                body(paragraph);
            }
        }
    }

    // A heading keeps at least one body line with it.
    private void heading(String heading) {
        if (!room(HEADING_LEADING + BODY_LEADING) && !nextSlot()) {
            unplacedWords += TextWrap.words(heading, Pen.SANS_BOLD).size();
            return;
        }
        Slot current = slots.get(slot);
        pen.text(Pen.SANS_BOLD, HEADING_SIZE, current.left() + PAD_X, cursor + HEADING_SIZE, heading);
        cursor += HEADING_LEADING;
    }

    private void body(Paragraph paragraph) {
        List<List<TextWrap.Word>> lines = TextWrap.wrap(paragraph.words(), BODY_SIZE, slots.get(0).width() - 2 * PAD_X);
        for (int i = 0; i < lines.size(); i++) {
            if (!room(BODY_LEADING) && !nextSlot()) {
                lines.subList(i, lines.size()).forEach(line -> unplacedWords += line.size());
                return;
            }
            TextWrap.draw(pen, lines.get(i), BODY_SIZE, slots.get(slot).left() + PAD_X, cursor + BODY_SIZE);
            cursor += BODY_LEADING;
        }
        cursor += PARAGRAPH_GAP;
    }

    private boolean room(float height) {
        Slot current = slots.get(slot);
        return cursor + height <= current.top() + current.height() - PAD_BOTTOM;
    }

    private boolean nextSlot() {
        slot++;
        if (slot >= slots.size()) {
            return false;
        }
        Slot current = slots.get(slot);
        float labelTop = current.top() + PAD_TOP;
        pen.text(Pen.SANS_BOLD, SIDE_LABEL_SIZE, current.left() + PAD_X, labelTop + SIDE_LABEL_SIZE, current.copy().backLabel());
        cursor = labelTop + SIDE_LABEL_HEIGHT;
        return true;
    }
}
