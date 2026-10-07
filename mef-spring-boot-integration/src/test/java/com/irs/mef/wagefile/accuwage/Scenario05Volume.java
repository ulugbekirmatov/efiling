package com.irs.mef.wagefile.accuwage;

import com.irs.mef.wagefile.Efw2Writer;
import com.irs.mef.wagefile.EmployeeWage;
import com.irs.mef.wagefile.WageYear;

import java.util.ArrayList;
import java.util.List;

import static com.irs.mef.wagefile.accuwage.ScenarioKit.employer;
import static com.irs.mef.wagefile.accuwage.ScenarioKit.pay;
import static com.irs.mef.wagefile.accuwage.ScenarioKit.percentOf;
import static com.irs.mef.wagefile.accuwage.ScenarioKit.person;

final class Scenario05Volume {

    private static final String[][] CITIES = {
            {"HARRISBURG", "17101"},
            {"PITTSBURGH", "15222"},
            {"PHILADELPHIA", "19103"},
            {"ALLENTOWN", "18101"},
            {"ERIE", "16501"}
    };

    /** Roughly the standard deduction: low earners have next to no federal withholding. */
    private static final long WITHHOLDING_FREE_GROSS_CENTS = 15_000_00L;

    private Scenario05Volume() {
    }

    static AccuWageScenario scenario() {
        List<EmployeeWage> employees = new ArrayList<>(250);
        for (int n = 100; n <= 349; n++) {
            String[] city = CITIES[n % 5];
            long gross = 15_000_00L + (n * 7_919_31L) % 125_000_00L;
            ScenarioKit.Pay row = pay(gross)
                    .federalIncomeTax(percentOf(gross - WITHHOLDING_FREE_GROSS_CENTS, 8 + (n % 9), 100));
            if (n % 7 == 0) {
                row = row.deferral401k(percentOf(gross, 5, 100));
            }
            employees.add(row.w2(person(n, city[0], "PA", city[1])));
        }
        WageYear year = new WageYear(ScenarioKit.submitter(), List.of(
                employer("254012192", "KEYSTONE STAFFING SERVICES LLC", "500 MARKET ST", "HARRISBURG", "PA",
                        "17101", employees)));
        return new AccuWageScenario(
                "05-volume.txt",
                "One employer with 250 employees across five Pennsylvania cities, wages, withholding on gross above $15,000, and 401k on every seventh person.",
                AccuWageScenario.AccuWageOutcome.NO_ERRORS,
                "No errors, no warnings.",
                new Efw2Writer().write(year));
    }
}
