package com.irs.mef.wagefile.accuwage;

import com.irs.mef.wagefile.Efw2Writer;
import com.irs.mef.wagefile.EmployeeWage;
import com.irs.mef.wagefile.WageYear;

import java.util.List;

import static com.irs.mef.wagefile.accuwage.ScenarioKit.SOCIAL_SECURITY_WAGE_BASE_CENTS;
import static com.irs.mef.wagefile.accuwage.ScenarioKit.employer;
import static com.irs.mef.wagefile.accuwage.ScenarioKit.pay;
import static com.irs.mef.wagefile.accuwage.ScenarioKit.person;

final class Scenario03HighEarners {

    private Scenario03HighEarners() {
    }

    static AccuWageScenario scenario() {
        List<EmployeeWage> employees = List.of(
                pay(SOCIAL_SECURITY_WAGE_BASE_CENTS).federalIncomeTax(34_132_50L).w2(person(20, "PHILADELPHIA", "PA", "19103")),
                pay(SOCIAL_SECURITY_WAGE_BASE_CENTS + 1).federalIncomeTax(36_900_00L).w2(person(21, "BRYN MAWR", "PA", "19010")),
                pay(199_999_99L).federalIncomeTax(44_800_00L).w2(person(22, "WAYNE", "PA", "19087")),
                pay(250_000_00L).federalIncomeTax(62_000_00L).w2(person(23, "ARDMORE", "PA", "19003")),
                pay(1_000_000_00L).federalIncomeTax(292_000_00L).w2(person(24, "GLADWYNE", "PA", "19035")));
        WageYear year = new WageYear(ScenarioKit.submitter(), List.of(
                employer("236549871", "MIDATLANTIC CAPITAL LLC", "1500 MARKET ST", "PHILADELPHIA", "PA", "19102",
                        employees)));
        return new AccuWageScenario(
                "03-high-earners.txt",
                "One employer, five employees at and above the Social Security wage base and Additional Medicare threshold.",
                AccuWageScenario.AccuWageOutcome.NO_ERRORS,
                "No errors, no warnings.",
                new Efw2Writer().write(year));
    }
}
