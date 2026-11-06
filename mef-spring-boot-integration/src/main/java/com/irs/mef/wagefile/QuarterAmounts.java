package com.irs.mef.wagefile;

/**
 * One quarter of Form 941 wage lines. When {@code column1ReplacesQuarter} is true the amounts are
 * Form 941-X column 1, which replaces that quarter. Column 4 is tax and is not a field here.
 */
public record QuarterAmounts(
        String ein,
        long wagesCents,
        long withholdingCents,
        long socialSecurityWagesCents,
        long medicareWagesCents,
        boolean column1ReplacesQuarter) {

    public QuarterAmounts {
        ein = FieldRules.digits(ein, 9, "EIN");
        FieldRules.nonNegative(wagesCents, "wages");
        FieldRules.nonNegative(withholdingCents, "withholding");
        FieldRules.nonNegative(socialSecurityWagesCents, "social security wages");
        FieldRules.nonNegative(medicareWagesCents, "medicare wages");
    }
}
