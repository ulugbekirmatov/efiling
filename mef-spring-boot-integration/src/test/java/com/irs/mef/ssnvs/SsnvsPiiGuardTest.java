package com.irs.mef.ssnvs;

import com.irs.mef.ssnvs.EmployerPlan.SentPerson;
import com.irs.mef.ssnvs.SsnvsFixtures.Row;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.irs.mef.ssnvs.SsnvsFixtures.employeeId;
import static com.irs.mef.ssnvs.SsnvsFixtures.row;
import static com.irs.mef.ssnvs.SsnvsFixtures.ssn;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Runs build and results on fixture people and asserts no fixture SSN, name or birth date reaches anything a
 * person or a log could see: stdout, stderr, exception messages, toString, manifest.json and the three CSVs.
 * Only ssnvs.txt (which SSA requires) may hold them. Every fixture name token starts with ZQ, so "ZQ" anywhere
 * in that text is a leak.
 */
class SsnvsPiiGuardTest {

    private static final int PEOPLE = 6;
    private static final int OTHER_HR_SSN = 77;
    private static final DateTimeFormatter MMDDYYYY = DateTimeFormatter.ofPattern("MMddyyyy");

    @TempDir
    Path temp;

    private static List<Row> fixturePeople() {
        List<Row> rows = new ArrayList<>(SsnvsFixtures.people(PEOPLE));
        rows.get(1).with("w2_box_e_first_middle", "ZQGX ZQGY");
        rows.get(2).with("hr_ssn_digits", ssn(OTHER_HR_SSN));
        rows.get(3).with("adp_birth_dt", SsnvsFixtures.birthDate(3).plusDays(1).toString());
        rows.get(4).with("w2_box_b_ein", SsnvsFixtures.ein(2));
        rows.add(row(PEOPLE).with("ssn", "666" + ssn(PEOPLE).substring(3)));
        return rows;
    }

    private static List<String> forbidden() {
        List<String> values = new ArrayList<>(List.of("ZQ", ssn(OTHER_HR_SSN), "666" + ssn(PEOPLE).substring(3)));
        for (int n = 0; n <= PEOPLE; n++) {
            LocalDate birthDate = SsnvsFixtures.birthDate(n);
            values.addAll(List.of(ssn(n), birthDate.toString(), birthDate.format(MMDDYYYY),
                    birthDate.plusDays(1).toString()));
        }
        return values;
    }

    private static void assertNoPii(String where, String text) {
        for (String value : forbidden()) {
            assertFalse(text.contains(value), where + " leaks a fixture value of length " + value.length());
        }
    }

    @Test
    @DisplayName("build, results and every error path leak no fixture SSN, name or birth date")
    void noLeaks() throws IOException {
        Path csv = temp.resolve("export.csv");
        Files.write(csv, SsnvsFixtures.csvBytes(fixturePeople()));
        Path out = temp.resolve("out");
        Path einDir = out.resolve(SsnvsFixtures.ein(1));
        StringBuilder seen = new StringBuilder();

        SsnvsCliTest.Run built = SsnvsCliTest.cli("build", "--csv", csv.toString(), "--out", out.toString(),
                "--today", SsnvsFixtures.TODAY.toString());
        assertEquals(SsnvsCli.Exit.ACTION_NEEDED, built.exit());
        seen.append(built.out()).append(built.err());

        byte[] request = Files.readAllBytes(einDir.resolve("ssnvs.txt"));
        Path results = temp.resolve("results.txt");
        Files.writeString(results, FakeSsaResults.answer(request,
                Map.of(employeeId(1), '5', employeeId(2), '1', employeeId(3), '3'), Map.of(employeeId(0), 'Y')));
        SsnvsCliTest.Run reconciled = SsnvsCliTest.cli("results", "--dir", einDir.toString(), "--results",
                results.toString());
        assertEquals(SsnvsCli.Exit.ACTION_NEEDED, reconciled.exit());
        seen.append(reconciled.out()).append(reconciled.err());

        for (String file : List.of("manifest.json", "holdbacks.csv", "notes.csv", "fixes.csv")) {
            String text = Files.readString(einDir.resolve(file), StandardCharsets.UTF_8);
            assertFalse(text.isEmpty(), file);
            assertNoPii(file, text);
        }

        String badCsv = SsnvsFixtures.csv(fixturePeople()).replace(SsnvsFixtures.birthDate(2).toString(),
                SsnvsFixtures.birthDate(2).format(MMDDYYYY));
        Files.writeString(csv, badCsv);
        SsnvsCliTest.Run badDate = SsnvsCliTest.cli("build", "--csv", csv.toString(), "--out", out.toString());
        assertEquals(SsnvsCli.Exit.INPUT_ERROR, badDate.exit());
        seen.append(badDate.out()).append(badDate.err());

        Files.writeString(results, Files.readString(results).replace("214OEVS000", "214OEVS00X"));
        SsnvsCliTest.Run badResults = SsnvsCliTest.cli("results", "--dir", einDir.toString(), "--results",
                results.toString());
        assertEquals(SsnvsCli.Exit.INPUT_ERROR, badResults.exit());
        seen.append(badResults.out()).append(badResults.err());

        assertNoPii("CLI output", seen.toString().replace(temp.toString(), "<temp>"));
    }

    @Test
    @DisplayName("toString of every domain object and every exception message leaks no fixture value")
    void noLeaksInObjects() {
        List<W2PersonRow> rows = SsnvsFixtures.parsed(fixturePeople());
        List<EmployerPlan> plans = SsnvsPlanner.plan(rows, SsnvsFixtures.TODAY);
        EmployerPlan plan = plans.get(0);
        List<SsnvsRequest> sent = plan.sent().stream().map(SentPerson::request).toList();
        List<SsnvsResult> results = SsnvsResultParser.parse(FakeSsaResults.answer(
                SsnvsOutput.requestFile(plan.sent()), Map.of(employeeId(1), '5')).getBytes(StandardCharsets.US_ASCII));
        Manifest manifest = Manifest.of(plan, SsnvsFixtures.TODAY);
        FixList fixes = FixList.reconcile(sent, manifest, results);
        StringBuilder shown = new StringBuilder();
        shown.append(rows).append(plans).append(sent).append(results).append(manifest).append(fixes);
        for (W2PersonRow row : rows) {
            shown.append(BoxESplit.of(row));
            shown.append(SsaNameRules.normalize(row.boxELastName(), NamePart.LAST));
        }
        for (SsnvsRequest request : sent) {
            shown.append(request.ssn()).append(request.last()).append(request.first()).append(request.middle())
                    .append(request.birthDate()).append(request.control());
        }

        String fixtureSsn = ssn(0);
        shown.append(assertThrows(IllegalArgumentException.class,
                () -> new Ssn("9" + fixtureSsn.substring(1))).getMessage());
        shown.append(assertThrows(IllegalArgumentException.class,
                () -> new LastName(SsnvsFixtures.last(0) + "-X")).getMessage());
        shown.append(assertThrows(IllegalArgumentException.class,
                () -> SsnvsRequest.parse(sent.get(0).record().substring(1))).getMessage());
        shown.append(assertThrows(IllegalArgumentException.class,
                () -> BirthDate.parseMmddyyyy(SsnvsFixtures.birthDate(0).format(MMDDYYYY).substring(1) + "X"))
                .getMessage());
        shown.append(assertThrows(SsnvsInputException.class, () -> SsnvsResultParser.parse(
                sent.get(0).record().getBytes(StandardCharsets.US_ASCII))).getMessage());

        assertNoPii("toString and messages", shown.toString());
    }
}
