package com.irs.mef.wagefile.accuwage;

import com.irs.mef.wagefile.Efw2Writer;
import com.irs.mef.wagefile.EmployeeWage;
import com.irs.mef.wagefile.WageYear;

import java.util.List;

import static com.irs.mef.wagefile.accuwage.ScenarioKit.employer;
import static com.irs.mef.wagefile.accuwage.ScenarioKit.pay;
import static com.irs.mef.wagefile.accuwage.ScenarioKit.person;

final class Scenario07TippedEmployees {

    private Scenario07TippedEmployees() {
    }

    static AccuWageScenario scenario() {
        List<EmployeeWage> employees = List.of(
                pay(22_000_00L).federalIncomeTax(2_430_00L).tips(18_500_00L)
                        .w2(person(10, "PITTSBURGH", "PA", "15201")),
                pay(31_000_00L).federalIncomeTax(5_242_57L).tips(27_250_75L)
                        .w2(person(11, "PITTSBURGH", "PA", "15206")),
                pay(26_000_00L).federalIncomeTax(1_560_00L)
                        .w2(person(12, "PITTSBURGH", "PA", "15213")),
                pay(160_000_00L).federalIncomeTax(32_000_00L).tips(40_000_00L)
                        .w2(person(13, "PITTSBURGH", "PA", "15217")),
                pay(190_000_00L).federalIncomeTax(31_200_00L).tips(5_000_00L)
                        .w2(person(14, "PITTSBURGH", "PA", "15232")));
        WageYear year = new WageYear(ScenarioKit.submitter(), List.of(
                employer("251876432", "THREE RIVERS GRILL LLC", "400 WATERFRONT DR", "PITTSBURGH", "PA", "15222",
                        employees)));
        return new AccuWageScenario(
                "07-tipped-employees.txt",
                "One restaurant, five employees covering Box 7 tips below, capped at, and already over the wage base.",
                AccuWageScenario.AccuWageOutcome.NO_ERRORS,
                "No errors, no warnings.",
                new Efw2Writer().write(year));
    }
}
