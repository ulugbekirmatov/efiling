package com.irs.mef.returnpdf;

import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.BreakIterator;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Locale;
import java.util.Optional;

final class Form941XSheet {

    private static final String PAGE1 = "topmostSubform[0].Page1[0].";
    private static final String ENTITY = PAGE1 + "Entity[0].";
    private static final String SIDE = PAGE1 + "SideBoxes[0].";
    private static final String PAGE2 = "topmostSubform[0].Page2[0].";
    private static final String PAGE3 = "topmostSubform[0].Page3[0].";
    private static final String PAGE5 = "topmostSubform[0].Page5[0].";
    private static final String F2 = PAGE2 + "f2_";
    private static final String F3 = PAGE3 + "f3_";
    private static final String F4 = "topmostSubform[0].Page4[0].f4_";

    private static final String CORRECTED = "TotalCorrectedAmount941XGrp/";
    private static final String ORIGINAL = "OriginallyRptOrPrevCorr941XGrp/";
    private static final String DIFFERENCE = "CorrRptDifference941XGrp/";
    private static final String TAX = "TaxCorrection941XGrp/";

    private static final PDType1Font BOLD = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
    private static final float FONT_SIZE = 8f;
    /**
     * Generated appearances clip 1pt inside the widget and start the text another 2pt in, so a
     * single-line value is clipped once it is wider than the widget minus 3pt.
     */
    private static final float CLIP_INSET = 3f;
    private static final float DOLLARS_WIDTH = 50.40f;
    private static final float COUNT_WIDTH = 72f;

    private static final float NAME_WIDTH = 248.60f;
    private static final float TRADE_WIDTH = 272.85f;
    private static final float STREET_WIDTH = 309.60f;
    private static final float CITY_WIDTH = 186.45f;
    private static final float PROVINCE_WIDTH = 79.20f;
    private static final float FOREIGN_POSTAL_WIDTH = 72f;
    private static final float PREPARER_LINE_WIDTH = 257.45f;
    private static final float PREPARER_PHONE_WIDTH = 122.40f;

    /**
     * Line 43 is one multiline box, 502.25pt by 324pt. PDFBox wraps inside a 4pt inset. The
     * embedded font's em box is 1190 units, so 8pt leading is 9.52pt and 33 lines stay inside the clip.
     */
    private static final float EXPLANATION_WIDTH = 502.25f - 4f;
    private static final int EXPLANATION_LINES = 33;
    private static final String EXPLANATION = PAGE5 + "YouMustGiveUs[0].f5_1[0]";

    private Form941XSheet() {}

    static void fill(ReturnXml.Node header, ReturnXml.Node form, Fields fields) {
        Filer filer = Filer.read(header);
        Quarter quarter = Quarter.read(header);
        entity(filer, fields);
        header(form, quarter, fields);
        runningHeader(filer, quarter, form, fields);
        certifications(form, fields);
        corrections(form, fields);
        explanation(fields, form.text("CorrectionsExplanationTxt"));
        paidPreparer(header, fields);
        header.notPrinted("DiscussWithThirdPartyNoInd", "Form 941-X has no third-party designee");
        header.notPrinted("DiscussWithThirdPartyYesGrp", "Form 941-X has no third-party designee");
        form.notPrinted("DirectDepositGrp", "f941x.pdf has no direct-deposit boxes");
    }

    private static void entity(Filer filer, Fields fields) {
        fields.ein(ENTITY + "f1_1[0]", ENTITY + "f1_2[0]", filer.ein());
        text(fields, ENTITY + "f1_3[0]", filer.name(), NAME_WIDTH);
        text(fields, ENTITY + "f1_4[0]", filer.tradeName(), TRADE_WIDTH);
        text(fields, ENTITY + "f1_5[0]", filer.street(), STREET_WIDTH);
        text(fields, ENTITY + "f1_6[0]", filer.city(), CITY_WIDTH);
        fields.text(ENTITY + "f1_7[0]", filer.state());
        fields.text(ENTITY + "f1_8[0]", filer.postalCode().filter(zip -> filer.country().isEmpty()));
        fields.text(ENTITY + "f1_9[0]", filer.country());
        text(fields, ENTITY + "f1_10[0]", filer.province(), PROVINCE_WIDTH);
        text(fields, ENTITY + "f1_11[0]", filer.postalCode().filter(postal -> filer.country().isPresent()),
                FOREIGN_POSTAL_WIDTH);
    }

    private static void header(ReturnXml.Node form, Quarter quarter, Fields fields) {
        fields.check(SIDE + "c1_1[0]", "1", form.checked("Form941CorrectionInd"));
        fields.check(SIDE + "c1_1[1]", "2", form.checked("Form941SSCorrectionInd"));
        fields.check(SIDE + "c1_2[" + (quarter.number() - 1) + "]", quarter.checkboxValue(), true);
        fields.check(SIDE + "c1_2[0]", "1", form.checked("Qtr1CorrectionInd"));
        fields.check(SIDE + "c1_2[1]", "2", form.checked("Qtr2CorrectionInd"));
        fields.check(SIDE + "c1_2[2]", "3", form.checked("Qtr3CorrectionInd"));
        fields.check(SIDE + "c1_2[3]", "4", form.checked("Qtr4CorrectionInd"));
        year(fields, SIDE + "f1_12[0]", form, quarter);
        discovered(form, fields);
        fields.check(SIDE + "c1_3[0]", "1", form.checked("Section3504AgentInd"));
        fields.check(SIDE + "c1_3[1]", "2", form.checked("CPEOInd"));
        fields.check(SIDE + "c1_3[2]", "3", form.checked("OtherThirdPartyInd"));
    }

    private static void discovered(ReturnXml.Node form, Fields fields) {
        form.text("ErrorsDiscoveredDt").ifPresent(raw -> {
            LocalDate date;
            try {
                date = LocalDate.parse(raw);
            } catch (DateTimeParseException e) {
                throw new ReturnPdfException("ErrorsDiscoveredDt is not yyyy-MM-dd: " + raw, e);
            }
            fields.text(SIDE + "f1_13[0]", "%02d".formatted(date.getMonthValue()));
            fields.text(SIDE + "f1_14[0]", "%02d".formatted(date.getDayOfMonth()));
            fields.text(SIDE + "f1_15[0]", "%04d".formatted(date.getYear()));
        });
    }

    private static void runningHeader(Filer filer, Quarter quarter, ReturnXml.Node form, Fields fields) {
        for (int page = 2; page <= 5; page++) {
            String base = "topmostSubform[0].Page" + page + "[0].";
            text(fields, base + "Name_ReadOrder[0].f1_3[0]", filer.name(), NAME_WIDTH);
            fields.ein(base + "EIN_ReadOrder[0].f1_1[0]", base + "EIN_ReadOrder[0].f1_2[0]", filer.ein());
            fields.text(base + "f2_1[0]", String.valueOf(quarter.number()));
            year(fields, base + "f1_12[0]", form, quarter);
        }
    }

    private static void year(Fields fields, String field, ReturnXml.Node form, Quarter quarter) {
        fields.text(field, form.text("CorrectingReturnCalendarYrCd"));
        fields.text(field, String.valueOf(quarter.year()));
    }

    private static void certifications(ReturnXml.Node form, Fields fields) {
        fields.check(PAGE1 + "c1_4[0]", "1", form.checked("AdjustedEmploymentTaxRetInd"));
        fields.check(PAGE1 + "c1_4[1]", "2", form.checked("ClaimInd"));
        // Part 2 boxes are drawn as a second group; their on-state in the PDF is 2, not 1.
        fields.check(PAGE1 + "c1_5[0]", "2", form.checked("FiledW2W2cCorrWageTaxCertInd"));
        fields.check(PAGE1 + "c1_6[0]", "2", form.checked("RpdOrReimbEmplShrTxsCYPYInd"));
        fields.check(PAGE1 + "c1_7[0]", "2", form.checked("AdjSSTMedcrTxEmplrShrInd"));
        fields.check(PAGE1 + "c1_8[0]", "2", form.checked("AdjNotWthldFedSSMedcrTaxInd"));
        fields.check(PAGE1 + "c1_9[0]", "2", form.checked("RpdReimbSSTMedcrTxPYNotClmInd"));
        fields.check(PAGE1 + "c1_10[0]", "2", form.checked("WrttnCnsntSSTMedcrEmpNotClmInd"));
        fields.check(PAGE1 + "c1_11[0]", "2", form.checked("SSTMedcrTxEmplrShrNotClmInd"));
        fields.check(PAGE1 + "c1_12[0]", "2", form.checked("NotWthldFedSSMedcrTaxClmInd"));
        fields.check(PAGE3 + "c3_1[0]", "1", form.checked("EligEmplRtntnCrRcvryStartUpInd"));
        fields.check(PAGE5 + "c5_1[0]", "1", form.checked("CorrUndrptOverreportedAmtInd"));
        fields.check(PAGE5 + "c5_2[0]", "1", form.checked("CorrReclassifiedWorkersInd"));
    }

    private static void corrections(ReturnXml.Node form, Fields fields) {
        line(form, fields, "WagesAmt", F2, 2, Column4.NONE);
        line(form, fields, "FederalIncomeTaxWithheldAmt", F2, 8, Column4.COPY_COLUMN_3);
        line(form, fields, "SocialSecurityTaxCashWagesAmt", F2, 16, Column4.TAX);
        line(form, fields, "TaxableSocSecTipsAmt", F2, 40, Column4.TAX);
        line(form, fields, "TaxableMedicareWagesTipsAmt", F2, 48, Column4.TAX);
        line(form, fields, "TxblWageTipsSubjAddnlMedcrAmt", F2, 56, Column4.TAX);
        line(form, fields, "TaxOnUnreportedTips3121qAmt", F2, 64, Column4.COPY_COLUMN_3);
        line(form, fields, "TaxAdjustmentsAmt", F2, 72, Column4.COPY_COLUMN_3);
        line(form, fields, "PayrollTaxCreditAmt", F2, 80, Column4.TAX);
        line(form, fields, "NrfdblCrQlfySLFMLWagesAmt", F2, 88, Column4.TAX);
        line(form, fields, "NrfdblCrQlfySLFMLWagesAftrAmt", F2, 104, Column4.TAX);
        line(form, fields, "NrfdblCOBRAPremiumSubsidyAmt", F2, 112, Column4.TAX);
        count(form, fields, CORRECTED + "COBRAPremiumSubsidyRcpntCnt", F2 + "120[0]");
        count(form, fields, ORIGINAL + "COBRAPremiumSubsidyRcpntCnt", F2 + "121[0]");
        count(form, fields, DIFFERENCE + "COBRAPremiumSubsidyRcpntCnt", F2 + "122[0]");
        line(form, fields, "SpclAddnWgsFedIncmTaxAmt", F2, 123, Column4.TAX);
        line(form, fields, "SpclAddnWgsSSTAmt", F2, 131, Column4.TAX);
        line(form, fields, "SpclAddnWgsMedcrTaxAmt", F2, 139, Column4.TAX);
        line(form, fields, "SpclAddnWgsAddnlMedcrTaxAmt", F3, 1, Column4.TAX);
        money(form, fields, TAX + "CombinedTaxAmt", F3 + "9[0]", F3 + "10[0]");
        line(form, fields, "RfdblCrQlfySLFMLWagesAmt", F3, 19, Column4.TAX);
        line(form, fields, "RfdblCrQlfySLFMLWagesAftrAmt", F3, 35, Column4.TAX);
        line(form, fields, "RfdblCOBRAPremiumSubsidyAmt", F3, 43, Column4.TAX);
        money(form, fields, TAX + "TotalAmt", F3 + "51[0]", F3 + "52[0]");
        line(form, fields, "QHPExpnssSSQlfyPdSLWageAmt", F3, 53, Column4.NONE);
        line(form, fields, "QHPExpnssSSQlfyPdFMLWageAmt", F3, 59, Column4.NONE);
        line(form, fields, "QlfySLWgsAftrAmt", F4, 19, Column4.NONE);
        line(form, fields, "QHPExpnssQlfySLWgsAftrAmt", F4, 25, Column4.NONE);
        line(form, fields, "CrtnBrgnAgrmtQlfySLWgsAftrAmt", F4, 31, Column4.NONE);
        line(form, fields, "QlfyFMLWgsAftrAmt", F4, 37, Column4.NONE);
        line(form, fields, "QHPExpnssQlfyFMLWgsAftrAmt", F4, 43, Column4.NONE);
        line(form, fields, "CrtnBrgnAgrmtQlfyFMLWgsAftrAmt", F4, 49, Column4.NONE);
    }

    private enum Column4 {
        NONE,
        TAX,
        /** Lines 7, 14, and 15 print "Copy Column 3 here"; the XSD sends no column 4 for them. */
        COPY_COLUMN_3
    }

    private static void line(ReturnXml.Node form, Fields fields, String element, String prefix, int start,
            Column4 column4) {
        money(form, fields, CORRECTED + element, prefix, start);
        money(form, fields, ORIGINAL + element, prefix, start + 2);
        money(form, fields, DIFFERENCE + element, prefix, start + 4);
        switch (column4) {
            case TAX -> money(form, fields, TAX + element, prefix, start + 6);
            case COPY_COLUMN_3 -> money(form, fields, DIFFERENCE + element, prefix, start + 6);
            case NONE -> { }
        }
    }

    private static void money(ReturnXml.Node form, Fields fields, String path, String prefix, int dollars) {
        money(form, fields, path, prefix + dollars + "[0]", prefix + (dollars + 1) + "[0]");
    }

    private static void money(ReturnXml.Node form, Fields fields, String path, String dollarsField, String centsField) {
        Optional<String> amount = form.text(path);
        amount.map(Fields::dollarsText).ifPresent(text -> requireFits(text, DOLLARS_WIDTH));
        fields.money(dollarsField, centsField, amount);
    }

    private static void count(ReturnXml.Node form, Fields fields, String path, String field) {
        text(fields, field, form.text(path), COUNT_WIDTH);
    }

    private static void explanation(Fields fields, Optional<String> text) {
        text.ifPresent(value -> {
            int lines = explanationLines(value);
            if (lines > EXPLANATION_LINES) {
                throw new ReturnPdfException(
                        "CorrectionsExplanationTxt does not fit line 43 (" + lines + " lines)");
            }
            fields.text(EXPLANATION, value);
        });
    }

    private static void paidPreparer(ReturnXml.Node header, Fields fields) {
        String preparer = "PaidPreparerInformationGrp/";
        fields.check(PAGE5 + "c5_3[0]", "1", header.checked(preparer + "SelfEmployedInd"));
        text(fields, PAGE5 + "f5_5[0]", header.text(preparer + "PreparerPersonNm"), PREPARER_LINE_WIDTH);
        fields.text(PAGE5 + "f5_6[0]", header.text(preparer + "PTIN").or(() -> header.text(preparer + "PreparerSSN")));
        text(fields, PAGE5 + "f5_7[0]", header.text(preparer + "PreparerFirmName/BusinessNameLine1Txt"),
                PREPARER_LINE_WIDTH);
        fields.text(PAGE5 + "f5_8[0]", header.text(preparer + "PreparerFirmEIN")
                .map(ein -> ein.substring(0, 2) + "-" + ein.substring(2)));
        text(fields, PAGE5 + "f5_9[0]", header.text(preparer + "PreparerUSAddress/AddressLine1Txt"),
                PREPARER_LINE_WIDTH);
        text(fields, PAGE5 + "f5_10[0]", header.text(preparer + "PhoneNum")
                .or(() -> header.text(preparer + "ForeignPhoneNum")), PREPARER_PHONE_WIDTH);
        fields.text(PAGE5 + "f5_11[0]", header.text(preparer + "PreparerUSAddress/CityNm"));
        fields.text(PAGE5 + "f5_12[0]", header.text(preparer + "PreparerUSAddress/StateAbbreviationCd"));
        fields.text(PAGE5 + "f5_13[0]", header.text(preparer + "PreparerUSAddress/ZIPCd"));
        header.notPrinted(preparer + "SignatureDt", "f941x.pdf has no fillable preparer date; it is signed by hand");
    }

    private static void text(Fields fields, String field, Optional<String> value, float widgetWidth) {
        value.ifPresent(present -> text(fields, field, present, widgetWidth));
    }

    private static void text(Fields fields, String field, String value, float widgetWidth) {
        requireFits(value, widgetWidth);
        fields.text(field, value);
    }

    private static void requireFits(String value, float widgetWidth) {
        if (widthOf(value) > widgetWidth - CLIP_INSET) {
            throw new ReturnPdfException("Value does not fit its 941-X field (" + value.length() + " characters)");
        }
    }

    private static int explanationLines(String value) {
        String[] paragraphs = value.replace('\t', ' ').split("\\R");
        int lines = 0;
        for (String paragraph : paragraphs) {
            lines += paragraph.isEmpty() ? 1 : wrapLines(paragraph);
        }
        return lines;
    }

    private static int wrapLines(String paragraph) {
        BreakIterator breaks = BreakIterator.getLineInstance(Locale.US);
        breaks.setText(paragraph);
        int lines = 1;
        float used = 0f;
        int start = breaks.first();
        for (int end = breaks.next(); end != BreakIterator.DONE; start = end, end = breaks.next()) {
            String chunk = paragraph.substring(start, end);
            while (!chunk.isEmpty()) {
                float chunkWidth = widthOf(chunk);
                if (chunkWidth <= EXPLANATION_WIDTH - used) {
                    used += chunkWidth;
                    break;
                }
                if (used > 0f) {
                    lines++;
                    used = 0f;
                    continue;
                }
                int take = charsThatFit(chunk);
                chunk = chunk.substring(take);
                lines++;
                used = 0f;
            }
        }
        return lines;
    }

    private static int charsThatFit(String chunk) {
        int take = 0;
        float used = 0f;
        for (int i = 0; i < chunk.length(); ) {
            int count = Character.charCount(chunk.codePointAt(i));
            float width = widthOf(chunk.substring(i, i + count));
            if (used + width > EXPLANATION_WIDTH) {
                break;
            }
            used += width;
            take += count;
            i += count;
        }
        if (take == 0) {
            throw new ReturnPdfException("CorrectionsExplanationTxt has a character wider than line 43");
        }
        return take;
    }

    private static float widthOf(String value) {
        try {
            return BOLD.getStringWidth(value) / 1000f * FONT_SIZE;
        } catch (IOException e) {
            throw new ReturnPdfException("Could not measure text for a 941-X field", e);
        }
    }
}
