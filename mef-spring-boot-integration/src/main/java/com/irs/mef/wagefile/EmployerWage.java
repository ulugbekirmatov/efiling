package com.irs.mef.wagefile;

import java.util.List;

/** One RE employer and its RW rows. Position 7 stays blank: a Form 8655 reporting agent is not a 2678 agent. */
public record EmployerWage(
        int taxYear,
        String ein,
        String name,
        String deliveryAddress,
        String city,
        String state,
        String zip,
        String contactName,
        String contactPhone,
        String contactEmail,
        List<EmployeeWage> employees) {

    public EmployerWage {
        if (taxYear < 1000 || taxYear > 9999) {
            throw new IllegalArgumentException("tax year must be CCYY");
        }
        ein = FieldRules.digits(ein, 9, "employer EIN");
        name = FieldRules.required(name, 57, "employer name");
        deliveryAddress = FieldRules.required(deliveryAddress, 22, "delivery address");
        city = FieldRules.required(city, 22, "city");
        state = FieldRules.state(state);
        zip = FieldRules.digits(zip, 5, "ZIP");
        contactName = FieldRules.required(contactName, 27, "contact name");
        contactPhone = FieldRules.phone(contactPhone);
        contactEmail = FieldRules.email(contactEmail);
        if (employees == null || employees.isEmpty()) {
            throw new IllegalArgumentException("employer " + ein + " has no employees");
        }
        employees = List.copyOf(employees);
    }
}
