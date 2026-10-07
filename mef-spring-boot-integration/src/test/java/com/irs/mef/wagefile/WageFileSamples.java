package com.irs.mef.wagefile;

import java.util.List;

final class WageFileSamples {

    private WageFileSamples() {
    }

    static WageYear oneEmployee(long wagesCents, long federalIncomeTaxWithheldCents, long socialSecurityWagesCents,
                                long socialSecurityTaxWithheldCents, long medicareWagesCents,
                                long medicareTaxWithheldCents) {
        Submitter submitter = new Submitter(
                "111111111",
                "USERID01",
                "Pyramos Software LLC",
                "",
                "1 Main St",
                "Austin",
                "TX",
                "78701",
                "Pyramos Software LLC",
                "Pyramos@Example.com");
        EmployeeWage employee = new EmployeeWage(
                "000000000",
                "Test",
                "",
                "Person",
                "1 Main St",
                "Austin",
                "TX",
                "78701",
                wagesCents,
                federalIncomeTaxWithheldCents,
                socialSecurityWagesCents,
                socialSecurityTaxWithheldCents,
                medicareWagesCents,
                medicareTaxWithheldCents);
        EmployerWage employer = new EmployerWage(
                2026,
                "003000004",
                "Orchid",
                "1 Main St",
                "Austin",
                "TX",
                "78701",
                "Payroll Desk",
                "5125550100",
                "orchid@example.com",
                List.of(employee));
        return new WageYear(submitter, List.of(employer));
    }
}
