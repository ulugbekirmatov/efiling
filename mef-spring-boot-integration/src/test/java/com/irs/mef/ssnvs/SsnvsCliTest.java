package com.irs.mef.ssnvs;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.irs.mef.ssnvs.SsnvsCli.Exit;
import com.irs.mef.ssnvs.SsnvsFixtures.Row;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static com.irs.mef.ssnvs.SsnvsFixtures.ein;
import static com.irs.mef.ssnvs.SsnvsFixtures.employeeId;
import static com.irs.mef.ssnvs.SsnvsFixtures.people;
import static com.irs.mef.ssnvs.SsnvsFixtures.row;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SsnvsCliTest {

    private static final String TODAY = SsnvsFixtures.TODAY.toString();

    @TempDir
    Path temp;

    /** One CLI invocation's exit and output. */
    record Run(Exit exit, String out, String err) {
    }

    static Run cli(String... args) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        Exit exit = SsnvsCli.run(args, new PrintStream(out, true, StandardCharsets.UTF_8),
                new PrintStream(err, true, StandardCharsets.UTF_8));
        return new Run(exit, out.toString(StandardCharsets.UTF_8), err.toString(StandardCharsets.UTF_8));
    }

    private Path csvFile(List<Row> rows) throws IOException {
        Path csv = temp.resolve("export.csv");
        Files.write(csv, SsnvsFixtures.csvBytes(rows));
        return csv;
    }

    private Run build(List<Row> rows, String... extra) throws IOException {
        List<String> args = new ArrayList<>();
        args.add("build");
        args.add("--csv");
        args.add(csvFile(rows).toString());
        args.add("--out");
        args.add(temp.resolve("out").toString());
        args.add("--today");
        args.add(TODAY);
        args.addAll(List.of(extra));
        return cli(args.toArray(String[]::new));
    }

    private Path einDir() {
        return temp.resolve("out").resolve(ein(1));
    }

    private Run results(String resultsText) throws IOException {
        Path results = temp.resolve("ssa-results.txt");
        Files.writeString(results, resultsText, StandardCharsets.US_ASCII);
        return cli("results", "--dir", einDir().toString(), "--results", results.toString());
    }

    @Test
    @DisplayName("a clean export builds one CRLF request file, exits 0, and a clean result reconciles with exit 0")
    void cleanRoundTrip() throws IOException {
        Run built = build(people(3));

        assertEquals(Exit.CLEAN, built.exit());
        assertEquals("EIN " + ein(1) + " TY2026: rows 3, sendable 3, held 0, notes 0 -> upload "
                + einDir().resolve("ssnvs.txt") + " in BSO with client EIN " + ein(1) + System.lineSeparator(), built.out());
        byte[] request = Files.readAllBytes(einDir().resolve("ssnvs.txt"));
        String text = new String(request, StandardCharsets.US_ASCII);
        assertEquals(3 * 132, request.length);
        assertTrue(text.endsWith("\r\n"));
        assertEquals(text.replace("\r\n", ""), text.replace("\r\n", "").replace("\n", "").replace("\r", ""));
        assertFalse(Files.exists(einDir().resolve("ssnvs.zip")));
        assertEquals("employee_id,w2_form_ids,company_codes,code\r\n",
                Files.readString(einDir().resolve("holdbacks.csv")));
        assertEquals("employee_id,w2_form_ids,company_codes,code\r\n", Files.readString(einDir().resolve("notes.csv")));

        Run reconciled = results(FakeSsaResults.answer(request, Map.of()));

        assertEquals(Exit.CLEAN, reconciled.exit());
        assertEquals("EIN " + ein(1) + " TY2026 results: sent 3, verified and alive 3, rows to fix 0 -> "
                + einDir().resolve("fixes.csv") + System.lineSeparator(), reconciled.out());
        assertEquals(String.join(",", FixList.COLUMNS) + "\r\n", Files.readString(einDir().resolve("fixes.csv")));
    }

    @Test
    @DisplayName("holdbacks and notes are written per code, the build exits 2, and a name mismatch result exits 2")
    void actionNeeded() throws IOException {
        List<Row> rows = new ArrayList<>(people(3));
        rows.add(row(3).with("ssn", null).with("w2_box_e_last_name", null));
        rows.get(1).with("w2_box_e_first_middle", "ZQGX ZQGY").with("company_codes", "C1,C2");

        Run built = build(rows);

        assertEquals(Exit.ACTION_NEEDED, built.exit());
        assertTrue(built.out().contains("rows 4, sendable 3, held 1, notes 1"), built.out());
        assertEquals("employee_id,w2_form_ids,company_codes,code\r\n"
                        + employeeId(3) + ",50003,C1,NO_SSN\r\n" + employeeId(3) + ",50003,C1,LAST_NAME_BLANK\r\n",
                Files.readString(einDir().resolve("holdbacks.csv")));
        assertEquals("employee_id,w2_form_ids,company_codes,code\r\n"
                        + employeeId(1) + ",50001,\"C1,C2\",FIRST_MIDDLE_SPLIT_GUESSED\r\n",
                Files.readString(einDir().resolve("notes.csv")));

        byte[] request = Files.readAllBytes(einDir().resolve("ssnvs.txt"));
        Run reconciled = results(FakeSsaResults.answer(request, Map.of(employeeId(1), '5')));

        assertEquals(Exit.ACTION_NEEDED, reconciled.exit());
        List<String> fixes = Files.readAllLines(einDir().resolve("fixes.csv"));
        assertEquals(2, fixes.size());
        assertTrue(fixes.get(1).startsWith(employeeId(1) + ",50001,\"C1,C2\",NAME_MISMATCH,"), fixes.get(1));
        assertTrue(fixes.get(1).contains(",N,GUESSED,Y,"), fixes.get(1));
    }

    @Test
    @DisplayName("manifest.json carries exactly the contract fields and no SSN, name or birth date")
    void manifestFields() throws IOException {
        build(people(2));

        JsonNode manifest = new ObjectMapper().readTree(einDir().resolve("manifest.json").toFile());

        assertEquals(List.of("ein", "taxYear", "asOf", "recordCount", "people"), fieldNames(manifest));
        assertEquals(ein(1), manifest.get("ein").asText());
        assertEquals(2026, manifest.get("taxYear").asInt());
        assertEquals(TODAY, manifest.get("asOf").asText());
        assertEquals(2, manifest.get("recordCount").asInt());
        JsonNode person = manifest.get("people").get(new ControlId(employeeId(0)).text());
        assertEquals(List.of("employeeId", "w2FormIds", "companyCodes", "nameSource", "birthDateSent"), fieldNames(person));
        assertEquals("HR_FULL", person.get("nameSource").asText());
        assertTrue(person.get("birthDateSent").asBoolean());
    }

    private static List<String> fieldNames(JsonNode node) {
        List<String> names = new ArrayList<>();
        for (Iterator<String> it = node.fieldNames(); it.hasNext(); ) {
            names.add(it.next());
        }
        return names;
    }

    @Test
    @DisplayName("over 500 records also writes ssnvs.zip holding exactly one entry, ssnvs.txt, with equal bytes")
    void zipOver500() throws IOException {
        Run built = build(people(501));

        assertTrue(built.out().contains("upload " + einDir().resolve("ssnvs.zip")), built.out());
        byte[] request = Files.readAllBytes(einDir().resolve("ssnvs.txt"));
        try (ZipInputStream zip = new ZipInputStream(Files.newInputStream(einDir().resolve("ssnvs.zip")))) {
            ZipEntry entry = zip.getNextEntry();
            assertEquals("ssnvs.txt", entry.getName());
            assertArrayEquals(request, zip.readAllBytes());
            assertNull(zip.getNextEntry());
        }

        build(people(500), "--replace");

        assertFalse(Files.exists(einDir().resolve("ssnvs.zip")));
    }

    @Test
    @DisplayName("a rebuild replaces the directory and the same export and date give the same bytes")
    void rebuildIsDeterministic() throws IOException {
        build(people(501));
        Map<String, byte[]> first = Map.of(
                "ssnvs.txt", Files.readAllBytes(einDir().resolve("ssnvs.txt")),
                "ssnvs.zip", Files.readAllBytes(einDir().resolve("ssnvs.zip")),
                "manifest.json", Files.readAllBytes(einDir().resolve("manifest.json")));
        Files.writeString(einDir().resolve("fixes.csv"), "stale");

        build(people(501));

        first.forEach((name, bytes) -> {
            try {
                assertArrayEquals(bytes, Files.readAllBytes(einDir().resolve(name)), name);
            } catch (IOException e) {
                throw new AssertionError(e);
            }
        });
        assertFalse(Files.exists(einDir().resolve("fixes.csv")));
        try (var listing = Files.list(temp.resolve("out"))) {
            assertEquals(List.of(ein(1)), listing.map(path -> path.getFileName().toString()).toList());
        }
    }

    @Test
    @DisplayName("a refused file writes no request: invalid EIN writes nothing, nothing-to-send keeps the holdbacks")
    void refusedFiles() throws IOException {
        Run invalid = build(List.of(row(0).with("employer_ein", "98-1").with("w2_box_b_ein", "98-1")));

        assertEquals(Exit.ACTION_NEEDED, invalid.exit());
        assertEquals("EIN 981 TY2026: REFUSED EIN_INVALID (rows 1, sendable 1, held 0, notes 0)" + System.lineSeparator(),
                invalid.out());
        assertFalse(Files.exists(temp.resolve("out")));

        Run nothing = build(List.of(row(0).with("ssn", null)));

        assertEquals(Exit.ACTION_NEEDED, nothing.exit());
        assertTrue(nothing.out().startsWith("EIN " + ein(1) + " TY2026: REFUSED NOTHING_TO_SEND"), nothing.out());
        assertTrue(Files.exists(einDir().resolve("holdbacks.csv")));
        assertFalse(Files.exists(einDir().resolve("ssnvs.txt")));
        assertEquals(Exit.INPUT_ERROR, results("").exit());
    }

    @Test
    @DisplayName("an inactive legal company is a note, not a refusal")
    void inactiveCompany() throws IOException {
        Run built = build(List.of(row(0).with("legal_company_is_active", "false")));

        assertEquals(Exit.CLEAN, built.exit());
        assertTrue(built.out().contains("legal company is inactive"), built.out());
    }

    @Test
    @DisplayName("a missing column, an unreadable file or a bad command exits 1 and names the problem")
    void inputErrors() throws IOException {
        Path csv = csvFile(people(1));
        Files.writeString(csv, Files.readString(csv).replace(",ssn,", ",ssn_text,"));

        Run missing = cli("build", "--csv", csv.toString(), "--out", temp.resolve("out").toString());

        assertEquals(Exit.INPUT_ERROR, missing.exit());
        assertEquals("input error: MISSING_COLUMN at line 1 column ssn" + System.lineSeparator(), missing.err());
        assertEquals(Exit.INPUT_ERROR, cli("build", "--csv", temp.resolve("absent.csv").toString(), "--out", "x").exit());
        assertEquals(Exit.INPUT_ERROR, cli("build", "--csv", csv.toString()).exit());
        assertEquals(Exit.INPUT_ERROR, cli("build", "--csv", csv.toString(), "--out", "x", "--today", "soon").exit());
        assertEquals(Exit.INPUT_ERROR, cli("verify").exit());
        assertEquals(Exit.INPUT_ERROR, cli().exit());
        assertEquals(Exit.INPUT_ERROR, cli("results", "--dir", temp.toString()).exit());
    }

    @Test
    @DisplayName("control data stays ASCII digits under a default locale with its own numbering system")
    void localeFreeControl() {
        Locale saved = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("ar-SA-u-nu-arab"));
            assertEquals("00000000004100", new ControlId(employeeId(0)).text());
        } finally {
            Locale.setDefault(saved);
        }
    }

    @Test
    @DisplayName("a results file with a short line exits 1 and names the line")
    void badResults() throws IOException {
        build(people(2));
        String good = FakeSsaResults.answer(Files.readAllBytes(einDir().resolve("ssnvs.txt")), Map.of());

        Run run = results(good + "too short\r\n");

        assertEquals(Exit.INPUT_ERROR, run.exit());
        assertEquals("input error: BAD_RESULT_RECORD at line 3 column results" + System.lineSeparator(), run.err());
    }

    @Test
    @DisplayName("two employer EINs are an input error and the build writes nothing")
    void mixedEmployers() throws IOException {
        Run mixed = build(List.of(row(0), row(1).with("employer_ein", ein(2))));

        assertEquals(Exit.INPUT_ERROR, mixed.exit());
        assertEquals("input error: MIXED_EMPLOYERS at line 3 column employer_ein" + System.lineSeparator(), mixed.err());
        assertFalse(Files.exists(temp.resolve("out")));
    }

    @Test
    @DisplayName("a build with manifest.json and no fixes.csv is refused unless --replace is passed")
    void uploadedBuildIsKept() throws IOException {
        build(people(1));
        byte[] request = Files.readAllBytes(einDir().resolve("ssnvs.txt"));

        Run refused = build(people(2));

        assertEquals(Exit.INPUT_ERROR, refused.exit());
        assertEquals("input error: BUILD_NOT_RECONCILED at line 0 column manifest.json: run results first or pass --replace"
                + System.lineSeparator(), refused.err());
        assertArrayEquals(request, Files.readAllBytes(einDir().resolve("ssnvs.txt")));
        assertFalse(Files.exists(einDir().resolve("fixes.csv")));

        Run replaced = build(people(2), "--replace");

        assertEquals(Exit.CLEAN, replaced.exit());
        assertEquals(2, new ObjectMapper().readTree(einDir().resolve("manifest.json").toFile()).get("recordCount").asInt());
    }

    @Test
    @DisplayName("replace puts the previous directory back when the new directory cannot take its place")
    void replaceRollsBack() throws IOException {
        Path root = temp.resolve("rollback");
        Path target = root.resolve(ein(1));
        Files.createDirectories(target);
        Files.writeString(target.resolve("manifest.json"), "{\"asOf\":\"kept\"}");
        Path staging = root.resolve("missing-staging");

        assertThrows(java.io.IOException.class, () -> SsnvsOutput.replace(staging, target));

        assertEquals("{\"asOf\":\"kept\"}", Files.readString(target.resolve("manifest.json")));
        assertFalse(Files.exists(target.resolveSibling("." + ein(1) + ".previous")));
    }

    @Test
    @DisplayName("a failed results run deletes the previous fixes.csv before parsing")
    void failedResultsDeletesStaleFixes() throws IOException {
        build(people(1));
        String good = FakeSsaResults.answer(Files.readAllBytes(einDir().resolve("ssnvs.txt")), Map.of());
        assertEquals(Exit.CLEAN, results(good).exit());
        assertEquals(String.join(",", FixList.COLUMNS) + "\r\n", Files.readString(einDir().resolve("fixes.csv")));

        Run failed = results("short\r\n");

        assertEquals(Exit.INPUT_ERROR, failed.exit());
        assertEquals("input error: BAD_RESULT_RECORD at line 1 column results" + System.lineSeparator(), failed.err());
        assertFalse(Files.exists(einDir().resolve("fixes.csv")));
    }
}
