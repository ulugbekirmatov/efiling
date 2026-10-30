package com.irs.mef.returnpdf;

import java.time.YearMonth;
import java.time.format.DateTimeParseException;

record Quarter(int year, int number) {

    private static final int MONTHS_PER_QUARTER = 3;

    static Quarter read(ReturnXml.Node header) {
        String raw = header.text("QuarterEndingDt")
                .orElseThrow(() -> new ReturnPdfException("ReturnHeader has no QuarterEndingDt"));
        try {
            YearMonth ending = YearMonth.parse(raw);
            if (ending.getMonthValue() % MONTHS_PER_QUARTER != 0) {
                throw new ReturnPdfException("QuarterEndingDt " + raw + " is not a quarter-end month");
            }
            return new Quarter(ending.getYear(), ending.getMonthValue() / MONTHS_PER_QUARTER);
        } catch (DateTimeParseException e) {
            throw new ReturnPdfException("QuarterEndingDt is not yyyy-MM: " + raw, e);
        }
    }

    /** The on-state of a "Report for this Quarter" checkbox, which IRS PDFs number 1 to 4. */
    String checkboxValue() {
        return String.valueOf(number);
    }
}
