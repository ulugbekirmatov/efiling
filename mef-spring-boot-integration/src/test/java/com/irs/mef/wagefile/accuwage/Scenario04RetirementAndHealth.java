package com.irs.mef.wagefile.accuwage;

import com.irs.mef.wagefile.Efw2Writer;
import com.irs.mef.wagefile.EmployeeWage;
import com.irs.mef.wagefile.WageYear;

import java.util.List;

import static com.irs.mef.wagefile.accuwage.ScenarioKit.employer;
import static com.irs.mef.wagefile.accuwage.ScenarioKit.pay;
import static com.irs.mef.wagefile.accuwage.ScenarioKit.person;

final class Scenario04RetirementAndHealth {

    private Scenario04RetirementAndHealth() {
    }

    static AccuWageScenario scenario() {
        List<EmployeeWage> employees = List.of(
                pay(65_000_00L).federalIncomeTax(4_840_00L).deferral401k(10_000_00L).healthCoverage(8_400_00L)
                        .w2(person(40, "HARRISBURG", "PA", "17102")),
                pay(72_000_00L).federalIncomeTax(7_200_00L).roth401k(6_000_00L).healthCoverage(19_800_00L)
                        .w2(person(41, "CAMP HILL", "PA", "17011")),
                pay(80_000_00L).federalIncomeTax(6_260_00L).deferral401k(12_000_00L).roth401k(3_000_00L)
                        .healthSavingsAccount(3_000_00L).section125(2_400_00L).healthCoverage(7_200_00L)
                        .w2(person(42, "MECHANICSBURG", "PA", "17055")),
                pay(48_000_00L).federalIncomeTax(3_632_40L).groupTermLife(432_00L).dependentCare(5_000_00L)
                        .healthCoverage(8_400_00L).w2(person(43, "CARLISLE", "PA", "17013")),
                pay(41_000_00L).federalIncomeTax(2_460_00L).w2(person(44, "HERSHEY", "PA", "17033")));
        WageYear year = new WageYear(ScenarioKit.submitter(), List.of(
                employer("241567832", "RIVERVIEW STAFFING LLC", "400 MARKET ST", "HARRISBURG", "PA", "17101",
                        employees)));
        return new AccuWageScenario(
                "04-retirement-and-health.txt",
                "One employer, five employees covering Box 12 D AA W DD C, Box 10, Box 13 retirement plan, and a no-benefits control.",
                AccuWageScenario.AccuWageOutcome.NO_ERRORS,
                "No errors, no warnings.",
                new Efw2Writer().write(year));
    }
}
