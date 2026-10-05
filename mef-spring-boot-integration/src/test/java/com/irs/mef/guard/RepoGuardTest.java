package com.irs.mef.guard;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.MalformedInputException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Repo-wide rules that agents kept breaking. Each failure names the file and what to do instead.
 * Runs in plain {@code mvn test}, locally and in CI.
 */
class RepoGuardTest {

    private static final Path MODULE = Path.of("");
    private static final Path REPO_ROOT = Path.of("..");

    /** OneWell ETIN / EFIN / ASID. Purged 2026-09-16 and again in af36bc9; they kept coming back. */
    private static final Pattern ONEWELL_ID = Pattern.compile("97661|238689|23868900");

    /**
     * Files that already used a OneWell value as sample data on 2026-10-05. A count may only go
     * down; a file not listed here may not add one. Lower or delete an entry when you clean a file.
     */
    private static final Map<String, Integer> ONEWELL_ID_BASELINE = Map.of(
            "src/test/java/com/irs/mef/inspector/FixtureSnapshots.java", 1,
            "src/test/java/com/irs/mef/inspector/ring/FileSendSnapshotRingTest.java", 3,
            "src/test/java/com/irs/mef/newsend/domain/NewSendManifestXmlTest.java", 3,
            "src/test/java/com/irs/mef/newsend/domain/NewSendSubmissionIdTest.java", 2,
            "src/test/java/com/irs/mef/newsend/journal/InMemoryNewSendSubmissionJournalTest.java", 9,
            "src/test/java/com/irs/mef/reportingagent/ReportingAgentOriginatorStandIn.java", 1,
            "src/test/java/com/irs/mef/reportingagent/ReportingAgentReturnTest.java", 2);

    /** Docs deleted because they taught superseded facts, mapped to what replaced them. */
    private static final Map<String, String> RETIRED_DOCS = Map.of(
            "mef-spring-boot-integration/EFIN_ETIN_USAGE.md",
            "CLAUDE.md gotcha 4 (one EFIN in manifest, OriginatorGrp, and submission ID; R0000-054-01)",
            "mef-spring-boot-integration/PROJECT_SUMMARY.md", "CLAUDE.md and CODE_MAP.md",
            "mef-spring-boot-integration/SETUP-COMPLETE.md", "CLAUDE.md, Build, Run, Test");

    private static final Pattern ALLOW = Pattern.compile(
            "repo-guard:allow onewell-id reason=\"[^\"]+\" expires=(\\d{4}-\\d{2}-\\d{2}) approved-by=\\S+");
    private static final String ALLOW_MARKER = "repo-guard:allow";

    @Test
    void oneWellIdentifiersDoNotSpread() throws IOException {
        List<String> violations = new ArrayList<>();
        for (Path file : guardedFiles()) {
            String relative = MODULE.toAbsolutePath().relativize(file.toAbsolutePath()).toString();
            int count = countOneWellIds(file, relative, violations);
            int allowed = ONEWELL_ID_BASELINE.getOrDefault(relative, 0);
            if (count > allowed) {
                violations.add(relative + " has " + count + " OneWell identifier(s), baseline " + allowed
                        + ". Read EFIN/ETIN/ASID from MefSdkConfig (MEF_* env), or use a stand-in such as 000000.");
            }
        }
        assertEquals(List.of(), violations);
    }

    @Test
    void retiredDocsStayDeleted() {
        List<String> revived = RETIRED_DOCS.entrySet().stream()
                .filter(doc -> Files.exists(REPO_ROOT.resolve(doc.getKey())))
                .map(doc -> doc.getKey() + " was retired; use " + doc.getValue())
                .toList();
        assertEquals(List.of(), revived);
    }

    private static int countOneWellIds(Path file, String relative, List<String> violations) throws IOException {
        int count = 0;
        List<String> lines = readLines(file);
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            Matcher ids = ONEWELL_ID.matcher(line);
            int onLine = 0;
            while (ids.find()) {
                onLine++;
            }
            if (onLine == 0) {
                continue;
            }
            if (line.contains(ALLOW_MARKER)) {
                checkException(relative + ":" + (i + 1), line, violations);
            } else {
                count += onLine;
            }
        }
        return count;
    }

    private static void checkException(String location, String line, List<String> violations) {
        Matcher allow = ALLOW.matcher(line);
        if (!allow.find()) {
            violations.add(location + ": a repo-guard:allow needs reason=\"...\" expires=YYYY-MM-DD approved-by=<human>");
        } else if (LocalDate.parse(allow.group(1)).isBefore(LocalDate.now())) {
            violations.add(location + ": repo-guard:allow expired on " + allow.group(1) + "; remove the value or renew the approval");
        }
    }

    private static List<Path> guardedFiles() throws IOException {
        List<Path> files = new ArrayList<>();
        try (Stream<Path> sources = Files.walk(MODULE.resolve("src"))) {
            sources.filter(Files::isRegularFile)
                    .filter(path -> !path.toString().endsWith(".mime"))
                    .filter(path -> !path.getFileName().toString().equals("RepoGuardTest.java"))
                    .forEach(files::add);
        }
        for (Path dir : List.of(MODULE, REPO_ROOT)) {
            try (Stream<Path> scripts = Files.list(dir)) {
                scripts.filter(path -> path.toString().endsWith(".sh")).forEach(files::add);
            }
        }
        return files;
    }

    private static List<String> readLines(Path file) throws IOException {
        try {
            return Files.readAllLines(file);
        } catch (MalformedInputException binary) {
            return List.of();
        }
    }
}
