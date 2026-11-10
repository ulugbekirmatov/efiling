package com.irs.mef.w2sheet;

import com.irs.mef.w2sheet.W2Statement.LocalLine;
import com.irs.mef.w2sheet.W2Statement.StateLine;
import org.apache.pdfbox.pdmodel.font.PDFont;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The front of one W-2 copy in a 4.25 x 5.5 in quarter page, in the vertical format of Pub 1141
 * Exhibit F: boxes 1 to 6 across the top, the identifiers, the boxes that hold a value, then the
 * state and local boxes, the negligence note on Copy C, the title, and the copy label at the bottom.
 * A value that would not fit its box fails the render rather than being clipped.
 */
final class CopyFace {

    private static final float PAD_X = 14f;
    private static final float PAD_TOP = 16f;
    private static final float PAD_BOTTOM = 12f;
    private static final float GAP = 3f;
    private static final float BOX_HEIGHT = 22f;
    private static final float ADDRESS_BOX_HEIGHT = 40f;
    private static final int ADDRESS_LINES = 3;
    private static final float LABEL_SIZE = 5.5f;
    private static final float NUMBER_SIZE = 6f;
    private static final float VALUE_SIZE = 8.5f;
    private static final float TEXT_SIZE = 8f;
    private static final float TEXT_LEADING = 9f;
    private static final float LIST_SIZE = 7.5f;
    private static final float LIST_LEADING = 9f;
    private static final float CHECK_SIZE = 5f;
    private static final float CHECK_LEADING = 7f;
    private static final float NOTE_SIZE = 5.6f;
    private static final float NOTE_LEADING = 6.6f;
    private static final float THIN_RULE = 0.5f;
    // Pub 1141 2.3.12: boxes 1 and 2 of Copy B are outlined in a bold 2-point rule.
    private static final float BOLD_RULE = 2f;
    private static final float TITLE_HEIGHT = 24f;
    private static final float COPY_LABEL_SIZE = 6.2f;

    private record Extra(float height, BoxDrawing drawing) {}

    @FunctionalInterface
    private interface BoxDrawing {
        void draw(float x, float top, float width, float height);
    }

    private final Pen pen;
    private final W2Statement w2;
    private final W2Copy copy;
    private final float left;
    private final float top;
    private final float width;
    private final float bottom;

    private CopyFace(Pen pen, W2Statement w2, W2Copy copy, float cellLeft, float cellTop, float cellWidth, float cellHeight) {
        this.pen = pen;
        this.w2 = w2;
        this.copy = copy;
        this.left = cellLeft + PAD_X;
        this.top = cellTop + PAD_TOP;
        this.width = cellWidth - 2 * PAD_X;
        this.bottom = cellTop + cellHeight - PAD_BOTTOM;
    }

    static void draw(Pen pen, W2Statement w2, W2Copy copy, float cellLeft, float cellTop, float cellWidth, float cellHeight) {
        new CopyFace(pen, w2, copy, cellLeft, cellTop, cellWidth, cellHeight).draw();
    }

    private void draw() {
        float cursor = coreBoxes(top);
        cursor = identifierBoxes(cursor + GAP);
        float extrasBottom = extraBoxes(cursor + GAP);
        float stateTop = footer();
        if (extrasBottom > stateTop - GAP) {
            throw new W2SheetException("Boxes 7 to 14b of " + copy + " overflow into the state and local boxes by "
                    + (extrasBottom - stateTop + GAP) + "pt");
        }
    }

    private float coreBoxes(float rowTop) {
        String[] labels = {"Wages, tips, other compensation", "Federal income tax withheld",
                "Social security wages", "Social security tax withheld",
                "Medicare wages and tips", "Medicare tax withheld"};
        W2Statement.CoreAmounts core = w2.core();
        BigDecimal[] amounts = {core.wages(), core.federalIncomeTaxWithheld(), core.socialSecurityWages(),
                core.socialSecurityTaxWithheld(), core.medicareWages(), core.medicareTaxWithheld()};
        float half = width / 2f;
        for (int i = 0; i < amounts.length; i++) {
            float x = left + (i % 2) * half;
            float y = rowTop + (i / 2) * BOX_HEIGHT;
            moneyBox(x, y, half, BOX_HEIGHT, String.valueOf(i + 1), labels[i], amounts[i]);
            if (copy.boldCoreRule && i < 2) {
                pen.rect(x + BOLD_RULE / 2f, y + BOLD_RULE / 2f, half - BOLD_RULE, BOX_HEIGHT - BOLD_RULE, BOLD_RULE);
            }
        }
        return rowTop + 3 * BOX_HEIGHT;
    }

    private float identifierBoxes(float rowTop) {
        float half = width / 2f;
        textBox(left, rowTop, half, BOX_HEIGHT, "a", "Employee’s social security number",
                List.of("XXX-XX-" + w2.employee().ssnLast4()));
        textBox(left + half, rowTop, half, BOX_HEIGHT, "b", "Employer identification number (EIN)",
                List.of(w2.employer().ein()));
        float y = rowTop + BOX_HEIGHT;
        textBox(left, y, width, ADDRESS_BOX_HEIGHT, "c", "Employer’s name, address, and ZIP code",
                addressLines("box c", w2.employer().name(), w2.employer().address()));
        y += ADDRESS_BOX_HEIGHT;
        textBox(left, y, width, ADDRESS_BOX_HEIGHT, "e/f", "Employee’s name, address, and ZIP code",
                addressLines("box e/f", w2.employee().fullName(), w2.employee().address()));
        return y + ADDRESS_BOX_HEIGHT;
    }

    private static List<String> addressLines(String box, String name, List<String> address) {
        List<String> lines = new ArrayList<>();
        lines.add(name);
        lines.addAll(address);
        if (lines.size() > ADDRESS_LINES) {
            throw new W2SheetException(box + " holds " + ADDRESS_LINES + " lines, got " + lines.size());
        }
        return lines;
    }

    private float extraBoxes(float rowTop) {
        List<Extra> extras = extras();
        float half = width / 2f;
        float y = rowTop;
        for (int i = 0; i < extras.size(); i += 2) {
            Extra first = extras.get(i);
            Extra second = i + 1 < extras.size() ? extras.get(i + 1) : null;
            float rowHeight = Math.max(first.height(), second == null ? 0f : second.height());
            first.drawing().draw(left, y, half, rowHeight);
            if (second != null) {
                second.drawing().draw(left + half, y, half, rowHeight);
            }
            y += rowHeight;
        }
        return y;
    }

    // Pub 1141 2.3.08 and 2.3.14: boxes 7 to 14b print only when they hold something for this employee.
    private List<Extra> extras() {
        List<Extra> extras = new ArrayList<>();
        W2Statement.OptionalAmounts optional = w2.optional();
        addMoney(extras, "7", "Social security tips", optional.socialSecurityTips());
        addMoney(extras, "8", "Allocated tips", optional.allocatedTips());
        addMoney(extras, "10", "Dependent care benefits", optional.dependentCareBenefits());
        addMoney(extras, "11", "Nonqualified plans", optional.nonqualifiedPlans());
        if (!w2.box12().isEmpty()) {
            List<String[]> rows = w2.box12().stream().map(c -> new String[] {c.code(), money(c.amount())}).toList();
            extras.add(new Extra(listHeight(rows.size()), (x, y, w, h) -> listBox(x, y, w, h, "12", "See instructions for box 12", rows)));
        }
        extras.add(new Extra(BOX_HEIGHT + CHECK_LEADING + 1f, this::box13));
        if (!w2.box14a().isEmpty()) {
            List<String[]> rows = w2.box14a().stream().map(l -> new String[] {l.label(), money(l.amount())}).toList();
            extras.add(new Extra(listHeight(rows.size()), (x, y, w, h) -> listBox(x, y, w, h, "14a", "Other", rows)));
        }
        if (!w2.box14b().isEmpty()) {
            List<String> codes = List.of(String.join(", ", w2.box14b()));
            extras.add(new Extra(BOX_HEIGHT, (x, y, w, h) -> textBox(x, y, w, h, "14b", "Treasury Tipped Occupation Code(s)", codes)));
        }
        return extras;
    }

    private void addMoney(List<Extra> extras, String number, String label, BigDecimal amount) {
        if (amount != null) {
            extras.add(new Extra(BOX_HEIGHT, (x, y, w, h) -> moneyBox(x, y, w, h, number, label, amount)));
        }
    }

    private static float listHeight(int rows) {
        return 9f + rows * LIST_LEADING + 3f;
    }

    private void box13(float x, float y, float w, float h) {
        frame(x, y, w, h, "13", "");
        W2Statement.Box13 box13 = w2.box13();
        String[] labels = {"Statutory employee", "Retirement plan", "Third-party sick pay"};
        boolean[] checked = {box13.statutoryEmployee(), box13.retirementPlan(), box13.thirdPartySickPay()};
        for (int i = 0; i < labels.length; i++) {
            float rowTop = y + 8f + i * CHECK_LEADING;
            pen.rect(x + 4f, rowTop, CHECK_SIZE, CHECK_SIZE, THIN_RULE);
            if (checked[i]) {
                pen.fill(x + 4f, rowTop, CHECK_SIZE, CHECK_SIZE);
            }
            pen.text(Pen.SANS, LABEL_SIZE, x + 4f + CHECK_SIZE + 3f, rowTop + CHECK_SIZE - 0.5f, labels[i]);
        }
    }

    // Pub 1141 2.3.13: state and local boxes 15 to 20 sit at the bottom of the copy.
    private float footer() {
        float labelBaseline = bottom - 1f;
        fitted(Pen.SANS_BOLD, COPY_LABEL_SIZE, width, copy.label, "copy label");
        pen.text(Pen.SANS_BOLD, COPY_LABEL_SIZE, left, labelBaseline, copy.label);
        float titleTop = labelBaseline - COPY_LABEL_SIZE - 2f - TITLE_HEIGHT;
        title(titleTop);
        float above = titleTop;
        if (copy.negligenceNote) {
            above = negligenceNote(titleTop);
        }
        float stateTop = above - GAP - 2 * BOX_HEIGHT;
        stateAndLocal(stateTop);
        return stateTop;
    }

    // Pub 1141 2.3.09 and 2.3.11: form number, title, and year together, and the agency on every copy.
    private void title(float titleTop) {
        pen.text(Pen.SANS, 6f, left, titleTop + 13f, "Form");
        pen.text(Pen.SANS_BOLD, 13f, left + Pen.width(Pen.SANS, 6f, "Form") + 2f, titleTop + 13f, "W-2");
        pen.text(Pen.SANS_BOLD, 8f, left, titleTop + 22f, "Wage and Tax Statement");
        pen.textCentered(Pen.MONO_BOLD, 20f, left + width * 0.58f, titleTop + 19f, String.valueOf(w2.taxYear()));
        pen.textRight(Pen.SANS, LABEL_SIZE, left + width, titleTop + 14f, "Department of the Treasury—");
        pen.textRight(Pen.SANS, LABEL_SIZE, left + width, titleTop + 20f, "Internal Revenue Service");
    }

    // Pub 1141 2.3.18: the negligence-penalty note on the front of Copy C.
    private float negligenceNote(float below) {
        List<List<TextWrap.Word>> lines = TextWrap.wrap(TextWrap.words(W2Copy.NEGLIGENCE_NOTE, Pen.SANS), NOTE_SIZE, width - 6f);
        float height = lines.size() * NOTE_LEADING + 3f;
        float noteTop = below - GAP - height;
        pen.rect(left, noteTop, width, height, THIN_RULE);
        for (int i = 0; i < lines.size(); i++) {
            TextWrap.draw(pen, lines.get(i), NOTE_SIZE, left + 3f, noteTop + (i + 1) * NOTE_LEADING);
        }
        return noteTop;
    }

    private void stateAndLocal(float rowTop) {
        StateLine state = w2.stateLines().isEmpty() ? null : w2.stateLines().get(0);
        LocalLine local = w2.localLines().isEmpty() ? null : w2.localLines().get(0);
        float x = left;
        float[] stateWidths = {0.14f, 0.36f, 0.25f, 0.25f};
        textBox(x, rowTop, width * stateWidths[0], BOX_HEIGHT, "15", "State",
                lines(state == null ? null : state.state()));
        x += width * stateWidths[0];
        textBox(x, rowTop, width * stateWidths[1], BOX_HEIGHT, "", "Employer’s state ID number", lines(state == null ? null : state.stateId()));
        x += width * stateWidths[1];
        moneyBox(x, rowTop, width * stateWidths[2], BOX_HEIGHT, "16", "State wages, tips, etc.", state == null ? null : state.wages());
        x += width * stateWidths[2];
        moneyBox(x, rowTop, width * stateWidths[3], BOX_HEIGHT, "17", "State income tax", state == null ? null : state.tax());

        float y = rowTop + BOX_HEIGHT;
        float[] localWidths = {0.3f, 0.3f, 0.4f};
        x = left;
        moneyBox(x, y, width * localWidths[0], BOX_HEIGHT, "18", "Local wages, tips, etc.", local == null ? null : local.wages());
        x += width * localWidths[0];
        moneyBox(x, y, width * localWidths[1], BOX_HEIGHT, "19", "Local income tax", local == null ? null : local.tax());
        x += width * localWidths[1];
        textBox(x, y, width * localWidths[2], BOX_HEIGHT, "20", "Locality name", lines(local == null ? null : local.locality()));
    }

    private static List<String> lines(String value) {
        return value == null || value.isBlank() ? List.of() : List.of(value);
    }

    private void moneyBox(float x, float y, float w, float h, String number, String label, BigDecimal amount) {
        frame(x, y, w, h, number, label);
        if (amount != null) {
            String value = money(amount);
            fitted(Pen.MONO_BOLD, VALUE_SIZE, w - 6f, value, "box " + number);
            pen.textRight(Pen.MONO_BOLD, VALUE_SIZE, x + w - 3f, y + h - 4f, value);
        }
    }

    private void textBox(float x, float y, float w, float h, String number, String label, List<String> values) {
        frame(x, y, w, h, number, label);
        for (int i = 0; i < values.size(); i++) {
            fitted(Pen.MONO_BOLD, TEXT_SIZE, w - 6f, values.get(i), "box " + (number.isEmpty() ? label : number));
            pen.text(Pen.MONO_BOLD, TEXT_SIZE, x + 3f, y + 15f + i * TEXT_LEADING, values.get(i));
        }
    }

    private void listBox(float x, float y, float w, float h, String number, String label, List<String[]> rows) {
        frame(x, y, w, h, number, label);
        for (int i = 0; i < rows.size(); i++) {
            String[] row = rows.get(i);
            float baseline = y + 9f + (i + 1) * LIST_LEADING - 1f;
            fitted(Pen.MONO_BOLD, LIST_SIZE, w - 6f, row[0] + "  " + row[1], "box " + number);
            pen.text(Pen.MONO_BOLD, LIST_SIZE, x + 4f, baseline, row[0]);
            pen.textRight(Pen.MONO_BOLD, LIST_SIZE, x + w - 3f, baseline, row[1]);
        }
    }

    private void frame(float x, float y, float w, float h, String number, String label) {
        pen.rect(x, y, w, h, THIN_RULE);
        float labelX = x + 2f;
        if (!number.isEmpty()) {
            pen.text(Pen.SANS_BOLD, NUMBER_SIZE, labelX, y + 6.5f, number);
            labelX += Pen.width(Pen.SANS_BOLD, NUMBER_SIZE, number) + 2f;
        }
        if (!label.isEmpty()) {
            fitted(Pen.SANS, LABEL_SIZE, x + w - labelX - 1f, label, "label of box " + number);
            pen.text(Pen.SANS, LABEL_SIZE, labelX, y + 6.5f, label);
        }
    }

    private static void fitted(PDFont font, float size, float room, String text, String where) {
        if (Pen.width(font, size, text) > room) {
            throw new W2SheetException("'" + text + "' does not fit " + where);
        }
    }

    static String money(BigDecimal amount) {
        return String.format(Locale.US, "%,.2f", amount);
    }
}
