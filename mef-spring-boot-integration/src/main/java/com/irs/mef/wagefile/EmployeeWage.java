package com.irs.mef.wagefile;

/** One RW row. Money is integer cents. */
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
        long medicareTaxWithheldCents) {

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
    }
}
