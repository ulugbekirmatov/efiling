package com.irs.mef.ssnvs;

import com.irs.mef.ssnvs.EmployerPlan.SentPerson;
import com.irs.mef.ssnvs.SsnvsFixtures.Row;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.irs.mef.ssnvs.SsnvsFixtures.employeeId;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Writes synthetic sample files for ssnvs_check.py to -Dssnvs.out (default target/ssnvs-samples):
 * a clean request file and SSA's fake answer to it with a mix of codes. generate-ssnvs-samples.sh points
 * the output at reports/ssnvs-samples and runs the checker on both.
 */
class SsnvsSampleFilesTest {

    static final String REQUEST = "ssnvs-request.txt";
    static final String RESULT = "ssnvs-result.txt";
    private static final Path OUTPUT = Path.of(System.getProperty("ssnvs.out", "target/ssnvs-samples"));

    /** Every name source and both optional fields absent at least once; all sendable. */
    private static List<Row> samplePeople() {
        List<Row> rows = new ArrayList<>(SsnvsFixtures.people(12));
        rows.get(1).with("w2_box_e_first_middle", SsnvsFixtures.first(1));
        rows.get(2).with("hr_birth_dt", null).with("adp_birth_dt", null);
        rows.get(3).with("w2_box_e_first_middle", "ZQGX ZQGY");
        rows.get(4).with("hr_first_name", "ZQOTHER");
        rows.get(5).with("w2_box_e_last_name", SsnvsFixtures.last(5) + " JR").with("hr_last_name", SsnvsFixtures.last(5));
        rows.get(6).with("w2_box_e_last_name", SsnvsFixtures.last(6) + "ABCDEFGHIJ")
                .with("hr_last_name", SsnvsFixtures.last(6) + "ABCDEFGHIJ");
        return rows;
    }

    @Test
    @DisplayName("writes a clean request file and a mixed-code result file for the independent checker")
    void writeSamples() throws IOException {
        EmployerPlan plan = SsnvsPlanner.plan(SsnvsFixtures.parsed(samplePeople()), SsnvsFixtures.TODAY).get(0);
        assertEquals(List.of(), plan.held());
        byte[] request = SsnvsOutput.requestFile(plan.sent());
        String result = FakeSsaResults.answer(request,
                Map.of(employeeId(1), '1', employeeId(2), '3', employeeId(3), '5', employeeId(4), '6'),
                Map.of(employeeId(5), 'Y'));

        Files.createDirectories(OUTPUT);
        Files.write(OUTPUT.resolve(REQUEST), request);
        Files.writeString(OUTPUT.resolve(RESULT), result, StandardCharsets.US_ASCII);

        FixList fixes = FixList.reconcile(plan.sent().stream().map(SentPerson::request).toList(),
                Manifest.of(plan, SsnvsFixtures.TODAY),
                SsnvsResultParser.parse(Files.readAllBytes(OUTPUT.resolve(RESULT))));
        assertEquals(List.of("SSN_NOT_IN_FILE", "BIRTH_DATE_MISMATCH", "NAME_MISMATCH", "NOT_VERIFIED_OTHER", "VERIFIED"),
                fixes.rows().stream().map(row -> row.finding().name()).toList());
    }
}
