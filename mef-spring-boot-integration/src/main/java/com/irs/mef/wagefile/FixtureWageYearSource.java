package com.irs.mef.wagefile;

import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Checked-in Orchid quarter. Social Security tax is 2500.00 * 0.124 and Medicare tax is 2500.00 * 0.029
 * so the wage file is internally consistent. The reconciler does not compare those tax amounts.
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
                31_000L,
                250_000L,
                7_250L);
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
