package com.irs.mef.returnpdf;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * IRS8974 onto f8974.pdf (Rev. December 2024). Line numbers come from the LineNumber annotations
 * in IRS8974.xsd.
 */
final class Form8974Sheet {

    private static final String PAGE1 = "topmostSubform[0].Page1[0].";
    private static final String ENTITY = PAGE1 + "EntityArea[0].";
    private static final int PART1_ROWS = 5;
    private static final int FIELDS_PER_ROW = 14;
    private static final int FIRST_ROW_FIELD = 5;

    private Form8974Sheet() {}

    static void fill(ReturnXml.Node header, ReturnXml.Node form, Fields fields) {
        // The header filer is the employment-tax return. This form's employer block is its own EIN.
        Filer.read(header);
        Quarter quarter = Quarter.read(header);
        employer(form, fields);
        String returnType = header.text("ReturnTypeCd")
                .orElseThrow(() -> new ReturnPdfException("ReturnHeader has no ReturnTypeCd"));
        int attached = attachedBox(returnType);
        fields.check(ENTITY + "c1_1[" + attached + "]", Integer.toString(attached + 1), true);
        fields.text(ENTITY + "f1_4[0]", Integer.toString(quarter.year()));
        if (attached == 0) {
            fields.check(PAGE1 + "c1_2[" + (quarter.number() - 1) + "]", quarter.checkboxValue(), true);
        }
        header.notPrinted("DiscussWithThirdPartyYesGrp", "f8974.pdf has no third-party designee");
        header.notPrinted("DiscussWithThirdPartyNoInd", "f8974.pdf has no third-party designee");
        header.notPrinted("PaidPreparerInformationGrp", "f8974.pdf has no paid preparer block");
        header.notPrinted("TaxYr", "the calendar year box is the year of QuarterEndingDt");
        part1(form, fields);
        part2(form, fields);
    }

    private static void employer(ReturnXml.Node form, Fields fields) {
        form.notPrinted("@documentName", "IRS internal attribute; f8974.pdf has no box for it");
        fields.ein(ENTITY + "f1_1[0]", ENTITY + "f1_2[0]",
                form.text("EIN").orElseThrow(() -> new ReturnPdfException("IRS8974 has no EIN")));
        fields.text(ENTITY + "f1_3[0]", businessName(form));
    }

    private static Optional<String> businessName(ReturnXml.Node form) {
        return form.child("BusinessName").map(name -> Stream.of(
                        name.text("BusinessNameLine1Txt"),
                        name.text("BusinessNameLine2Txt"))
                .flatMap(Optional::stream)
                .collect(Collectors.joining(" ")))
                .filter(joined -> !joined.isEmpty());
    }

    /** The 941 checkbox is the whole 941 series, so 941-X shares box 0. */
    private static int attachedBox(String returnType) {
        if (returnType.startsWith("941")) {
            return 0;
        }
        if (returnType.startsWith("943")) {
            return 1;
        }
        if (returnType.startsWith("944")) {
            return 2;
        }
        throw new ReturnPdfException("Form 8974 attaches to Form 941, 943, or 944, not " + returnType);
    }

    private static void part1(ReturnXml.Node form, Fields fields) {
        List<ReturnXml.Node> rows = form.all("EmployerPayrollTaxElectionGrp");
        if (rows.size() > PART1_ROWS) {
            throw new ReturnPdfException("IRS8974 has " + rows.size()
                    + " EmployerPayrollTaxElectionGrp rows and f8974.pdf holds " + PART1_ROWS);
        }
        for (int i = 0; i < rows.size(); i++) {
            election(rows.get(i), fields, i);
        }
    }

    private static void election(ReturnXml.Node row, Fields fields, int index) {
        String at = PAGE1 + "Table_Part1[0].Row" + (index + 1) + "[0].";
        date(row, "TaxYearEndDt", fields, at + "ColA[0].f1_", field(index, 0));
        fields.text(at + "f1_" + field(index, 3) + "[0]", row.text("ReturnTypeCd"));
        date(row, "ReturnFiledDt", fields, at + "ColC[0].f1_", field(index, 4));
        fields.text(at + "f1_" + field(index, 7) + "[0]", row.text("GroupMemberEIN").map(Form8974Sheet::hyphenatedEin));
        columnAmount(row, "PayrollTaxCreditAllocatedAmt", fields, at + "ColE[0].f1_", field(index, 8));
        columnAmount(row, "PriorPeriodPayrollTaxCreditAmt", fields, at + "ColF[0].f1_", field(index, 10));
        columnAmount(row, "RemainingCreditAmt", fields, at + "ColG[0].f1_", field(index, 12));
    }

    private static void part2(ReturnXml.Node form, Fields fields) {
        // Line 7 is "enter the amount from line 6(g)". The XSD stores that amount once.
        Optional<String> line6 = form.text("MaxAllwblPayrollTaxCreditAmt");
        amount(fields, line6, 75);
        amount(fields, line6, 77);
        amount(form, fields, "SocialSecurityTaxAmt", 79);
        amount(form, fields, "TaxOnSocialSecurityTipsAmt", 81);
        amount(form, fields, "TotalSocialSecurityTaxTipAmt", 83);
        fields.check(PAGE1 + "Line11_ReadOrder[0].c1_3[0]", "1", form.checked("ThirdPartySickPayInd"));
        fields.check(PAGE1 + "Line11_ReadOrder[0].c1_4[0]", "1", form.checked("Section3121qInd"));
        amount(form, fields, "AdjSocialSecurityTaxTipAmt", 85);
        amount(form, fields, "CrAgnstEmplrShrSSTAmt", 87);
        amount(form, fields, "CalcCrRdcngAgnstEmplrShrSSTAmt", 89);
        amount(form, fields, "TaxOnMedicareWagesTipsAmt", 91);
        amount(form, fields, "CalcEmplrShareMedcrTaxAmt", 93);
        amount(form, fields, "CreditForEmployerSSMedcrTxAmt", 95);
        amount(form, fields, "PayrollTaxCreditAmt", 97);
    }

    private static int field(int row, int offset) {
        return FIRST_ROW_FIELD + row * FIELDS_PER_ROW + offset;
    }

    private static void date(ReturnXml.Node node, String path, Fields fields, String prefix, int first) {
        node.text(path).map(Form8974Sheet::isoDate).ifPresent(parsed -> {
            fields.text(prefix + first + "[0]", twoDigits(parsed.getMonthValue()));
            fields.text(prefix + (first + 1) + "[0]", twoDigits(parsed.getDayOfMonth()));
            fields.text(prefix + (first + 2) + "[0]", Integer.toString(parsed.getYear()));
        });
    }

    private static LocalDate isoDate(String iso) {
        try {
            return LocalDate.parse(iso);
        } catch (DateTimeParseException e) {
            throw new ReturnPdfException("Not a date: " + iso, e);
        }
    }

    private static String twoDigits(int value) {
        return value < 10 ? "0" + value : Integer.toString(value);
    }

    /** Column (d) is one field of length 10, an EIN with its hyphen. */
    private static String hyphenatedEin(String ein) {
        return ein.substring(0, 2) + "-" + ein.substring(2);
    }

    private static void columnAmount(ReturnXml.Node node, String path, Fields fields, String prefix, int dollars) {
        fields.money(prefix + dollars + "[0]", prefix + (dollars + 1) + "[0]", node.text(path));
    }

    private static void amount(ReturnXml.Node form, Fields fields, String path, int dollars) {
        amount(fields, form.text(path), dollars);
    }

    private static void amount(Fields fields, Optional<String> value, int dollars) {
        fields.money(PAGE1 + "f1_" + dollars + "[0]", PAGE1 + "f1_" + (dollars + 1) + "[0]", value);
    }
}
