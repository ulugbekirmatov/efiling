package com.irs.mef.returnpdf;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The PDF names a column of the month
 * (Day1-8, Day9-16, Day17-24, Day25-31), not the day, and the atlas lists dollars then cents
 * down that column. Those columns are consecutive days, so the field numbers run in day order
 * from f1_15, one pair per day, then the month total.
 */
final class ScheduleBSheet {

    private static final String PAGE = "topmostSubform[0].Page1[0].";
    private static final String ENTITY = PAGE + "Header-Entity[0].Entity[0].";
    private static final int MONTHS = 3;
    private static final int DAYS = 31;
    private static final int COLUMN_DAYS = 8;
    private static final int FIRST_DAY_FIELD = 15;
    private static final int PAIR = 2;
    private static final int FIELDS_PER_MONTH = DAYS * PAIR + PAIR;

    private ScheduleBSheet() {}

    static void fill(ReturnXml.Node header, ReturnXml.Node form, Fields fields) {
        Filer filer = Filer.read(header);
        fields.perCharacter(boxes(1, 9), filer.ein());
        fields.text(ENTITY + "f1_10[0]", filer.name());
        Quarter quarter = Quarter.read(header);
        fields.perCharacter(boxes(11, 4), Integer.toString(quarter.year()));
        fields.check(PAGE + "c1_1[" + (quarter.number() - 1) + "]", quarter.checkboxValue(), true);
        months(form, fields);
        money(fields, FIRST_DAY_FIELD + MONTHS * FIELDS_PER_MONTH, form.text("TotalQuarterTaxLiabilityAmt"));
    }

    private static void months(ReturnXml.Node form, Fields fields) {
        List<ReturnXml.Node> months = form.all("TaxLiabilityQtrMonthlyDetail");
        if (months.size() > MONTHS) {
            throw new ReturnPdfException("IRS941ScheduleB has " + months.size()
                    + " TaxLiabilityQtrMonthlyDetail rows; f941sb.pdf holds " + MONTHS);
        }
        boolean[] seenMonth = new boolean[MONTHS + 1];
        for (ReturnXml.Node month : months) {
            int monthOfQuarter = inRange(month.text("MonthOfQuarterCd"), 1, MONTHS, "MonthOfQuarterCd");
            if (seenMonth[monthOfQuarter]) {
                throw new ReturnPdfException("MonthOfQuarterCd " + monthOfQuarter + " is repeated");
            }
            seenMonth[monthOfQuarter] = true;
            days(month, monthOfQuarter, fields);
            money(fields, FIRST_DAY_FIELD + (monthOfQuarter - 1) * FIELDS_PER_MONTH + DAYS * PAIR,
                    month.text("TotalTaxLiabilityAmt"));
        }
    }

    private static void days(ReturnXml.Node month, int monthOfQuarter, Fields fields) {
        List<ReturnXml.Node> days = month.all("DailyTaxLiabilityDetail");
        if (days.size() > DAYS) {
            throw new ReturnPdfException("Month " + monthOfQuarter + " has " + days.size()
                    + " DailyTaxLiabilityDetail rows; f941sb.pdf holds " + DAYS);
        }
        boolean[] seenDay = new boolean[DAYS + 1];
        for (ReturnXml.Node detail : days) {
            int day = inRange(detail.text("DayNum"), 1, DAYS, "DayNum");
            if (seenDay[day]) {
                throw new ReturnPdfException("Month " + monthOfQuarter + " repeats day " + day);
            }
            seenDay[day] = true;
            int dollars = FIRST_DAY_FIELD + (monthOfQuarter - 1) * FIELDS_PER_MONTH + (day - 1) * PAIR;
            String group = PAGE + column(day) + "_Month" + monthOfQuarter + "[0].";
            fields.money(group + fieldName(dollars), group + fieldName(dollars + 1), detail.text("TaxLiabilityAmt"));
        }
    }

    private static String column(int day) {
        int start = ((day - 1) / COLUMN_DAYS) * COLUMN_DAYS + 1;
        int end = Math.min(start + COLUMN_DAYS - 1, DAYS);
        return "Day" + start + "-" + end;
    }

    /** The IRS file names month 2's first cents box f1_80_, not f1_80. */
    private static String fieldName(int number) {
        return "f1_" + number + (number == 80 ? "_" : "") + "[0]";
    }

    private static void money(Fields fields, int dollarsNumber, Optional<String> amount) {
        fields.money(PAGE + fieldName(dollarsNumber), PAGE + fieldName(dollarsNumber + 1), amount);
    }

    private static List<String> boxes(int first, int count) {
        List<String> names = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            int number = first + i;
            names.add(ENTITY + "f1_" + (number < 10 ? "0" + number : Integer.toString(number)) + "[0]");
        }
        return names;
    }

    private static int inRange(Optional<String> raw, int min, int max, String name) {
        String text = raw.orElseThrow(() -> new ReturnPdfException(name + " is missing"));
        int value;
        try {
            value = Integer.parseInt(text);
        } catch (NumberFormatException e) {
            throw new ReturnPdfException(name + " is not an integer: " + text, e);
        }
        if (value < min || value > max) {
            throw new ReturnPdfException(name + " " + text + " is outside " + min + ".." + max);
        }
        return value;
    }
}
