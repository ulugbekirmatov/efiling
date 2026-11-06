package com.irs.mef.ssnvs;

import com.irs.mef.ssnvs.EmployerPlan.SentPerson;
import com.irs.mef.ssnvs.SsnvsFixtures.Row;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.irs.mef.ssnvs.SsnvsFixtures.TODAY;
import static com.irs.mef.ssnvs.SsnvsFixtures.ein;
import static com.irs.mef.ssnvs.SsnvsFixtures.employeeId;
import static com.irs.mef.ssnvs.SsnvsFixtures.first;
import static com.irs.mef.ssnvs.SsnvsFixtures.last;
import static com.irs.mef.ssnvs.SsnvsFixtures.middle;
import static com.irs.mef.ssnvs.SsnvsFixtures.plan;
import static com.irs.mef.ssnvs.SsnvsFixtures.row;
import static com.irs.mef.ssnvs.SsnvsFixtures.ssn;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SsnvsPlannerTest {

    private static EmployerPlan only(Row... rows) {
        return plan(rows);
    }

    private static SentPerson sentOnly(Row row) {
        EmployerPlan plan = only(row);
        assertEquals(List.of(), plan.held(), "person was held");
        return plan.sent().get(0);
    }

    private static String field(SentPerson person, SsnvsRequest.Field field) {
        return field.in(person.request().record()).stripTrailing();
    }

    /** One row each; DUPLICATE_SSN and DUPLICATE_EMPLOYEE_ROW need two rows and have their own tests. */
    private static Map<Holdback, Row> singleRowHoldbacks() {
        Map<Holdback, Row> cases = new EnumMap<>(Holdback.class);
        cases.put(Holdback.NO_SSN, row(0).with("ssn", null));
        cases.put(Holdback.SSN_NOT_9_DIGITS, row(0).with("ssn", ssn(0).substring(0, 8)));
        cases.put(Holdback.SSN_SHAPE_NEVER_ISSUED, row(0).with("ssn", "666" + ssn(0).substring(3)));
        cases.put(Holdback.W2_FIELDS_BLANK_OR_DISAGREE, row(0).with("active_w2_count", "2").with("ssn", null));
        cases.put(Holdback.W2_EIN_DIFFERS, row(0).with("w2_box_b_ein", ein(2)));
        cases.put(Holdback.W2_EIN_BLANK, row(0).with("w2_box_b_ein", null));
        cases.put(Holdback.LAST_NAME_BLANK, row(0).with("w2_box_e_last_name", null));
        cases.put(Holdback.FIRST_NAME_BLANK, row(0).with("w2_box_e_first_middle", null));
        cases.put(Holdback.NAME_INVALID_CHARACTER, row(0).with("w2_box_e_last_name", "ZQL9"));
        return cases;
    }

    /** One row each; DUPLICATE_FIRST_LAST needs two rows and has its own test. */
    private static Map<Advisory, Row> singleRowAdvisories() {
        Map<Advisory, Row> cases = new EnumMap<>(Advisory.class);
        cases.put(Advisory.SSN_DIFFERS_FROM_HR, row(0).with("hr_ssn_digits", ssn(1)));
        cases.put(Advisory.FIRST_MIDDLE_SPLIT_GUESSED, row(0).with("w2_box_e_first_middle", "ZQGX ZQGY"));
        cases.put(Advisory.SUFFIX_OR_PREFIX_DROPPED, row(0).with("w2_box_e_last_name", last(0) + " JR"));
        cases.put(Advisory.NAME_TRUNCATED, row(0).with("w2_box_e_last_name", last(0) + "ABCDEFGHI")
                .with("hr_last_name", last(0) + "ABCDEFGHI"));
        cases.put(Advisory.BIRTH_DATE_OMITTED_MASTERS_DISAGREE,
                row(0).with("adp_birth_dt", SsnvsFixtures.birthDate(0).plusDays(1).toString()));
        cases.put(Advisory.BIRTH_DATE_OMITTED_OUT_OF_RANGE, row(0).with("hr_birth_dt", TODAY.plusDays(1).toString())
                .with("adp_birth_dt", TODAY.plusDays(1).toString()));
        cases.put(Advisory.SEVERAL_W2S, row(0).with("active_w2_count", "2"));
        return cases;
    }

    @Test
    @DisplayName("every holdback has a case, and each single-row case holds the person for exactly that reason")
    void eachHoldback() {
        Map<Holdback, Row> cases = singleRowHoldbacks();
        EnumSet<Holdback> covered = EnumSet.copyOf(cases.keySet());
        covered.addAll(List.of(Holdback.DUPLICATE_SSN, Holdback.DUPLICATE_EMPLOYEE_ROW));
        assertEquals(EnumSet.allOf(Holdback.class), covered);
        cases.forEach((holdback, row) -> {
            EmployerPlan plan = only(row);
            assertEquals(List.of(), plan.sent(), holdback.name());
            assertEquals(EnumSet.of(holdback), plan.held().get(0).holdbacks(), holdback.name());
        });
    }

    @Test
    @DisplayName("every advisory has a case, and each single-row case sends the person with exactly that note")
    void eachAdvisory() {
        Map<Advisory, Row> cases = singleRowAdvisories();
        EnumSet<Advisory> covered = EnumSet.copyOf(cases.keySet());
        covered.add(Advisory.DUPLICATE_FIRST_LAST);
        assertEquals(EnumSet.allOf(Advisory.class), covered);
        cases.forEach((advisory, row) -> assertEquals(EnumSet.of(advisory), sentOnly(row).advisories(), advisory.name()));
    }

    @Test
    @DisplayName("an SSN listed twice holds both holders, and the rest of the file still goes")
    void duplicateSsnHoldsBoth() {
        EmployerPlan plan = only(row(0), row(1).with("ssn", ssn(0)).with("hr_ssn_digits", ssn(0)), row(2));

        assertEquals(List.of(employeeId(2)), plan.sent().stream().map(p -> p.refs().employeeId()).toList());
        assertEquals(List.of(employeeId(0), employeeId(1)), plan.held().stream().map(p -> p.refs().employeeId()).toList());
        plan.held().forEach(person -> assertEquals(EnumSet.of(Holdback.DUPLICATE_SSN), person.holdbacks()));
    }

    @Test
    @DisplayName("an employee id exported twice holds both rows")
    void duplicateEmployeeRow() {
        EmployerPlan plan = only(row(0), row(1).with("employee_id", Long.toString(employeeId(0))));

        assertEquals(List.of(), plan.sent());
        plan.held().forEach(person -> assertTrue(person.holdbacks().contains(Holdback.DUPLICATE_EMPLOYEE_ROW)));
    }

    @Test
    @DisplayName("same first and last name with different SSNs are both sent and both flagged")
    void duplicateFirstLast() {
        EmployerPlan plan = only(row(0), row(1).with("w2_box_e_first_middle", first(0))
                .with("w2_box_e_last_name", last(0)));

        assertEquals(2, plan.sent().size());
        plan.sent().forEach(person -> assertTrue(person.advisories().contains(Advisory.DUPLICATE_FIRST_LAST)));
    }

    @Test
    @DisplayName("each box e split source cuts first and middle where the masters say")
    void nameSources() {
        record Case(Row row, NameSource source, String first, String middle) {
        }
        List<Case> cases = List.of(
                new Case(row(0), NameSource.HR_FULL, first(0), middle(0)),
                new Case(row(0).with("hr_first_name", "zqf ann").with("hr_middle_name", "zqm")
                        .with("w2_box_e_first_middle", "ZQF  ANN ZQM"), NameSource.HR_FULL, "ZQFANN", "ZQM"),
                new Case(row(0).with("hr_middle_name", "ZQOTHER").with("w2_box_e_first_middle", first(0) + " Z"),
                        NameSource.HR_INITIAL, first(0), "Z"),
                new Case(row(0).with("hr_first_name", "ZQOTHER"), NameSource.ADP_FULL, first(0), middle(0)),
                new Case(row(0).with("w2_box_e_first_middle", first(0)), NameSource.SINGLE_TOKEN, first(0), ""),
                new Case(row(0).with("w2_box_e_first_middle", first(0) + " ZQ QA"), NameSource.HR_FIRST_PREFIX,
                        first(0), "ZQQA"),
                new Case(row(0).with("w2_box_e_first_middle", "ZQGX ZQGY"), NameSource.GUESSED, "ZQGX", "ZQGY"),
                new Case(row(0).with("w2_box_e_first_middle", "Dr. " + first(0) + " " + middle(0)),
                        NameSource.HR_FULL, first(0), middle(0)));
        for (Case c : cases) {
            SentPerson person = sentOnly(c.row());
            assertEquals(c.source(), person.nameSource(), c.toString());
            assertEquals(c.first(), field(person, SsnvsRequest.Field.FIRST_NAME), c.source().name());
            assertEquals(c.middle(), field(person, SsnvsRequest.Field.MIDDLE_NAME), c.source().name());
            assertEquals(last(0), field(person, SsnvsRequest.Field.LAST_NAME), c.source().name());
        }
        SentPerson titled = sentOnly(row(0).with("w2_box_e_first_middle", "Dr. " + first(0) + " " + middle(0)));
        assertEquals(EnumSet.of(Advisory.SUFFIX_OR_PREFIX_DROPPED), titled.advisories());
        assertEquals(first(0), field(titled, SsnvsRequest.Field.FIRST_NAME));
        assertEquals(middle(0), field(titled, SsnvsRequest.Field.MIDDLE_NAME));
    }

    @Test
    @DisplayName("a title-only box e stays the title with no suffix advisory")
    void titleOnlyBoxEStays() {
        SentPerson person = sentOnly(row(0).with("w2_box_e_first_middle", "DR")
                .with("hr_first_name", "ZQOTHER").with("adp_first_name", "ZQOTHER")
                .with("hr_middle_name", null).with("hr_middle_initial", null).with("adp_middle_name", null));

        assertEquals(NameSource.SINGLE_TOKEN, person.nameSource());
        assertEquals("DR", field(person, SsnvsRequest.Field.FIRST_NAME));
        assertEquals("", field(person, SsnvsRequest.Field.MIDDLE_NAME));
        assertEquals(EnumSet.noneOf(Advisory.class), person.advisories());
    }

    @Test
    @DisplayName("a suffix in box e first and middle is dropped before the master cut")
    void suffixInBoxEFirstMiddle() {
        String initial = middle(0).substring(0, 1);
        SentPerson withMiddle = sentOnly(row(0)
                .with("w2_box_e_first_middle", first(0) + " " + initial + " JR")
                .with("hr_middle_name", initial));

        assertEquals(NameSource.HR_FULL, withMiddle.nameSource());
        assertEquals(first(0), field(withMiddle, SsnvsRequest.Field.FIRST_NAME));
        assertEquals(initial, field(withMiddle, SsnvsRequest.Field.MIDDLE_NAME));
        assertEquals(EnumSet.of(Advisory.SUFFIX_OR_PREFIX_DROPPED), withMiddle.advisories());

        SentPerson noMiddle = sentOnly(row(0)
                .with("w2_box_e_first_middle", first(0) + " JR")
                .with("hr_middle_name", null).with("hr_middle_initial", null).with("adp_middle_name", null));

        assertEquals(NameSource.HR_FULL, noMiddle.nameSource());
        assertEquals(first(0), field(noMiddle, SsnvsRequest.Field.FIRST_NAME));
        assertEquals("", field(noMiddle, SsnvsRequest.Field.MIDDLE_NAME));
        assertEquals(EnumSet.of(Advisory.SUFFIX_OR_PREFIX_DROPPED), noMiddle.advisories());
    }

    @Test
    @DisplayName("a lone V after the first name in box e is sent as the middle initial, not dropped as a suffix")
    void middleInitialV() {
        SentPerson person = sentOnly(row(0)
                .with("w2_box_e_first_middle", first(0) + " V")
                .with("hr_middle_name", null).with("hr_middle_initial", "V").with("adp_middle_name", null));

        assertEquals(NameSource.HR_INITIAL, person.nameSource());
        assertEquals(first(0), field(person, SsnvsRequest.Field.FIRST_NAME));
        assertEquals("V", field(person, SsnvsRequest.Field.MIDDLE_NAME));
        assertEquals(EnumSet.noneOf(Advisory.class), person.advisories());
    }

    @Test
    @DisplayName("accents, a no-break space, and a trailing last-name suffix still match the master")
    void masterMatchUsesTokens() {
        SentPerson accent = sentOnly(row(0).with("hr_first_name", "zqf\u00e1" + first(0).substring(4)));
        assertEquals(NameSource.HR_FULL, accent.nameSource());
        assertEquals(first(0), field(accent, SsnvsRequest.Field.FIRST_NAME));

        SentPerson nbsp = sentOnly(row(0).with("w2_box_e_first_middle", first(0) + "\u00A0" + middle(0)));
        assertEquals(NameSource.HR_FULL, nbsp.nameSource());
        assertEquals(first(0), field(nbsp, SsnvsRequest.Field.FIRST_NAME));
        assertEquals(middle(0), field(nbsp, SsnvsRequest.Field.MIDDLE_NAME));

        SentPerson suffix = sentOnly(row(0).with("w2_box_e_last_name", last(0) + " JR"));
        assertEquals(NameSource.HR_FULL, suffix.nameSource());
        assertEquals(last(0), field(suffix, SsnvsRequest.Field.LAST_NAME));
        assertEquals(EnumSet.of(Advisory.SUFFIX_OR_PREFIX_DROPPED), suffix.advisories());
    }

    @Test
    @DisplayName("a null box b EIN is W2_EIN_BLANK for one W-2 and W2_FIELDS_BLANK_OR_DISAGREE for several")
    void nullBoxBEin() {
        assertEquals(EnumSet.of(Holdback.W2_EIN_BLANK),
                only(row(0).with("w2_box_b_ein", null)).held().get(0).holdbacks());
        assertEquals(EnumSet.of(Holdback.W2_FIELDS_BLANK_OR_DISAGREE),
                only(row(0).with("w2_box_b_ein", null).with("active_w2_count", "2")).held().get(0).holdbacks());
    }

    @Test
    @DisplayName("birth date: one master or both agreeing is sent; neither is blank with no note")
    void birthDatePolicy() {
        String expected = SsnvsFixtures.birthDate(0).format(DateTimeFormatter.ofPattern("MMddyyyy"));

        assertEquals(expected, field(sentOnly(row(0)), SsnvsRequest.Field.BIRTH_DATE));
        assertEquals(expected, field(sentOnly(row(0).with("adp_birth_dt", null)), SsnvsRequest.Field.BIRTH_DATE));
        assertEquals(expected, field(sentOnly(row(0).with("hr_birth_dt", null)), SsnvsRequest.Field.BIRTH_DATE));
        SentPerson neither = sentOnly(row(0).with("hr_birth_dt", null).with("adp_birth_dt", null));
        assertEquals("", field(neither, SsnvsRequest.Field.BIRTH_DATE));
        assertEquals(EnumSet.noneOf(Advisory.class), neither.advisories());
        assertFalse(neither.birthDateSent());
        assertEquals("", field(sentOnly(singleRowAdvisories().get(Advisory.BIRTH_DATE_OMITTED_MASTERS_DISAGREE)),
                SsnvsRequest.Field.BIRTH_DATE));
    }

    @Test
    @DisplayName("an employer EIN that is not nine digits refuses the file")
    void einInvalid() {
        EmployerPlan plan = only(row(0).with("employer_ein", "98-12").with("w2_box_b_ein", "98-12"));

        assertEquals(Optional.of(FileGate.EIN_INVALID), plan.gate());
        assertEquals("9812", plan.ein());
    }

    @Test
    @DisplayName("an employer with nobody sendable refuses the file")
    void nothingToSend() {
        assertEquals(Optional.of(FileGate.NOTHING_TO_SEND), only(row(0).with("ssn", null)).gate());
    }

    @Test
    @DisplayName("more than 250,000 sendable people refuses the file; 250,000 does not")
    void over250000() {
        List<W2PersonRow> rows = new ArrayList<>();
        for (int n = 0; n <= FileGate.MAX_RECORDS; n++) {
            rows.add(new W2PersonRow(n + 2, "7", ein(1), true, SsnvsFixtures.TAX_YEAR, employeeId(n), 1,
                    "1", "C1", ein(1), ssn(n), ssn(n), first(n), last(n), first(n), null, null, last(n),
                    null, null, null, null, null));
        }

        EmployerPlan plan = SsnvsPlanner.plan(rows, TODAY);

        assertEquals(FileGate.MAX_RECORDS + 1, plan.sent().size());
        assertEquals(Optional.of(FileGate.OVER_250000), plan.gate());
        assertEquals(Optional.empty(), FileGate.check(ein(1), FileGate.MAX_RECORDS));
    }
}
