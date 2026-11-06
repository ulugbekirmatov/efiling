package com.irs.mef.wagefile;

/** Per-employer wage totals taken from a built EFW2 file. Tax withheld is not compared. */
public record EmployerTotals(
        String ein,
        long wagesCents,
        long withholdingCents,
        long socialSecurityWagesCents,
        long medicareWagesCents) {

    public EmployerTotals {
        ein = FieldRules.digits(ein, 9, "EIN");
        FieldRules.nonNegative(wagesCents, "wages");
        FieldRules.nonNegative(withholdingCents, "withholding");
        FieldRules.nonNegative(socialSecurityWagesCents, "social security wages");
        FieldRules.nonNegative(medicareWagesCents, "medicare wages");
    }
}
