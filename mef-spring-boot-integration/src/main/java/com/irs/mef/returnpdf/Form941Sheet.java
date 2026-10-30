package com.irs.mef.returnpdf;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * IRS941 onto f941.pdf (Rev. March 2026). Field names come from the IRS PDF; line numbers come
 * from the LineNumber annotations in IRS941.xsd. Part 5 stays blank: a Reporting Agent signs with
 * the RA PIN, and the PIN never goes on a copy.
 */
final class Form941Sheet {

    private static final String PAGE1 = "topmostSubform[0].Page1[0].";
    private static final String ENTITY = PAGE1 + "Header[0].EntityArea[0].";
    private static final String PAGE2 = "topmostSubform[0].Page2[0].";
    private static final DateTimeFormatter COMB_DATE = DateTimeFormatter.ofPattern("MMddyyyy");

    private Form941Sheet() {}

    static void fill(ReturnXml.Node header, ReturnXml.Node form, Fields fields) {
        Filer filer = Filer.read(header);
        entity(filer, fields);
        Quarter quarter = Quarter.read(header);
        fields.check(PAGE1 + "Header[0].ReportForQuarter[0].c1_1[" + (quarter.number() - 1) + "]",
                quarter.checkboxValue(), true);
        fields.check(PAGE1 + "Header[0].AggregateReturn[0].c1_2[0]", "1", form.checked("Section3504AgentInd"));
        fields.check(PAGE1 + "Header[0].AggregateReturn[0].c1_2[1]", "2", form.checked("CPEOInd"));
        fields.check(PAGE1 + "Header[0].AggregateReturn[0].c1_2[2]", "3", form.checked("OtherThirdPartyInd"));
        form.notPrinted("SpanishLanguageInd", "selects Form 941 (sp); the English form has no box for it");
        part1(form, fields);
        part2(form, fields);
        part3(form, fields);
        designee(header, fields);
        paidPreparer(header, fields);
    }

    private static void entity(Filer filer, Fields fields) {
        fields.ein(ENTITY + "f1_1[0]", ENTITY + "f1_2[0]", filer.ein());
        fields.text(ENTITY + "f1_3[0]", filer.name());
        fields.text(ENTITY + "f1_4[0]", filer.tradeName());
        fields.text(ENTITY + "f1_5[0]", filer.street());
        fields.text(ENTITY + "f1_6[0]", filer.city());
        fields.text(ENTITY + "f1_7[0]", filer.state());
        fields.text(ENTITY + "f1_8[0]", filer.postalCode().filter(zip -> filer.country().isEmpty()));
        fields.text(ENTITY + "f1_9[0]", filer.country());
        fields.text(ENTITY + "f1_10[0]", filer.province());
        fields.text(ENTITY + "f1_11[0]", filer.postalCode().filter(postal -> filer.country().isPresent()));
        fields.text(PAGE2 + "Name_ReadOrder[0].f1_3[0]", filer.name());
        fields.ein(PAGE2 + "EIN_Number[0].f1_1[0]", PAGE2 + "EIN_Number[0].f1_2[0]", filer.ein());
    }

    private static void part1(ReturnXml.Node form, Fields fields) {
        fields.text(PAGE1 + "f1_12[0]", form.text("EmployeeCnt"));
        money(form, fields, "WagesAmt", 13);
        money(form, fields, "FederalIncomeTaxWithheldAmt", 15);
        fields.check(PAGE1 + "c1_3[0]", "1", form.checked("WagesNotSubjToSSMedcrTaxInd"));
        money(form, fields, "SocialSecurityWageAndTaxGrp/SocialSecurityTaxCashWagesAmt", 17);
        money(form, fields, "SocialSecurityWageAndTaxGrp/SocialSecurityTaxAmt", 19);
        money(form, fields, "SocialSecurityTipsAndTaxGrp/TaxableSocSecTipsAmt", 21);
        money(form, fields, "SocialSecurityTipsAndTaxGrp/TaxOnSocialSecurityTipsAmt", 23);
        money(form, fields, "MedicareWageTipsAndTaxGrp/TaxableMedicareWagesTipsAmt", 25);
        money(form, fields, "MedicareWageTipsAndTaxGrp/TaxOnMedicareWagesTipsAmt", 27);
        money(form, fields, "AddnlMedicareWageTipsAndTaxGrp/TxblWageTipsSubjAddnlMedcrAmt", 29);
        money(form, fields, "AddnlMedicareWageTipsAndTaxGrp/TaxOnWageTipsSubjAddnlMedcrAmt", 31);
        money(form, fields, "TotalSSMdcrTaxAmt", 33);
        money(form, fields, "TaxOnUnreportedTips3121qAmt", 35);
        money(form, fields, "TotalTaxBeforeAdjustmentAmt", 37);
        money(form, fields, "CurrentQtrFractionsCentsAmt", 39);
        money(form, fields, "CurrentQuarterSickPaymentAmt", 41);
        money(form, fields, "CurrQtrTipGrpTermLifeInsAdjAmt", 43);
        money(form, fields, "TotalTaxAfterAdjustmentAmt", 45);
        money(form, fields, "PayrollTaxCreditAmt", 47);
        money(form, fields, "TotalTaxAmt", 49);
        money(form, fields, "TotalTaxDepositAmt", 51);
        money(form, fields, "BalanceDueAmt", 53);
        money(form, fields, "OverpaymentGrp/OverpaidAmt", 55);
        fields.check(PAGE1 + "c1_4[0]", "1", form.checked("OverpaymentGrp/ApplyOverpaymentNextReturnInd"));
        fields.check(PAGE1 + "c1_4[1]", "2", form.checked("OverpaymentGrp/RefundOverpaymentInd"));
        fields.text(PAGE1 + "RoutingNo[0].f1_57[0]", form.text("DirectDepositGrp/RoutingTransitNum"));
        fields.check(PAGE1 + "c1_5[0]", "1", form.checked("DirectDepositGrp/CheckingAccountInd"));
        fields.check(PAGE1 + "c1_5[1]", "2", form.checked("DirectDepositGrp/SavingsAccountInd"));
        fields.text(PAGE1 + "AccountNo[0].f1_58[0]", form.text("DirectDepositGrp/DepositorAccountNum"));
    }

    private static void part2(ReturnXml.Node form, Fields fields) {
        fields.check(PAGE2 + "c2_1[0]", "1", form.checked("TotalTaxLessThanLimitAmtInd"));
        fields.check(PAGE2 + "c2_1[1]", "2", form.checked("MonthlyScheduleDepositorGrp/MonthlyScheduleDepositorInd"));
        page2Money(form, fields, "MonthlyScheduleDepositorGrp/TaxLiabilityMonth1Amt", 1);
        page2Money(form, fields, "MonthlyScheduleDepositorGrp/TaxLiabilityMonth2Amt", 3);
        page2Money(form, fields, "MonthlyScheduleDepositorGrp/TaxLiabilityMonth3Amt", 5);
        page2Money(form, fields, "MonthlyScheduleDepositorGrp/TotalQuarterTaxLiabilityAmt", 7);
        fields.check(PAGE2 + "c2_1[2]", "3", form.checked("SemiweeklyScheduleDepositorInd"));
    }

    private static void part3(ReturnXml.Node form, Fields fields) {
        fields.check(PAGE2 + "c2_2[0]", "1", form.checked("BusinessClosedGrp/FutureFilingNotRequiredInd"));
        fields.text(PAGE2 + "f2_9[0]", form.text("BusinessClosedGrp/FinalWagesPaidDt")
                .map(date -> LocalDate.parse(date).format(COMB_DATE)));
        fields.check(PAGE2 + "c2_3[0]", "1", form.checked("SeasonalEmployerInd"));
    }

    private static void designee(ReturnXml.Node header, Fields fields) {
        String yes = "DiscussWithThirdPartyYesGrp/";
        fields.check(PAGE2 + "c2_4[0]", "1", header.checked(yes + "DiscussWithThirdPartyYesInd"));
        fields.text(PAGE2 + "f2_10[0]", header.text(yes + "ThirdPartyDesigneeNm"));
        fields.text(PAGE2 + "f2_11[0]", header.text(yes + "ThirdPartyDesigneePhoneNum")
                .or(() -> header.text(yes + "ThirdPartyDesigneeFrgnPhoneNum")));
        fields.text(PAGE2 + "f2_12[0]", header.text(yes + "ThirdPartyDesigneePIN"));
        fields.check(PAGE2 + "c2_4[1]", "2", header.checked("DiscussWithThirdPartyNoInd"));
    }

    private static void paidPreparer(ReturnXml.Node header, Fields fields) {
        String preparer = "PaidPreparerInformationGrp/";
        fields.check(PAGE2 + "c2_5[0]", "1", header.checked(preparer + "SelfEmployedInd"));
        fields.text(PAGE2 + "f2_16[0]", header.text(preparer + "PreparerPersonNm"));
        fields.text(PAGE2 + "f2_17[0]", header.text(preparer + "PTIN")
                .or(() -> header.text(preparer + "PreparerSSN")));
        fields.text(PAGE2 + "f2_18[0]", header.text(preparer + "PreparerFirmName/BusinessNameLine1Txt"));
        fields.text(PAGE2 + "f2_19[0]", header.text(preparer + "PreparerFirmEIN")
                .map(ein -> ein.substring(0, 2) + "-" + ein.substring(2)));
        fields.text(PAGE2 + "f2_20[0]", header.text(preparer + "PreparerUSAddress/AddressLine1Txt"));
        fields.text(PAGE2 + "f2_21[0]", header.text(preparer + "PhoneNum"));
        fields.text(PAGE2 + "f2_22[0]", header.text(preparer + "PreparerUSAddress/CityNm"));
        fields.text(PAGE2 + "f2_23[0]", header.text(preparer + "PreparerUSAddress/StateAbbreviationCd"));
        fields.text(PAGE2 + "f2_24[0]", header.text(preparer + "PreparerUSAddress/ZIPCd"));
        header.notPrinted(preparer + "SignatureDt", "f941.pdf has no fillable preparer date; it is signed by hand");
    }

    /** Page 1 amounts sit in consecutive fields: dollars in f1_n, cents in f1_(n+1). */
    private static void money(ReturnXml.Node form, Fields fields, String path, int dollarsField) {
        fields.money(PAGE1 + "f1_" + dollarsField + "[0]", PAGE1 + "f1_" + (dollarsField + 1) + "[0]", form.text(path));
    }

    private static void page2Money(ReturnXml.Node form, Fields fields, String path, int dollarsField) {
        fields.money(PAGE2 + "f2_" + dollarsField + "[0]", PAGE2 + "f2_" + (dollarsField + 1) + "[0]", form.text(path));
    }
}
