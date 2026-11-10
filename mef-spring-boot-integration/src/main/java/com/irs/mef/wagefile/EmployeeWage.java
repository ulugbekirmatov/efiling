package com.irs.mef.wagefile;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * One RW row. Money is integer cents.
 * The six core amounts are named fields; every other RW money field (tips, Box 12 codes) goes in
 * {@code otherAmountsCents}. {@code retirementPlan} is RW 488, Box 13 "Retirement plan".
 */
public record EmployeeWage(
        String ssn,
        String firstName,
        String middleName,
        String lastName,
        String deliveryAddress,
        String city,
        String state,
        String zip,
        long wagesCents,
        long federalIncomeTaxWithheldCents,
        long socialSecurityWagesCents,
        long socialSecurityTaxWithheldCents,
        long medicareWagesCents,
        long medicareTaxWithheldCents,
        Map<Efw2Amount, Long> otherAmountsCents,
        boolean retirementPlan) {

    private static final Set<Efw2Amount> NAMED_AMOUNTS = EnumSet.of(
            Efw2Amount.WAGES,
            Efw2Amount.FEDERAL_INCOME_TAX_WITHHELD,
            Efw2Amount.SOCIAL_SECURITY_WAGES,
            Efw2Amount.SOCIAL_SECURITY_TAX_WITHHELD,
            Efw2Amount.MEDICARE_WAGES_AND_TIPS,
            Efw2Amount.MEDICARE_TAX_WITHHELD);

    public EmployeeWage {
        ssn = FieldRules.digits(ssn, 9, "SSN");
        firstName = FieldRules.required(firstName, 15, "first name");
        middleName = FieldRules.bounded(middleName, 15, "middle name");
        lastName = FieldRules.required(lastName, 20, "last name");
        deliveryAddress = FieldRules.required(deliveryAddress, 22, "delivery address");
        city = FieldRules.required(city, 22, "city");
        state = FieldRules.state(state);
        zip = FieldRules.digits(zip, 5, "ZIP");
        FieldRules.nonNegative(wagesCents, "wages");
        FieldRules.nonNegative(federalIncomeTaxWithheldCents, "federal income tax withheld");
        FieldRules.nonNegative(socialSecurityWagesCents, "social security wages");
        FieldRules.nonNegative(socialSecurityTaxWithheldCents, "social security tax withheld");
        FieldRules.nonNegative(medicareWagesCents, "medicare wages");
        FieldRules.nonNegative(medicareTaxWithheldCents, "medicare tax withheld");
        Map<Efw2Amount, Long> others = new EnumMap<>(Efw2Amount.class);
        if (otherAmountsCents != null) {
            for (Map.Entry<Efw2Amount, Long> entry : otherAmountsCents.entrySet()) {
                if (entry.getKey() == null || entry.getValue() == null) {
                    throw new IllegalArgumentException("other amounts cannot hold a null amount or key");
                }
                if (NAMED_AMOUNTS.contains(entry.getKey())) {
                    throw new IllegalArgumentException(entry.getKey().label() + " is a named field, not an other amount");
                }
                FieldRules.nonNegative(entry.getValue(), entry.getKey().label());
                others.put(entry.getKey(), entry.getValue());
            }
        }
        otherAmountsCents = Map.copyOf(others);
    }

    public EmployeeWage(String ssn, String firstName, String middleName, String lastName, String deliveryAddress,
                        String city, String state, String zip, long wagesCents, long federalIncomeTaxWithheldCents,
                        long socialSecurityWagesCents, long socialSecurityTaxWithheldCents, long medicareWagesCents,
                        long medicareTaxWithheldCents) {
        this(ssn, firstName, middleName, lastName, deliveryAddress, city, state, zip, wagesCents,
                federalIncomeTaxWithheldCents, socialSecurityWagesCents, socialSecurityTaxWithheldCents,
                medicareWagesCents, medicareTaxWithheldCents, Map.of(), false);
    }

    public long cents(Efw2Amount amount) {
        return switch (amount) {
            case WAGES -> wagesCents;
            case FEDERAL_INCOME_TAX_WITHHELD -> federalIncomeTaxWithheldCents;
            case SOCIAL_SECURITY_WAGES -> socialSecurityWagesCents;
            case SOCIAL_SECURITY_TAX_WITHHELD -> socialSecurityTaxWithheldCents;
            case MEDICARE_WAGES_AND_TIPS -> medicareWagesCents;
            case MEDICARE_TAX_WITHHELD -> medicareTaxWithheldCents;
            default -> otherAmountsCents.getOrDefault(amount, 0L);
        };
    }
}
