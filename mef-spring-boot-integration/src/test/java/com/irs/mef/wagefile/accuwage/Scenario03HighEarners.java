package com.irs.mef.wagefile.accuwage;

import com.irs.mef.wagefile.Efw2Writer;
import com.irs.mef.wagefile.WageYear;

import java.util.List;

import static com.irs.mef.wagefile.accuwage.ScenarioKit.employer;
import static com.irs.mef.wagefile.accuwage.ScenarioKit.pay;
import static com.irs.mef.wagefile.accuwage.ScenarioKit.person;

final class Scenario03HighEarners {

    private Scenario03HighEarners() {
    }

    static AccuWageScenario scenario() {
        WageYear year = new WageYear(ScenarioKit.submitter(), List.of(
                employer("231234567", "STUB EMPLOYER", "1 STUB ST", "HARRISBURG", "PA", "17101",
                        List.of(pay(10_000_00L).w2(person(0, "HARRISBURG", "PA", "17101"))))));
        return new AccuWageScenario(
                "03-high-earners.txt",
                "TODO Social Security wage base cap and Additional Medicare",
                AccuWageScenario.AccuWageOutcome.NO_ERRORS,
                "TODO",
                new Efw2Writer().write(year));
    }
}
