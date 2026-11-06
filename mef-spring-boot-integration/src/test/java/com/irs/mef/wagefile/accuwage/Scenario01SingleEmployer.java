package com.irs.mef.wagefile.accuwage;

import com.irs.mef.wagefile.Efw2Writer;
import com.irs.mef.wagefile.EmployeeWage;
import com.irs.mef.wagefile.WageYear;

import java.util.List;

import static com.irs.mef.wagefile.accuwage.ScenarioKit.employer;
import static com.irs.mef.wagefile.accuwage.ScenarioKit.pay;
import static com.irs.mef.wagefile.accuwage.ScenarioKit.person;

final class Scenario01SingleEmployer {

    private Scenario01SingleEmployer() {
    }

    static AccuWageScenario scenario() {
        List<EmployeeWage> employees = List.of(
                pay(52_000_00L).federalIncomeTax(4_212_00L).w2(person(1, "HARRISBURG", "PA", "17102")),
                pay(38_500_50L).federalIncomeTax(2_105_37L).w2(person(2, "CAMP HILL", "PA", "17011")),
                pay(71_250_00L).federalIncomeTax(7_830_00L).w2(person(3, "MECHANICSBURG", "PA", "17050")));
        WageYear year = new WageYear(ScenarioKit.submitter(), List.of(
                employer("231234567", "KEYSTONE HOME CARE LLC", "200 FRONT ST", "HARRISBURG", "PA", "17101",
                        employees)));
        return new AccuWageScenario(
                "01-single-employer.txt",
                "One employer, three employees, wages and withholding only.",
                AccuWageScenario.AccuWageOutcome.NO_ERRORS,
                "No errors, no warnings.",
                new Efw2Writer().write(year));
    }
}
