package com.irs.mef.wagefile.accuwage;

import com.irs.mef.wagefile.Efw2Writer;
import com.irs.mef.wagefile.EmployeeWage;
import com.irs.mef.wagefile.WageYear;

import java.util.List;

import static com.irs.mef.wagefile.accuwage.ScenarioKit.employer;
import static com.irs.mef.wagefile.accuwage.ScenarioKit.pay;
import static com.irs.mef.wagefile.accuwage.ScenarioKit.person;

final class Scenario06NamesAndAddresses {

    private Scenario06NamesAndAddresses() {
    }

    static AccuWageScenario scenario() {
        List<EmployeeWage> employees = List.of(
                pay(52_000_00L).federalIncomeTax(4_212_00L).w2(new ScenarioKit.Person(
                        person(60, "WILMINGTON", "DE", "19801").ssn(),
                        "CHARLOTTEAMELIA", "", "GARCIA", "10 MARKET ST", "WILMINGTON", "DE", "19801")),
                pay(38_500_00L).federalIncomeTax(2_105_00L).w2(new ScenarioKit.Person(
                        person(61, "ALBANY", "NY", "12207").ssn(),
                        "JAMES", "J", "HENDERSONCHRISTOPHER", "200 STATE ST", "ALBANY", "NY", "12207")),
                pay(71_250_00L).federalIncomeTax(7_830_00L).w2(new ScenarioKit.Person(
                        person(62, "RANCHO SANTA MARGARITA", "CA", "92688").ssn(),
                        "LINDA", "CONSTANTINEANNE", "BROWN", "15 OAK AVE", "RANCHO SANTA MARGARITA", "CA",
                        "92688")),
                pay(45_000_00L).federalIncomeTax(3_600_00L).w2(new ScenarioKit.Person(
                        person(63, "AUSTIN", "TX", "78701").ssn(),
                        "ROBERT", "", "SMITH-JONES", "1234 WASHINGTON STREET", "AUSTIN", "TX", "78701")),
                pay(62_000_00L).federalIncomeTax(6_200_00L).w2(new ScenarioKit.Person(
                        person(64, "MIAMI", "FL", "33130").ssn(),
                        "PATRICIA", "M", "O'BRIEN", "88 BISCAYNE BLVD", "MIAMI", "FL", "33130")),
                pay(28_750_00L).federalIncomeTax(1_437_50L).w2(new ScenarioKit.Person(
                        person(65, "NEWARK", "DE", "19711").ssn(),
                        "MICHAEL", "", "D'ANGELO", "5 KIRKWOOD HWY", "NEWARK", "DE", "19711")),
                pay(85_000_00L).federalIncomeTax(10_200_00L).w2(new ScenarioKit.Person(
                        person(66, "BUFFALO", "NY", "14201").ssn(),
                        "MARY ANN", "L", "WILLIAMS", "400 MAIN ST", "BUFFALO", "NY", "14201")),
                pay(41_200_00L).federalIncomeTax(2_884_00L).w2(new ScenarioKit.Person(
                        person(67, "DALLAS", "TX", "75201").ssn(),
                        "katie", "", "LOPEZ", "1200 COMMERCE ST", "DALLAS", "TX", "75201")),
                pay(55_555_00L).federalIncomeTax(5_000_00L).w2(new ScenarioKit.Person(
                        person(68, "TAMPA", "FL", "33602").ssn(),
                        "DAVID", "ANN", "TAYLOR", "50 BAYSHORE BLVD", "TAMPA", "FL", "33602")));
        WageYear year = new WageYear(ScenarioKit.submitter(), List.of(
                employer("232456789", "CENTRAL PENNSYLVANIA HOME HEALTH AND HOSPICE SERVICES LLC",
                        "200 FRONT ST", "HARRISBURG", "PA", "17101", employees)));
        return new AccuWageScenario(
                "06-names-and-addresses.txt",
                "Maximum-length names and addresses, hyphen and apostrophe surnames, a two-word first name, blank and initial middles, and lowercase input, with employees in DE, NY, CA, TX, and FL.",
                AccuWageScenario.AccuWageOutcome.NO_ERRORS,
                "No errors, no warnings.",
                new Efw2Writer().write(year));
    }
}
