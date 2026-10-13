package com.irs.mef.ssnvs;

import com.irs.mef.ssnvs.EmployerPlan.SentPerson;
import com.irs.mef.ssnvs.FixList.FixRow;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static com.irs.mef.ssnvs.SsnvsFixtures.employeeId;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FixListTest {

    private final EmployerPlan plan = SsnvsPlanner.plan(SsnvsFixtures.parsed(SsnvsFixtures.people(6)),
            SsnvsFixtures.TODAY).get(0);
    private final byte[] requestFile = SsnvsOutput.requestFile(plan.sent());
    private final List<SsnvsRequest> sent = plan.sent().stream().map(SentPerson::request).toList();
    private final Manifest manifest = Manifest.of(plan, SsnvsFixtures.TODAY);

    private FixList reconcile(String results) {
        return FixList.reconcile(sent, manifest, SsnvsResultParser.parse(results.getBytes(StandardCharsets.ISO_8859_1)));
    }

    private static List<String> codes(FixList fixes) {
        return fixes.rows().stream().map(row -> row.cells().get(0) + ":" + row.cells().get(3)).toList();
    }

    @Test
    @DisplayName("everyone verified and alive leaves an empty fix list")
    void clean() {
        FixList fixes = reconcile(FakeSsaResults.answer(requestFile, Map.of()));

        assertEquals(List.of(), fixes.rows());
        assertEquals(6, fixes.verifiedAlive());
    }

    @Test
    @DisplayName("each SSA code and a verified-but-deceased person get a row with the action from the table")
    void codesBecomeRows() {
        String results = FakeSsaResults.answer(requestFile,
                Map.of(employeeId(1), '1', employeeId(2), '3', employeeId(3), '5', employeeId(4), '6'),
                Map.of(employeeId(5), 'Y'));

        FixList fixes = reconcile(results);

        assertEquals(List.of(employeeId(1) + ":SSN_NOT_IN_FILE", employeeId(2) + ":BIRTH_DATE_MISMATCH",
                employeeId(3) + ":NAME_MISMATCH", employeeId(4) + ":NOT_VERIFIED_OTHER",
                employeeId(5) + ":VERIFIED"), codes(fixes));
        assertEquals(1, fixes.verifiedAlive());
        assertEquals(List.of(Long.toString(employeeId(2)), "50002", "C1", "BIRTH_DATE_MISMATCH",
                        "Name and SSN match; birth date does not", "N", "HR_FULL", "Y",
                        "Name and SSN are fine, so the W-2 is OK. Fix the birth date in HR/ADP."),
                fixes.rows().get(1).cells());
        assertEquals("SSA records this person as deceased: confirm the wages belong to this person.",
                fixes.rows().get(4).action());
        assertEquals("Y", fixes.rows().get(4).cells().get(5));
    }

    @Test
    @DisplayName("code 7 is UNKNOWN: held, not verified")
    void unknownCode() {
        FixList fixes = reconcile(FakeSsaResults.answer(requestFile, Map.of(employeeId(0), '7')));

        assertEquals(List.of(employeeId(0) + ":UNKNOWN"), codes(fixes));
        assertEquals("Hold the W-2 and ask BSO support what the code means.", fixes.rows().get(0).action());
    }

    @Test
    @DisplayName("a changed echo or last four, an unknown control, a missing and a doubled person are integrity rows")
    void integrity() {
        String[] lines = FakeSsaResults.answer(requestFile, Map.of()).split("\r\n");
        lines[0] = lines[0].substring(0, 9) + "ZQOTHER" + lines[0].substring(16);
        lines[1] = lines[1].substring(0, 5) + "0000" + lines[1].substring(9);
        lines[2] = lines[2].substring(0, 83) + "00000000009999" + lines[2].substring(97);
        lines[3] = lines[4];

        FixList fixes = reconcile(String.join("\r\n", lines) + "\r\n");

        assertEquals(List.of(employeeId(0) + ":ECHO_MISMATCH", employeeId(1) + ":ECHO_MISMATCH",
                employeeId(2) + ":MISSING_FROM_RESULTS", employeeId(3) + ":MISSING_FROM_RESULTS",
                employeeId(4) + ":DUPLICATE_RESULT", employeeId(4) + ":DUPLICATE_RESULT", ":UNKNOWN_CONTROL"),
                codes(fixes));
        FixRow unknown = fixes.rows().get(6);
        assertEquals("Check that the results file belongs to this EIN's build; do not act on the line. (results line 3)",
                unknown.action());
        assertEquals("", unknown.cells().get(6));
    }

    @Test
    @DisplayName("two confirmation numbers in one results file is a file-level problem")
    void confirmationDiffers() {
        String[] lines = FakeSsaResults.answer(requestFile, Map.of()).split("\r\n");
        lines[5] = lines[5].substring(0, 48) + "ZQCONFIRM0000002" + lines[5].substring(64);

        FixList fixes = reconcile(String.join("\r\n", lines));

        assertEquals(List.of(":CONFIRMATION_DIFFERS"), codes(fixes));
        assertEquals(6, fixes.rows().get(0).resultsLine());
    }

    @Test
    @DisplayName("a manifest that does not list exactly the sent controls is refused")
    void manifestMismatch() {
        assertThrows(SsnvsInputException.class, () -> FixList.reconcile(sent.subList(1, 6), manifest, List.of()));
        assertTrue(FixList.reconcile(sent, manifest, List.of()).rows().stream()
                .allMatch(row -> row.finding() == Integrity.MISSING_FROM_RESULTS));
    }
}
