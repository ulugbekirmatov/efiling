package com.irs.mef.wagefile;

import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Checked-in Orchid quarter. W-2 Boxes 4 and 6 hold the employee share only: Social Security tax is
 * 2500.00 * 0.062 and Medicare tax is 2500.00 * 0.0145. AccuWage rejects the 941 combined rates here.
 */
@Component
public final class FixtureWageYearSource implements WageYearSource {

    @Override
    public WageYear load(int taxYear) {
        if (taxYear != 2026) {
            throw new IllegalArgumentException("fixture wage year is 2026");
        }
        Submitter submitter = new Submitter(
                "112233445",
                "TESTBSO1",
                "PYRAMOS SOFTWARE LLC",
                "",
                "1 MAIN ST",
                "AUSTIN",
                "TX",
                "78701",
                "PYRAMOS SOFTWARE LLC",
                "PAYROLL CONTACT",
                "5125550100",
                "payroll@example.com");
        EmployeeWage employee = new EmployeeWage(
                "000000000",
                "TEST",
                "",
                "PERSON",
                "1 ORCHID LN",
                "AUSTIN",
                "TX",
                "78701",
                250_000L,
                25_000L,
                250_000L,
                15_500L,
                250_000L,
                3_625L);
        EmployerWage employer = new EmployerWage(
                2026,
                "003000004",
                "ORCHID",
                "1 ORCHID LN",
                "AUSTIN",
                "TX",
                "78701",
                "PAYROLL",
                "5125550100",
                "orchid@example.com",
                List.of(employee));
        return new WageYear(submitter, List.of(employer));
    }
}
