package com.irs.mef.returnpdf;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * IRS941ScheduleR onto f941sr.pdf (Rev. March 2024). Clients fill page 1 lines 1-5, then
 * continuation lines 1-8, in document order. The schema has no element for line 7 or the
 * continuation subtotal (it says the repeating group replaces them), so those boxes stay blank.
 * SubtotalForClientGrp is line 6, AggregateEmployeeInfoGrp is line 8, and
 * TotalAggregateAndClientInfoGrp is line 9.
 */
final class ScheduleRSheet {

    private static final String ROOT = "topmostSubform[0].";
    private static final String PAGE1 = ROOT + "Page1[0].";
    private static final String PAGE2 = ROOT + "Page2[0].";
    private static final int DOLLARS_MAX = 10;
    private static final int CENTS_MAX = 3;
    private static final int TEXT_MAX = 10;
    private static final int FIRST_PAGE_CLIENTS = 5;

    /** Page 1 lines 1–5, then the continuation sheet's lines 1–8. */
    private static final List<ClientRow> CLIENT_ROWS = List.of(
            client(1, "Page1[0]", "Row1[0]",
                    new int[] {5, 6, 7, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20},
                    new int[] {136, 137, 138, 139, 140, 141, 142, 143, 144, 145, 146, 147, 148, 150, 151},
                    new int[] {280, 281, 282, 283, 284, 286, 287, 288, 289, 290, 291, 292, 293, 294, 295}),
            client(1, "Page1[0]", "Row2[0]",
                    new int[] {21, 22, 23, 25, 26, 27, 28, 29, 30, 31, 32, 33, 34, 35, 36},
                    new int[] {152, 153, 154, 155, 156, 157, 158, 159, 160, 161, 162, 163, 164, 166, 167},
                    new int[] {296, 297, 298, 299, 300, 301, 302, 303, 304, 305, 306, 307, 308, 309, 310}),
            client(1, "Page1[0]", "Row3[0]",
                    new int[] {37, 38, 39, 41, 42, 43, 44, 45, 46, 47, 48, 49, 50, 51, 52},
                    new int[] {168, 169, 170, 171, 172, 173, 174, 175, 176, 177, 178, 179, 180, 182, 183},
                    new int[] {311, 312, 313, 314, 315, 316, 317, 318, 319, 320, 321, 322, 323, 324, 325}),
            client(1, "Page1[0]", "Row4[0]",
                    new int[] {53, 54, 55, 57, 58, 59, 60, 61, 62, 63, 64, 65, 66, 67, 68},
                    new int[] {184, 185, 186, 187, 188, 189, 190, 191, 192, 193, 194, 195, 196, 198, 199},
                    new int[] {326, 327, 328, 329, 330, 331, 332, 333, 334, 335, 336, 337, 338, 339, 340}),
            client(1, "Page1[0]", "Row5[0]",
                    new int[] {69, 70, 71, 72, 73, 74, 75, 76, 77, 78, 79, 80, 81, 82, 83},
                    new int[] {200, 201, 202, 203, 204, 205, 206, 207, 208, 209, 210, 211, 212, 214, 215},
                    new int[] {341, 342, 343, 344, 345, 346, 347, 348, 349, 350, 351, 352, 353, 354, 355}),
            client(2, "Page2[0]", "Row1[0]",
                    new int[] {7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21},
                    new int[] {140, 141, 142, 143, 144, 145, 146, 147, 148, 149, 150, 151, 152, 153, 154},
                    new int[] {275, 276, 277, 278, 279, 280, 281, 282, 283, 284, 285, 286, 287, 288, 289}),
            client(2, "Page2[0]", "Row2[0]",
                    new int[] {22, 23, 24, 25, 26, 27, 28, 29, 30, 31, 32, 33, 34, 35, 36},
                    new int[] {155, 156, 157, 158, 159, 160, 161, 162, 163, 164, 165, 166, 167, 168, 169},
                    new int[] {290, 291, 292, 293, 294, 295, 296, 297, 298, 299, 300, 301, 302, 303, 304}),
            client(2, "Page2[0]", "Row3[0]",
                    new int[] {37, 38, 39, 40, 41, 42, 43, 44, 45, 46, 47, 48, 49, 50, 51},
                    new int[] {170, 171, 172, 173, 174, 175, 176, 177, 178, 179, 180, 181, 182, 183, 184},
                    new int[] {305, 306, 307, 308, 309, 310, 311, 312, 313, 314, 315, 316, 317, 318, 319}),
            client(2, "Page2[0]", "Row4[0]",
                    new int[] {52, 53, 54, 55, 56, 57, 58, 59, 60, 61, 62, 63, 64, 65, 66},
                    new int[] {185, 186, 187, 188, 189, 190, 191, 192, 193, 194, 195, 196, 197, 198, 199},
                    new int[] {320, 321, 322, 323, 324, 325, 326, 327, 328, 329, 330, 331, 332, 333, 334}),
            client(2, "Page2[0]", "Row5[0]",
                    new int[] {67, 68, 69, 70, 71, 72, 73, 74, 75, 76, 77, 78, 79, 80, 81},
                    new int[] {200, 201, 202, 203, 204, 205, 206, 207, 208, 209, 210, 211, 212, 213, 214},
                    new int[] {335, 336, 337, 338, 339, 340, 341, 342, 343, 344, 345, 346, 347, 348, 349}),
            client(2, "Page2[0]", "Row6[0]",
                    new int[] {82, 83, 84, 85, 86, 87, 88, 89, 90, 91, 92, 93, 94, 95, 96},
                    new int[] {215, 216, 217, 218, 219, 220, 221, 222, 223, 224, 225, 226, 227, 228, 229},
                    new int[] {350, 351, 352, 353, 354, 355, 356, 357, 358, 359, 360, 361, 362, 363, 364}),
            client(2, "Page2[0]", "Row7[0]",
                    new int[] {97, 98, 99, 100, 101, 102, 103, 104, 105, 106, 107, 108, 109, 110, 111},
                    new int[] {230, 231, 232, 233, 234, 235, 236, 237, 238, 239, 240, 241, 242, 243, 244},
                    new int[] {365, 366, 367, 368, 369, 370, 371, 372, 373, 374, 375, 376, 377, 378, 379}),
            client(2, "Page2[0]", "Row8[0]",
                    new int[] {112, 113, 114, 115, 116, 117, 118, 119, 120, 121, 122, 123, 124, 125, 126},
                    new int[] {245, 246, 247, 248, 249, 250, 251, 252, 253, 254, 255, 256, 257, 258, 259},
                    new int[] {380, 381, 382, 383, 384, 385, 386, 387, 388, 389, 390, 391, 392, 393, 394}));

    private static final Allocation LINE_6 = summary("Page1[0].Line6[0]", "Row6[0]",
            new int[] {84, 85, 86, 87, 88, 89, 90, 91, 92, 93, 94, 95, 96},
            new int[] {216, 217, 218, 219, 220, 221, 222, 223, 224, 225, 226, 227, 228, 230, 231},
            new int[] {356, 357, 358, 359, 360, 361, 362, 363, 364, 365, 366, 367, 368, 369, 370});
    private static final Allocation LINE_8 = summary("Page1[0].Line8[0]", "Row8[0]",
            new int[] {110, 111, 112, 113, 114, 115, 116, 117, 118, 119, 120, 121, 122},
            new int[] {248, 249, 250, 251, 252, 253, 254, 255, 256, 257, 258, 259, 260, 262, 263},
            new int[] {386, 387, 388, 389, 390, 391, 392, 393, 394, 395, 396, 397, 398, 399, 400});
    private static final Allocation LINE_9 = summary("Page1[0].Line9[0]", "Row9[0]",
            new int[] {123, 124, 125, 126, 127, 128, 129, 130, 131, 132, 133, 134, 135},
            new int[] {264, 265, 266, 267, 268, 269, 270, 271, 272, 273, 274, 275, 276, 278, 279},
            new int[] {401, 402, 403, 404, 405, 406, 407, 408, 409, 410, 411, 412, 413, 414, 415});

    private ScheduleRSheet() {}

    static void fill(ReturnXml.Node header, ReturnXml.Node form, Fields fields) {
        Filer filer = Filer.read(header);
        Quarter quarter = Quarter.read(header);
        String attachedTo = header.text("ReturnTypeCd")
                .orElseThrow(() -> new ReturnPdfException("ReturnHeader has no ReturnTypeCd"));
        List<ReturnXml.Node> clients = form.all("ClientInformationGrp");
        if (clients.size() > CLIENT_ROWS.size()) {
            throw new ReturnPdfException("IRS941ScheduleR lists " + clients.size()
                    + " clients and f941sr.pdf has " + CLIENT_ROWS.size() + " client rows");
        }
        headers(PAGE1, "1", filer, quarter, attachedTo, form, fields);
        if (clients.size() > FIRST_PAGE_CLIENTS) {
            headers(PAGE2, "2", filer, quarter, attachedTo, form, fields);
        }
        for (int i = 0; i < clients.size(); i++) {
            client(clients.get(i), CLIENT_ROWS.get(i), fields);
        }
        group(form, "SubtotalForClientGrp", LINE_6, fields);
        group(form, "AggregateEmployeeInfoGrp", LINE_8, fields);
        group(form, "TotalAggregateAndClientInfoGrp", LINE_9, fields);
    }

    /** Every page repeats the filer block. {@code pageNumber} is the digit IRS uses in that page's field names. */
    private static void headers(String page, String pageNumber, Filer filer, Quarter quarter, String attachedTo,
            ReturnXml.Node form, Fields fields) {
        String box = "c" + pageNumber + "_";
        String field = "f" + pageNumber + "_";
        int fieldBase = "1".equals(pageNumber) ? 1 : 3;
        fields.ein(page + "EINAndName[0]." + field + fieldBase + "[0]",
                page + "EINAndName[0]." + field + (fieldBase + 1) + "[0]", filer.ein());
        fields.text(page + "EINAndName[0]." + field + (fieldBase + 2) + "[0]", filer.name());
        fields.check(page + "EINAndName[0]." + box + "1[0]", "1", form.checked("Section3504AgentInd"));
        fields.check(page + "EINAndName[0]." + box + "1[1]", "2", form.checked("CPEOInd"));
        fields.check(page + "EINAndName[0]." + box + "1[2]", "3", form.checked("OtherThirdPartyInd"));
        fields.text(page + "ReportForBox[0]." + field + (fieldBase + 3) + "[0]", Integer.toString(quarter.year()));
        fields.check(page + "ReportForBox[0]." + box + "2[" + (quarter.number() - 1) + "]",
                quarter.checkboxValue(), true);
        boolean correction = "941X".equals(attachedTo);
        fields.check(page + "ThisScheduleBox[0]." + box + "3[" + (correction ? 1 : 0) + "]",
                correction ? "2" : "1", true);
    }

    private static void client(ReturnXml.Node client, ClientRow row, Fields fields) {
        limited(fields, row.ein(), client.text("EIN"));
        fields.text(row.wageType(), client.text("WagesTypeCd"));
        client.child("ClientAllocationInformationGrp")
                .ifPresent(group -> allocation(group, row.amounts(), fields));
    }

    private static void group(ReturnXml.Node form, String name, Allocation line, Fields fields) {
        form.child(name).ifPresent(group -> allocation(group, line, fields));
    }

    private static void allocation(ReturnXml.Node group, Allocation line, Fields fields) {
        limited(fields, line.employees(), group.text("EmployeeCnt"));
        money(fields, line.wages(), group.text("WagesAmt"));
        money(fields, line.withheld(), group.text("FederalIncomeTaxWithheldAmt"));
        money(fields, line.socialSecurity(), group.text("TotalSocialSecurityTaxTipAmt"));
        money(fields, line.medicare(), group.text("TaxOnMedicareWagesTipsAmt"));
        money(fields, line.ssMedicare(), group.text("TotalSSMdcrTaxAmt"));
        money(fields, line.unreportedTips(), group.text("TaxOnUnreportedTips3121qAmt"));
        money(fields, line.researchCredit(), group.text("PayrollTaxCreditAmt"));
        money(fields, line.nonrefundableSick(), group.text("TotalCrQlfySLFMLWagesAmt"));
        money(fields, line.sickAfter(), group.text("TotalCrQlfySLFMLWagesAftrAmt"));
        money(fields, line.cobra(), group.text("TotalCOBRAPremiumSubsidyAmt"));
        limited(fields, line.cobraRecipients(), group.text("COBRAPremiumSubsidyRcpntCnt"));
        money(fields, line.totalTax(), group.text("TotalTaxAmt"));
        money(fields, line.deposits(), group.text("TotalTaxDepositAmt"));
        money(fields, line.qhpSick(), group.text("TotQHPExpnssSSQlfyPdSLFMLWgAmt"));
        money(fields, line.bargainSick(), group.text("TotBrgnAgrmtQlfySLWgsAftrAmt"));
        money(fields, line.qhpAfter(), group.text("TotQHPExpnssQlfyLvWgsAftrAmt"));
        money(fields, line.bargainFamily(), group.text("TotBrgnAgrmtQlfyFMLWgsAftrAmt"));
    }

    private static void limited(Fields fields, String field, Optional<String> value) {
        value.ifPresent(present -> {
            if (present.length() > TEXT_MAX) {
                throw new ReturnPdfException(present.length() + " characters do not fit " + field);
            }
            fields.text(field, present);
        });
    }

    private static void money(Fields fields, Pair pair, Optional<String> amount) {
        amount.ifPresent(raw -> {
            String[] split = dollarsAndCents(raw);
            if (split[0].length() > DOLLARS_MAX || split[1].length() > CENTS_MAX) {
                throw new ReturnPdfException(raw + " does not fit " + pair.dollars());
            }
        });
        fields.money(pair.dollars(), pair.cents(), amount);
    }

    /** Same split {@link Fields#money} writes, so a too-long amount fails before the field clips it. */
    private static String[] dollarsAndCents(String raw) {
        try {
            BigDecimal value = new BigDecimal(raw).setScale(2, RoundingMode.UNNECESSARY);
            BigDecimal dollars = value.abs().setScale(0, RoundingMode.DOWN);
            String cents = value.abs().subtract(dollars).movePointRight(2)
                    .setScale(0, RoundingMode.UNNECESSARY).toPlainString();
            return new String[] {
                    (value.signum() < 0 ? "-" : "") + thousands().format(dollars),
                    cents.length() == 1 ? "0" + cents : cents
            };
        } catch (NumberFormatException | ArithmeticException e) {
            throw new ReturnPdfException("Not a two-decimal amount: " + raw, e);
        }
    }

    private static DecimalFormat thousands() {
        return new DecimalFormat("#,##0", DecimalFormatSymbols.getInstance(Locale.US));
    }

    private static ClientRow client(int formNo, String page, String row, int[] columnsAtoI, int[] columnsJtoQ,
            int[] columnsRtoY) {
        String ai = page + ".Table_ColA-I[0]." + row;
        return new ClientRow(
                field(formNo, ai, columnsAtoI[0]),
                field(formNo, ai, columnsAtoI[1]),
                allocation(formNo, ai, columnsAtoI, 2, page, row, columnsJtoQ, columnsRtoY));
    }

    private static Allocation summary(String employeesParent, String row, int[] columnsAtoI, int[] columnsJtoQ,
            int[] columnsRtoY) {
        return allocation(1, employeesParent, columnsAtoI, 0, "Page1[0]", row, columnsJtoQ, columnsRtoY);
    }

    /**
     * {@code origin} is column (c) inside {@code columnsAtoI}. The skipped slots are the reserved
     * paper columns the schema does not send: (f) on the first band, (m) on the second, (s) (t)
     * and (v) on the third.
     */
    private static Allocation allocation(int formNo, String aiParent, int[] ai, int origin, String page, String row,
            int[] jq, int[] ry) {
        String jqParent = page + ".Table_ColJ-Q[0]." + row;
        String ryParent = page + ".Table_ColR-Y[0]." + row;
        return new Allocation(
                field(formNo, aiParent, ai[origin]),
                pair(formNo, aiParent, ai[origin + 1], ai[origin + 2]),
                pair(formNo, aiParent, ai[origin + 3], ai[origin + 4]),
                pair(formNo, aiParent, ai[origin + 7], ai[origin + 8]),
                pair(formNo, aiParent, ai[origin + 9], ai[origin + 10]),
                pair(formNo, aiParent, ai[origin + 11], ai[origin + 12]),
                pair(formNo, jqParent, jq[0], jq[1]),
                pair(formNo, jqParent, jq[2], jq[3]),
                pair(formNo, jqParent, jq[4], jq[5]),
                pair(formNo, jqParent, jq[8], jq[9]),
                pair(formNo, jqParent, jq[10], jq[11]),
                field(formNo, jqParent, jq[12]),
                pair(formNo, jqParent, jq[13], jq[14]),
                pair(formNo, ryParent, ry[0], ry[1]),
                pair(formNo, ryParent, ry[5], ry[6]),
                pair(formNo, ryParent, ry[9], ry[10]),
                pair(formNo, ryParent, ry[11], ry[12]),
                pair(formNo, ryParent, ry[13], ry[14]));
    }

    private static Pair pair(int formNo, String parent, int dollars, int cents) {
        return new Pair(field(formNo, parent, dollars), field(formNo, parent, cents));
    }

    private static String field(int formNo, String parent, int number) {
        return ROOT + parent + ".f" + formNo + "_" + number + "[0]";
    }

    private record Pair(String dollars, String cents) {}

    private record Allocation(
            String employees,
            Pair wages,
            Pair withheld,
            Pair socialSecurity,
            Pair medicare,
            Pair ssMedicare,
            Pair unreportedTips,
            Pair researchCredit,
            Pair nonrefundableSick,
            Pair sickAfter,
            Pair cobra,
            String cobraRecipients,
            Pair totalTax,
            Pair deposits,
            Pair qhpSick,
            Pair bargainSick,
            Pair qhpAfter,
            Pair bargainFamily) {}

    private record ClientRow(String ein, String wageType, Allocation amounts) {}
}
