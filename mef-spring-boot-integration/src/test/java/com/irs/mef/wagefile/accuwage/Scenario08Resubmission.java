package com.irs.mef.wagefile.accuwage;

import com.irs.mef.wagefile.Efw2Writer;
import com.irs.mef.wagefile.EmployeeWage;
import com.irs.mef.wagefile.WageYear;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static com.irs.mef.wagefile.accuwage.ScenarioKit.employer;
import static com.irs.mef.wagefile.accuwage.ScenarioKit.pay;
import static com.irs.mef.wagefile.accuwage.ScenarioKit.person;

final class Scenario08Resubmission {

    private Scenario08Resubmission() {
    }

    static AccuWageScenario scenario() {
        List<EmployeeWage> employees = List.of(
                pay(52_000_00L).federalIncomeTax(4_212_00L).w2(person(8, "HARRISBURG", "PA", "17102")),
                pay(38_500_50L).federalIncomeTax(2_105_37L).w2(person(9, "CAMP HILL", "PA", "17011")));
        WageYear year = new WageYear(ScenarioKit.submitter(), List.of(
                employer("241876543", "RIVER CITY PAYROLL LLC", "300 MARKET ST", "HARRISBURG", "PA", "17101",
                        employees)));
        String text = new Efw2Writer().write(year);
        String wfid = "TEST01";
        String fileText = new String(
                new Efw2Writer().markResubmit(text.getBytes(StandardCharsets.US_ASCII), wfid),
                StandardCharsets.US_ASCII);
        return new AccuWageScenario(
                "08-resubmission.txt",
                "Two-employee resubmission; WFID TEST01 is a placeholder until SSA issues one on acceptance.",
                AccuWageScenario.AccuWageOutcome.NO_ERRORS,
                "No errors; AccuWage reads it as a resubmission (RA 29 = 1, WFID TEST01).",
                fileText);
    }
}
