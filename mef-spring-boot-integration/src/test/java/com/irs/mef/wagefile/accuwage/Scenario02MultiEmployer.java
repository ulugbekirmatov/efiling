package com.irs.mef.wagefile.accuwage;

import com.irs.mef.wagefile.Efw2Writer;
import com.irs.mef.wagefile.EmployeeWage;
import com.irs.mef.wagefile.WageYear;

import java.util.List;

import static com.irs.mef.wagefile.accuwage.ScenarioKit.employer;
import static com.irs.mef.wagefile.accuwage.ScenarioKit.pay;
import static com.irs.mef.wagefile.accuwage.ScenarioKit.person;

final class Scenario02MultiEmployer {

    private Scenario02MultiEmployer() {
    }

    static AccuWageScenario scenario() {
        ScenarioKit.Person josephSmith = person(10, "YORK", "PA", "17401");
        List<EmployeeWage> lancasterHomeCare = List.of(
                pay(42_500_00L).federalIncomeTax(3_187_50L).w2(josephSmith),
                pay(28_750_00L).federalIncomeTax(1_437_00L).w2(person(11, "LANCASTER", "PA", "17603")));
        List<EmployeeWage> hobokenCatering = List.of(
                pay(18_000_00L).federalIncomeTax(720_00L).w2(person(20, "HOBOKEN", "NJ", "07030")),
                pay(55_000_00L).federalIncomeTax(5_500_00L).w2(person(21, "JERSEY CITY", "NJ", "07302")),
                pay(73_400_00L).federalIncomeTax(8_808_00L).w2(person(22, "UNION CITY", "NJ", "07087")),
                pay(91_200_00L).federalIncomeTax(12_768_00L).w2(person(23, "WEEHAWKEN", "NJ", "07086")));
        List<EmployeeWage> chesapeakeMarine = List.of(
                pay(33_000_00L).federalIncomeTax(2_475_00L).w2(josephSmith),
                pay(61_800_00L).federalIncomeTax(6_798_00L).w2(person(30, "ANNAPOLIS", "MD", "21401")),
                pay(95_000_00L).federalIncomeTax(14_250_00L).w2(person(31, "BALTIMORE", "MD", "21201")));
        WageYear year = new WageYear(ScenarioKit.submitter(), List.of(
                employer("232817456", "LANCASTER HOME CARE LLC", "412 QUEEN ST", "LANCASTER", "PA", "17602",
                        lancasterHomeCare),
                employer("221558913", "HOBOKEN CATERING LLC", "314 WASHINGTON ST", "HOBOKEN", "NJ", "07030",
                        hobokenCatering),
                employer("521667234", "CHESAPEAKE MARINE SUPPLY", "45 WEST ST", "ANNAPOLIS", "MD", "21401",
                        chesapeakeMarine)));
        return new AccuWageScenario(
                "02-multi-employer.txt",
                "Three client employers in one Reporting Agent file; one employee has a W-2 under two of them.",
                AccuWageScenario.AccuWageOutcome.NO_ERRORS,
                "No errors, no warnings.",
                new Efw2Writer().write(year));
    }
}
